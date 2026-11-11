import { isAdminRole } from '@/shared/types';
import { formatShortDate } from '@/shared/utils/format';
import type {
  AccessRequest,
  AccountDeletionRequest,
  AgentAccessState,
  FieldAgent,
  FieldAgentPage,
  PaymentMode,
  Candidate,
  HouseEntryRow,
  HouseUnitSummary,
  AuthSession,
  AuthUser,
  ConfidenceSummary,
  DashboardSummary,
  HouseSentimentEntry,
  Page,
  SentimentSummary,
  SentimentValue,
  Unit,
  UnitLevel,
  Voter,
  VoterChangePayload,
  VoterChangeRequest,
  VoterUpload,
} from '@/shared/types';
import {
  accessRequests,
  accountDeletionRequests,
  addMonths,
  agentExtras,
  registerAgentExtra,
  booths,
  candidates,
  houseSentimentEntries,
  nextId,
  nowIso,
  sentimentEntries,
  unitById,
  units,
  users,
  voterChangeRequests,
  voterUploads,
  voters,
} from './data';

/**
 * In-memory stand-in for the Spring Boot API. Every route here has a matching
 * controller in `backend/`, so switching VITE_USE_MOCKS to false is the only
 * change needed to run against the real thing.
 */

const LATENCY_MS = 220;
const DEV_OTP = '123456';

let currentUser: AuthUser | null = null;

interface MockRequest {
  body?: unknown;
  query?: Record<string, string | number | boolean | undefined | null>;
  formData?: FormData;
}

class MockError extends Error {
  status: number;
  constructor(message: string, status = 400) {
    super(message);
    this.status = status;
  }
}

function delay<T>(value: T): Promise<T> {
  return new Promise((resolve) => window.setTimeout(() => resolve(value), LATENCY_MS));
}

function q(req: MockRequest, key: string): string | undefined {
  const value = req.query?.[key];
  return value === undefined || value === null || value === '' ? undefined : String(value);
}

function body<T>(req: MockRequest): T {
  return (req.body ?? {}) as T;
}

/* ----------------------------- unit helpers ----------------------------- */

function childrenOf(unitId: string): Unit[] {
  return units.filter((u) => u.parentId === unitId);
}

export function descendantUnits(unitId: string): Unit[] {
  const out: Unit[] = [];
  const stack = [...childrenOf(unitId)];
  while (stack.length) {
    const unit = stack.pop()!;
    out.push(unit);
    stack.push(...childrenOf(unit.id));
  }
  return out;
}

/** A grant on any unit implies every booth beneath it (FR-U3). */
function boothIdsUnder(unitId: string): string[] {
  const unit = unitById(unitId);
  if (!unit) return [];
  if (unit.level === 'BOOTH') return [unit.id];
  return descendantUnits(unitId)
    .filter((u) => u.level === 'BOOTH')
    .map((u) => u.id);
}

function isLive(request: AccessRequest) {
  return (
    request.status === 'APPROVED' && (!request.expiresAt || new Date(request.expiresAt).getTime() > Date.now())
  );
}

function grantsFor(agentId: string): AccessRequest[] {
  return accessRequests.filter((r) => r.agentId === agentId && isLive(r));
}

function accessibleBooths(agentId: string): Unit[] {
  const ids = new Set(grantsFor(agentId).flatMap((g) => boothIdsUnder(g.unitId)));
  return booths.filter((b) => ids.has(b.id));
}

function requireUser(): AuthUser {
  if (!currentUser) throw new MockError('Session expired — please sign in again', 401);
  return currentUser;
}

/* --------------------------- summary helpers ---------------------------- */

interface SummaryFilters {
  candidateId?: string;
  sentiment?: string;
  confidence?: string;
  from?: string;
  to?: string;
  /** Leave out entries recorded against a voter living elsewhere (agent Insights). */
  residentsOnly?: boolean;
}

function entriesMatching(filters: SummaryFilters) {
  return sentimentEntries.filter((e) => {
    if (filters.candidateId && filters.candidateId !== 'ALL' && e.candidateId !== filters.candidateId) return false;
    if (filters.sentiment && filters.sentiment !== 'ALL' && e.sentiment !== filters.sentiment) return false;
    if (filters.confidence && filters.confidence !== 'ALL' && e.confidence !== filters.confidence) return false;
    if (filters.from && new Date(e.recordedAt) < new Date(filters.from)) return false;
    if (filters.to && new Date(e.recordedAt) > new Date(filters.to)) return false;
    return true;
  });
}

function voterBoothId(voterId: string): string | undefined {
  return voters.find((v) => v.id === voterId)?.boothId;
}

/** Rolls booth-level counts up to whatever level was asked for (REP-2). */
function summarise(level: UnitLevel, filters: SummaryFilters, restrictToBooths?: Set<string>): SentimentSummary[] {
  const entries = entriesMatching(filters);
  const targets = units.filter((u) => u.level === level);

  return targets
    .map((unit) => {
      const boothIds = boothIdsUnder(unit.id).filter((id) => !restrictToBooths || restrictToBooths.has(id));
      if (boothIds.length === 0) return null;
      const boothSet = new Set(boothIds);
      const scoped = entries.filter((e) => {
        const bid = voterBoothId(e.voterId);
        return bid ? boothSet.has(bid) : false;
      });
      const totalVoters = voters.filter((v) => boothSet.has(v.boothId)).length;
      const nonResident = scoped.filter((e) => !e.resident).length;
      // The split counts whoever the residence choice admits; "left to do" counts
      // everyone already visited either way, and the roll loses the rest.
      const counted = filters.residentsOnly ? scoped.filter((e) => e.resident) : scoped;
      const positive = counted.filter((e) => e.sentiment === 'POSITIVE').length;
      const neutral = counted.filter((e) => e.sentiment === 'NEUTRAL').length;
      const negative = counted.filter((e) => e.sentiment === 'NEGATIVE').length;
      return {
        unitId: unit.id,
        unitName: unit.name,
        unitLevel: unit.level,
        positive,
        neutral,
        negative,
        notRecorded: Math.max(0, totalVoters - scoped.length),
        total: Math.max(0, totalVoters - (filters.residentsOnly ? nonResident : 0)),
        nonResident,
      } satisfies SentimentSummary;
    })
    .filter((s): s is SentimentSummary => s !== null);
}

function confidenceSummary(filters: SummaryFilters, restrictToBooths?: Set<string>): ConfidenceSummary {
  const entries = entriesMatching(filters)
    .filter((e) => !filters.residentsOnly || e.resident)
    .filter((e) => {
      if (!restrictToBooths) return true;
      const bid = voterBoothId(e.voterId);
      return bid ? restrictToBooths.has(bid) : false;
    });
  return {
    high: entries.filter((e) => e.confidence === 'HIGH').length,
    medium: entries.filter((e) => e.confidence === 'MEDIUM').length,
    low: entries.filter((e) => e.confidence === 'LOW').length,
  };
}

/* ------------------------------- routing -------------------------------- */

type Handler = (req: MockRequest, params: string[]) => unknown;

interface Route {
  method: string;
  pattern: RegExp;
  handler: Handler;
}

const routes: Route[] = [];

function route(method: string, pattern: string, handler: Handler) {
  const regex = new RegExp(`^${pattern.replace(/:[a-zA-Z]+/g, '([^/]+)')}$`);
  routes.push({ method, pattern: regex, handler });
}

/* --- auth --- */

route('POST', '/api/auth/admin/login', (req) => {
  const { email, password } = body<{ email: string; password: string }>(req);
  const admin = users.find((u) => isAdminRole(u.role) && u.email?.toLowerCase() === email?.toLowerCase());
  if (!admin || password !== 'admin123') throw new MockError('Invalid email or password', 401);
  currentUser = admin;
  return { token: `mock.${admin.id}`, user: admin } satisfies AuthSession;
});

const DEMO_AGENT_PASSWORD = 'agent123';

/**
 * The user as the server hands it back at sign-in. The flag matters: a password
 * an admin reset sends the agent to the change-password screen and nowhere else
 * (FA-25), and the mock has to say so or that screen is never seen in demo mode.
 */
function signedIn(agent: AuthUser): AuthUser {
  return {
    ...agent,
    mustChangePassword: Boolean(agentExtras[agent.id]?.mustChangePassword),
    reportsEnabled: Boolean(agentExtras[agent.id]?.reportsEnabled),
  };
}

/**
 * Passwords agents have set. The seeded demo agents start with one so the email
 * route can be tried without signing up first; an agent who signs up and skips
 * the password gets no entry here, and so cannot sign in this way — which is
 * what the server does.
 */
const agentPasswords = new Map<string, string>(
  users
    .filter((u) => u.role === 'FIELD_AGENT' && u.email)
    .map((u) => [u.email!.toLowerCase(), DEMO_AGENT_PASSWORD] as const),
);

route('POST', '/api/auth/agent/login', (req) => {
  const { email, password } = body<{ email: string; password: string }>(req);
  const key = email?.toLowerCase() ?? '';
  const agent = users.find((u) => u.role === 'FIELD_AGENT' && u.email?.toLowerCase() === key);
  // One message however it failed, as on the server: this must not reveal which
  // email addresses are registered, or which accounts have a password.
  if (!agent || !agentPasswords.has(key) || password !== agentPasswords.get(key)) {
    throw new MockError('Invalid email or password', 401);
  }
  if (!agent.active) throw new MockError('This account has been deactivated', 403);
  currentUser = signedIn(agent);
  return { token: `mock.${agent.id}`, user: currentUser } satisfies AuthSession;
});

route('POST', '/api/auth/agent/otp/request', (req) => {
  const { phone } = body<{ phone: string }>(req);
  const agent = users.find((u) => u.role === 'FIELD_AGENT' && u.phone === phone);
  if (!agent) throw new MockError('No agent registered with that number', 404);
  if (!agent.active) throw new MockError('This account has been deactivated', 403);
  return { sent: true, devOtp: DEV_OTP };
});

route('POST', '/api/auth/agent/otp/verify', (req) => {
  const { phone, otp } = body<{ phone: string; otp: string }>(req);
  const agent = users.find((u) => u.role === 'FIELD_AGENT' && u.phone === phone);
  if (!agent) throw new MockError('No agent registered with that number', 404);
  if (otp !== DEV_OTP) throw new MockError('That OTP does not match', 401);
  currentUser = signedIn(agent);
  return { token: `mock.${agent.id}`, user: currentUser } satisfies AuthSession;
});

route('POST', '/api/auth/agent/signup', (req) => {
  const payload = body<{
    name: string;
    email: string;
    phone: string;
    address: string;
    referralNo?: string;
    password?: string;
  }>(req);
  if (users.some((u) => u.phone === payload.phone)) {
    throw new MockError('That phone number is already registered', 409);
  }
  if (users.some((u) => u.email?.toLowerCase() === payload.email?.toLowerCase())) {
    throw new MockError('That email is already registered', 409);
  }
  const agent: AuthUser = {
    id: nextId('agent'),
    name: payload.name,
    email: payload.email,
    phone: payload.phone,
    role: 'FIELD_AGENT',
    active: true,
  };
  users.push(agent);
  // The new agent needs a record of their own, or the admin's list would show
  // them with no agent ID and no referral.
  registerAgentExtra(agent.id, normaliseReferral(payload.referralNo, payload.phone), payload.address);
  if (payload.password) {
    agentPasswords.set(payload.email.toLowerCase(), payload.password);
    agentExtras[agent.id].passwordSet = true;
  }
  return {
    message: payload.password
      ? 'Account created. Sign in with your email and password, or with the OTP sent to your phone.'
      : 'Account created. Sign in with the OTP sent to your phone.',
    devOtp: DEV_OTP,
  };
});

route('POST', '/api/auth/logout', () => {
  currentUser = null;
  return { ok: true };
});

route('GET', '/api/auth/me', () => requireUser());

/* --- reference data --- */

route('GET', '/api/candidates', () => candidates);

route('GET', '/api/units', (req) => {
  const level = q(req, 'level');
  const parentId = q(req, 'parentId');
  return units.filter((u) => (!level || u.level === level) && (!parentId || u.parentId === parentId));
});

/* --- admin: candidates --- */

/** Mirrors CandidateService.resolvePanchayat on the server. */
function resolvePanchayat(unitId: string | null | undefined) {
  if (!unitId) return { unitId: null, unitName: null };
  const unit = units.find((u) => u.id === unitId);
  if (unit === undefined || unit.level !== 'PANCHAYAT') {
    throw new MockError('A candidate maps to a panchayat');
  }
  return { unitId: unit.id, unitName: unit.name };
}

route('POST', '/api/admin/candidates', (req) => {
  const payload = body<{ name: string; party: string | null; unitId: string | null }>(req);
  const name = payload.name.trim();
  if (candidates.some((c) => c.name.toLowerCase() === name.toLowerCase())) {
    throw new MockError(`There is already a candidate called ${name}`, 409);
  }
  const candidate: Candidate = {
    id: nextId('cand'),
    name,
    party: payload.party?.trim() || null,
    ...resolvePanchayat(payload.unitId),
  };
  candidates.push(candidate);
  return candidate;
});

route('PUT', '/api/admin/candidates/:id', (req, [id]) => {
  const payload = body<{ name: string; party: string | null; unitId: string | null }>(req);
  const candidate = candidates.find((c) => c.id === id);
  if (candidate === undefined) throw new MockError('Unknown candidate', 404);
  const mapped = resolvePanchayat(payload.unitId);
  candidate.name = payload.name.trim();
  candidate.party = payload.party?.trim() || null;
  candidate.unitId = mapped.unitId;
  candidate.unitName = mapped.unitName;
  return candidate;
});

route('DELETE', '/api/admin/candidates/:id', (_req, [id]) => {
  const candidate = candidates.find((c) => c.id === id);
  if (candidate === undefined) throw new MockError('Unknown candidate', 404);
  const entries = sentimentEntries.filter((e) => e.candidateId === id).length;
  if (entries > 0) {
    throw new MockError(`${entries} sentiment entr(ies) were recorded toward ${candidate.name}, so it cannot be deleted.`, 409);
  }
  if (accessRequests.some((r) => r.candidateId === id)) {
    throw new MockError(`Access requests name ${candidate.name}, so it cannot be deleted.`, 409);
  }
  candidates.splice(candidates.indexOf(candidate), 1);
  return null;
});

/* --- admin: locations --- */

const LEVEL_ORDER: UnitLevel[] = ['DISTRICT', 'BLOCK', 'PANCHAYAT', 'BOOTH'];

function unitPath(name: string, parent: Unit | undefined) {
  return parent ? `${parent.path} > ${name}` : name;
}

/** Mirrors LocationService.resolveParent on the server. */
function resolveParent(level: UnitLevel, parentId: string | null | undefined): Unit | undefined {
  if (level === 'DISTRICT') {
    if (parentId) throw new MockError('A district sits at the top, so it has no parent');
    return undefined;
  }
  const expected = LEVEL_ORDER[LEVEL_ORDER.indexOf(level) - 1];
  if (parentId) {
    const parent = units.find((u) => u.id === parentId);
    if (parent && parent.level === expected) return parent;
    throw new MockError(`A ${level.toLowerCase()} belongs to a ${expected.toLowerCase()}`);
  }
  throw new MockError(`Pick the ${expected.toLowerCase()} this ${level.toLowerCase()} belongs to`);
}

function repath(parent: Unit) {
  units
    .filter((u) => u.parentId === parent.id)
    .forEach((child) => {
      child.path = unitPath(child.name, parent);
      repath(child);
    });
}

/**
 * Demo mode runs on a fixed tree held in memory, so there is nothing for a
 * master upload to write into. Saying so is better than a silent 404 or a
 * pretend success that leaves the tree unchanged.
 */
route('POST', '/api/admin/locations/import/hierarchy', () => {
  throw new MockError('Uploading the location master needs the real API — demo mode runs on fixed data', 400);
});

route('POST', '/api/admin/locations/import/booths', () => {
  throw new MockError('Uploading the location master needs the real API — demo mode runs on fixed data', 400);
});

route('POST', '/api/admin/units', (req) => {
  const payload = body<{ level: UnitLevel; name: string; parentId: string | null }>(req);
  const parent = resolveParent(payload.level, payload.parentId);
  const name = payload.name.trim();
  const unit: Unit = {
    id: nextId('unit'),
    level: payload.level,
    name,
    path: unitPath(name, parent),
    parentId: parent ? parent.id : null,
  };
  units.push(unit);
  return unit;
});

route('PUT', '/api/admin/units/:id', (req, [id]) => {
  const payload = body<{ level: UnitLevel; name: string; parentId: string | null }>(req);
  const unit = units.find((u) => u.id === id);
  if (unit === undefined) throw new MockError('Unknown unit', 404);
  if (unit.level !== payload.level) throw new MockError("A location's level cannot be changed");
  const parent = resolveParent(payload.level, payload.parentId);
  unit.name = payload.name.trim();
  unit.parentId = parent ? parent.id : null;
  unit.path = unitPath(unit.name, parent);
  repath(unit);
  return unit;
});

route('DELETE', '/api/admin/units/:id', (_req, [id]) => {
  const unit = units.find((u) => u.id === id);
  if (unit === undefined) throw new MockError('Unknown unit', 404);
  if (units.some((u) => u.parentId === id)) {
    throw new MockError(`${unit.name} still has locations under it. Delete those first.`, 409);
  }
  if (voters.some((v) => v.boothId === id)) {
    throw new MockError(`${unit.name} holds voters, so it cannot be deleted.`, 409);
  }
  units.splice(units.indexOf(unit), 1);
  return null;
});

/* --- admin: dashboard --- */

route('GET', '/api/admin/dashboard', (req) => {
  const level = (q(req, 'level') ?? 'BOOTH') as UnitLevel;
  const filters: SummaryFilters = {
    candidateId: q(req, 'candidateId'),
    sentiment: q(req, 'sentiment'),
    confidence: q(req, 'confidence'),
    from: q(req, 'from'),
    to: q(req, 'to'),
  };
  return {
    entriesRecorded: entriesMatching(filters).length,
    activeAgents: users.filter((u) => u.role === 'FIELD_AGENT' && u.active).length,
    pendingAccessRequests: accessRequests.filter((r) => r.status === 'PENDING').length,
    pendingChangeRequests: voterChangeRequests.filter((r) => r.status === 'PENDING').length,
    byUnit: summarise(level, filters),
    confidence: confidenceSummary(filters),
  } satisfies DashboardSummary;
});

/* --- admin: access requests --- */

route('GET', '/api/admin/access-requests', (req) => {
  const status = q(req, 'status');
  return accessRequests
    .filter((r) => !status || status === 'ALL' || r.status === status)
    .slice()
    .sort((a, b) => b.requestedAt.localeCompare(a.requestedAt));
});

route('POST', '/api/admin/access-requests/:id/approve', (req, [id]) => {
  const { months = 6, note } = body<{ months?: number; note?: string }>(req);
  const request = accessRequests.find((r) => r.id === id);
  if (!request) throw new MockError('Access request not found', 404);
  request.status = 'APPROVED';
  request.decidedAt = nowIso();
  request.expiresAt = addMonths(months);
  request.reviewerNote = note ?? null;
  return request;
});

route('POST', '/api/admin/access-requests/:id/reject', (req, [id]) => {
  const { note } = body<{ note?: string }>(req);
  const request = accessRequests.find((r) => r.id === id);
  if (!request) throw new MockError('Access request not found', 404);
  request.status = 'REJECTED';
  request.decidedAt = nowIso();
  request.reviewerNote = note ?? null;
  return request;
});

route('POST', '/api/admin/access-requests/:id/revoke', (_req, [id]) => {
  const request = accessRequests.find((r) => r.id === id);
  if (!request) throw new MockError('Access request not found', 404);
  request.status = 'REVOKED';
  request.expiresAt = nowIso();
  return request;
});

/* --- admin: voters --- */

function paginate<T>(items: T[], page: number, size: number): Page<T> {
  const start = page * size;
  return {
    content: items.slice(start, start + size),
    page,
    size,
    totalElements: items.length,
    totalPages: Math.max(1, Math.ceil(items.length / size)),
  };
}

/** Same order as the backend: numeric house number (voters without one last), then house, then name. */
function houseOrder(a: Voter, b: Voter) {
  const num = (v: Voter) => {
    const digits = /^\d+/.exec(v.houseNo ?? '');
    return digits ? Number(digits[0]) : Number.POSITIVE_INFINITY;
  };
  const house = (v: Voter) => v.houseNo ?? '\uffff'; // blank house numbers sort last
  return num(a) - num(b) || house(a).localeCompare(house(b)) || a.name.localeCompare(b.name);
}

/** `by` mirrors the server: admins search the EPIC no., agents the house no. */
function filterVoters(req: MockRequest, pool: Voter[], by: 'EPIC_NO' | 'HOUSE_NO' = 'EPIC_NO') {
  const boothId = q(req, 'boothId');
  const search = q(req, 'search')?.toLowerCase();
  const sentiment = q(req, 'sentiment');
  return pool.filter((v) => {
    if (boothId && v.boothId !== boothId) return false;
    if (sentiment === 'NOT_RECORDED' && v.sentiment !== null) return false;
    if (sentiment && sentiment !== 'ALL' && sentiment !== 'NOT_RECORDED' && v.sentiment !== sentiment) return false;
    const other = by === 'HOUSE_NO' ? (v.houseNo ?? '') : v.epicNo;
    if (search && !v.name.toLowerCase().includes(search) && !other.toLowerCase().includes(search)) return false;
    return true;
  }).sort(houseOrder);
}

route('GET', '/api/admin/voters', (req) => {
  const page = Number(q(req, 'page') ?? 0);
  const size = Number(q(req, 'size') ?? 25);
  return paginate(filterVoters(req, voters), page, size);
});

route('POST', '/api/admin/voters', (req) => {
  const payload = body<VoterChangePayload & { boothId: string }>(req);
  const booth = unitById(payload.boothId);
  if (!booth) throw new MockError('Unknown booth', 400);
  const voter: Voter = {
    id: nextId('voter'),
    epicNo: payload.epicNo ?? nextId('EPIC').toUpperCase(),
    name: payload.name ?? 'Unnamed voter',
    relation: payload.relation ?? '',
    houseNo: payload.houseNo ?? null,
    age: payload.age ?? 18,
    gender: payload.gender ?? 'OTHER',
    boothId: booth.id,
    boothName: booth.name,
    wardNo: payload.wardNo ?? null,
    // Neither the admin form nor an agent's proposal carries a caste: an agent
    // records it at the door, on the assessment screen.
    caste: null,
    sentiment: null,
    confidence: null,
  };
  voters.push(voter);
  return voter;
});

route('PUT', '/api/admin/voters/:id', (req, [id]) => {
  const voter = voters.find((v) => v.id === id);
  if (!voter) throw new MockError('Voter not found', 404);
  const payload = body<VoterChangePayload>(req);
  Object.assign(voter, {
    epicNo: payload.epicNo ?? voter.epicNo,
    name: payload.name ?? voter.name,
    relation: payload.relation ?? voter.relation,
    houseNo: payload.houseNo ?? voter.houseNo,
    age: payload.age ?? voter.age,
    gender: payload.gender ?? voter.gender,
    wardNo: payload.wardNo ?? voter.wardNo,
  });
  return voter;
});

route('DELETE', '/api/admin/voters/:id', (_req, [id]) => {
  const index = voters.findIndex((v) => v.id === id);
  if (index === -1) throw new MockError('Voter not found', 404);
  voters.splice(index, 1);
  return { ok: true };
});

route('DELETE', '/api/admin/voters/booth/:boothId', (_req, [boothId]) => {
  const booth = unitById(boothId);
  if (booth === undefined || booth.level !== 'BOOTH') throw new MockError('Pick a booth', 400);
  const doomed = voters.filter((v) => v.boothId === boothId).map((v) => v.id);
  const entries = sentimentEntries.filter((e) => doomed.includes(e.voterId)).length;
  for (let i = voters.length - 1; i >= 0; i--) {
    if (doomed.includes(voters[i].id)) voters.splice(i, 1);
  }
  for (let i = sentimentEntries.length - 1; i >= 0; i--) {
    if (doomed.includes(sentimentEntries[i].voterId)) sentimentEntries.splice(i, 1);
  }
  return { boothId, boothName: booth.name, voters: doomed.length, sentimentEntries: entries };
});

route('GET', '/api/admin/voters/uploads', () => voterUploads.slice().sort((a, b) => b.uploadedAt.localeCompare(a.uploadedAt)));

route('POST', '/api/admin/voters/import', (req) => {
  const file = req.formData?.get('file');
  const unitId = String(req.formData?.get('unitId') ?? '');
  const unit = unitById(unitId);
  if (!(file instanceof File)) throw new MockError('Choose a .csv or .xlsx file to upload', 400);
  if (!unit) throw new MockError('Pick the unit this roll belongs to', 400);
  const upload: VoterUpload = {
    id: nextId('up'),
    fileName: file.name,
    unitLevel: unit.level,
    unitName: unit.name,
    // Mock mode can't parse the workbook; the real backend reports the true row count.
    rowCount: Math.max(1, Math.round(file.size / 90)),
    uploadedAt: nowIso(),
    uploadedByName: currentUser?.name ?? 'Admin',
    status: 'PROCESSED',
    message: 'Imported in mock mode — no rows were written',
  };
  voterUploads.unshift(upload);
  return upload;
});

/* --- admin: voter change requests --- */

/* --- admin: field agents (FR-A20) --- */

/**
 * The referral rule, as ReferralNumbers applies it on the server: the mobile
 * number of whoever sent the agent, or nothing. The mock enforces it too, so a
 * demo cannot save what the real API would refuse.
 */
function normaliseReferral(raw: string | undefined | null, ownPhone: string | null | undefined): string | null {
  if (!raw || !raw.trim()) return null;
  let digits = raw.replace(/[\s\-()]/g, '').replace(/^\+/, '');
  if (digits.length === 12 && digits.startsWith('91')) digits = digits.slice(2);
  if (!/^[6-9]\d{9}$/.test(digits)) {
    throw new MockError('A referral number is the 10-digit mobile number of whoever referred the agent');
  }
  if (ownPhone && digits === ownPhone.trim()) throw new MockError('An agent cannot be their own referral');
  return digits;
}

route('PUT', '/api/admin/field-agents/:id/reports-access', (req, [id]) => {
  const agent = users.find((u) => u.id === id && u.role === 'FIELD_AGENT');
  if (!agent || !agentExtras[id]) throw new MockError('Field agent not found', 404);
  agentExtras[id].reportsEnabled = body<{ enabled: boolean }>(req).enabled;
  return agentRow(agent);
});

route('POST', '/api/admin/field-agents/reports-access/bulk', (req) => {
  const payload = body<{
    selection: 'IDS' | 'ALL_MATCHING';
    ids?: string[];
    districtId?: string;
    blockId?: string;
    panchayatId?: string;
    access?: string;
    paidFilter?: boolean;
    reportsFilter?: boolean;
    search?: string;
    enabled: boolean;
  }>(req);

  // Resolved here from the filters, as the server does it.
  const ids =
    payload.selection === 'ALL_MATCHING'
      ? matchingAgents({
          query: {
            districtId: payload.districtId,
            blockId: payload.blockId,
            panchayatId: payload.panchayatId,
            access: payload.access,
            paid: payload.paidFilter,
            reports: payload.reportsFilter,
            q: payload.search,
          },
        }).map((row) => row.id)
      : (payload.ids ?? []);

  if (ids.length === 0) throw new MockError('No agents were selected');
  ids.forEach((id) => {
    if (agentExtras[id]) agentExtras[id].reportsEnabled = payload.enabled;
  });
  return { agents: ids.length, enabled: payload.enabled };
});

route('PUT', '/api/admin/field-agents/:id/referral', (req, [id]) => {
  const agent = users.find((u) => u.id === id && u.role === 'FIELD_AGENT');
  if (!agent || !agentExtras[id]) throw new MockError('Field agent not found', 404);
  agentExtras[id].referralNo = normaliseReferral(body<{ referralNo?: string }>(req).referralNo, agent.phone);
  return agentRow(agent);
});

/**
 * The Field agents page, answered the way the server does it: the area, access
 * state and counts are all worked out here, and the page only ever receives the
 * slice it asked for.
 */
function agentRow(agent: AuthUser): FieldAgent {
  const extra = agentExtras[agent.id];
  const grants = accessRequests.filter((r) => r.agentId === agent.id);
  // The grant that describes the agent today: live first, then pending, then
  // whatever was asked for last.
  const current =
    grants.find((r) => isLive(r)) ??
    grants.find((r) => r.status === 'PENDING') ??
    grants.slice().sort((a, b) => b.requestedAt.localeCompare(a.requestedAt))[0];
  const path = current ? (unitById(current.unitId)?.path ?? '').split(' > ') : [];
  const access: AgentAccessState = !current
    ? 'NONE'
    : isLive(current)
      ? 'APPROVED'
      : current.status === 'PENDING'
        ? 'PENDING'
        : current.status === 'APPROVED'
          ? 'EXPIRED'
          : 'NONE';
  const own = sentimentEntries.filter((e) => e.recordedById === agent.id);

  return {
    id: agent.id,
    agentCode: extra?.agentCode ?? null,
    name: agent.name,
    phone: agent.phone,
    email: agent.email,
    address: agentExtras[agent.id]?.address ?? (path.length ? [...path].reverse().join(', ') : null),
    referralNo: agentExtras[agent.id]?.referralNo ?? null,
    active: agent.active,
    areaUnitId: current?.unitId ?? null,
    panchayatName: path[2] ?? null,
    blockName: path[1] ?? null,
    districtName: path[0] ?? null,
    candidateName: current?.candidateName ?? null,
    access,
    accessExpiresAt: current?.expiresAt ?? null,
    votersRecorded: own.length,
    lastActive: own.map((e) => e.updatedAt).sort().slice(-1)[0] ?? null,
    joined: extra?.joined ?? null,
    downloadEnabled: Boolean(agent.downloadEnabled),
    paid: extra?.paid ?? false,
    paidAmount: extra?.paidAmount ?? null,
    paidOn: extra?.paidOn ?? null,
    paidMode: extra?.paidMode ?? null,
    paidRef: extra?.paidRef ?? null,
    paidUpdatedAt: extra?.paidUpdatedAt ?? null,
    paidUpdatedByName: extra?.paidUpdatedByName ?? null,
    reportsEnabled: extra?.reportsEnabled ?? false,
    passwordSet: extra?.passwordSet ?? false,
    mustChangePassword: extra?.mustChangePassword ?? false,
    passwordResetRequestedAt: extra?.passwordResetRequestedAt ?? null,
  };
}

/** Every unit at or under one, the way the server's scope check reads it. */
function unitIdsAtOrUnder(unitId: string): Set<string> {
  return new Set([unitId, ...descendantUnits(unitId).map((u) => u.id)]);
}

/** The admin's own scope, narrowed by whatever area they picked (FA-3, FA-4). */
function agentAreaIds(req: MockRequest): Set<string> | null {
  const admin = requireUser();
  const picked = q(req, 'panchayatId') ?? q(req, 'blockId') ?? q(req, 'districtId');
  const scope = admin.scopeUnitId ? unitIdsAtOrUnder(admin.scopeUnitId) : null;
  if (!picked) return scope;
  const chosen = unitIdsAtOrUnder(picked);
  if (!scope) return chosen;
  return new Set([...chosen].filter((id) => scope.has(id)));
}

function matchingAgents(req: MockRequest): FieldAgent[] {
  const area = agentAreaIds(req);
  const access = q(req, 'access');
  const paid = q(req, 'paid');
  const reports = q(req, 'reports');
  const search = (q(req, 'q') ?? '').trim().toLowerCase();

  return users
    .filter((u) => u.role === 'FIELD_AGENT')
    .filter((u) => !area || accessRequests.some((r) => r.agentId === u.id && area.has(r.unitId)))
    .map(agentRow)
    .filter((row) => !access || row.access === access)
    .filter((row) => paid === undefined || row.paid === (paid === 'true'))
    .filter((row) => reports === undefined || row.reportsEnabled === (reports === 'true'))
    .filter(
      (row) =>
        !search ||
        row.name.toLowerCase().includes(search) ||
        (row.phone ?? '').toLowerCase().includes(search) ||
        (row.referralNo ?? '').toLowerCase().includes(search) ||
        (row.agentCode ?? '').toLowerCase().includes(search),
    );
}

function sortAgents(rows: FieldAgent[], req: MockRequest): FieldAgent[] {
  const desc = q(req, 'desc') === 'true' ? -1 : 1;
  const byVoters = q(req, 'sort') === 'VOTERS';
  return rows
    .slice()
    .sort(
      (a, b) =>
        desc * (byVoters ? a.votersRecorded - b.votersRecorded : a.name.localeCompare(b.name)) ||
        a.name.localeCompare(b.name),
    );
}

route('GET', '/api/admin/field-agents', (req) => {
  const page = Number(q(req, 'page') ?? 0);
  const size = Number(q(req, 'size') ?? 25);
  const rows = sortAgents(matchingAgents(req), req);
  // The tab counts ignore the payment filter, so the two always add up to All.
  const across = matchingAgents({ ...req, query: { ...req.query, paid: undefined } });
  const paidCount = across.filter((row) => row.paid).length;

  return {
    items: rows.slice(page * size, page * size + size),
    page,
    size,
    total: rows.length,
    allCount: across.length,
    paidCount,
    unpaidCount: across.length - paidCount,
    totalPages: Math.max(1, Math.ceil(rows.length / size)),
  } satisfies FieldAgentPage;
});

route('GET', '/api/admin/field-agents/areas', (req) => {
  const admin = requireUser();
  const level = q(req, 'level');
  const parentId = q(req, 'parentId');
  const scope = admin.scopeUnitId ? unitIdsAtOrUnder(admin.scopeUnitId) : null;

  return units
    .filter((u) => u.level === level && (!parentId || u.parentId === parentId))
    .filter((u) => !scope || scope.has(u.id))
    .map((unit) => {
      const within = unitIdsAtOrUnder(unit.id);
      const agents = new Set(
        accessRequests.filter((r) => within.has(r.unitId)).map((r) => r.agentId),
      );
      return { unitId: unit.id, name: unit.name, agents: agents.size };
    });
});

route('GET', '/api/admin/field-agents/:id', (_req, [id]) => {
  const agent = users.find((u) => u.id === id && u.role === 'FIELD_AGENT');
  if (!agent) throw new MockError('Field agent not found', 404);
  return agentRow(agent);
});

function applyPayment(ids: string[], payload: PaymentBody) {
  const admin = requireUser();
  ids.forEach((id) => {
    const extra = agentExtras[id];
    if (!extra) return;
    extra.paid = payload.paid;
    // Not paid clears the payment; only who changed it is kept (FA-12).
    extra.paidAmount = payload.paid ? (payload.amount ?? null) : null;
    extra.paidOn = payload.paid ? (payload.paidOn ?? nowIso()) : null;
    extra.paidMode = payload.paid ? (payload.mode ?? null) : null;
    extra.paidRef = payload.paid ? (payload.reference ?? null) : null;
    extra.paidUpdatedAt = nowIso();
    extra.paidUpdatedByName = admin.name;
  });
}

interface PaymentBody {
  paid: boolean;
  amount?: number;
  paidOn?: string;
  mode?: PaymentMode;
  reference?: string;
}

route('PUT', '/api/admin/field-agents/:id/payment', (req, [id]) => {
  const agent = users.find((u) => u.id === id && u.role === 'FIELD_AGENT');
  if (!agent) throw new MockError('Field agent not found', 404);
  const payload = body<PaymentBody>(req);
  if (payload.paid && !(Number(payload.amount) > 0)) throw new MockError('Enter the amount paid');
  applyPayment([id], payload);
  return agentRow(agent);
});

route('POST', '/api/admin/field-agents/payment/bulk', (req) => {
  const payload = body<PaymentBody & { selection: 'IDS' | 'ALL_MATCHING'; ids?: string[] }>(req);
  // ALL_MATCHING is resolved here from the filters, never from a list the page
  // put together, exactly as the server does it (FA-14).
  const ids =
    payload.selection === 'ALL_MATCHING'
      ? matchingAgents({
          query: {
            districtId: payload['districtId' as keyof typeof payload] as string | undefined,
            blockId: payload['blockId' as keyof typeof payload] as string | undefined,
            panchayatId: payload['panchayatId' as keyof typeof payload] as string | undefined,
            access: payload['access' as keyof typeof payload] as string | undefined,
            paid: payload['paidFilter' as keyof typeof payload] as boolean | undefined,
            q: payload['search' as keyof typeof payload] as string | undefined,
          },
        }).map((row) => row.id)
      : (payload.ids ?? []);

  if (ids.length === 0) throw new MockError('No agents were selected');
  if (payload.paid && !(Number(payload.amount) > 0)) throw new MockError('Enter the amount paid');
  applyPayment(ids, payload);
  return {
    agents: ids.length,
    paid: payload.paid,
    total: payload.paid ? (payload.amount ?? 0) * ids.length : null,
  };
});

const RESET_WORDS = ['seva', 'jaga', 'sathi', 'bela', 'tara', 'nila'];

route('POST', '/api/admin/field-agents/:id/password-reset', (req, [id]) => {
  const agent = users.find((u) => u.id === id && u.role === 'FIELD_AGENT');
  const extra = agentExtras[id];
  if (!agent || !extra) throw new MockError('Field agent not found', 404);

  const typed = (body<{ password?: string }>(req).password ?? '').trim();
  const password =
    typed || `${RESET_WORDS[Math.floor(Math.random() * RESET_WORDS.length)]}${1000 + Math.floor(Math.random() * 9000)}`;
  if (password.length < 6 || /\s/.test(password)) {
    throw new MockError('A temporary password needs at least 6 characters and no spaces');
  }

  if (agent.email) agentPasswords.set(agent.email.toLowerCase(), password);
  extra.passwordSet = true;
  extra.mustChangePassword = true;
  extra.passwordResetRequestedAt = null;
  return { temporaryPassword: password, validDays: 7 };
});

route('DELETE', '/api/admin/field-agents/:id/password-request', (_req, [id]) => {
  const agent = users.find((u) => u.id === id && u.role === 'FIELD_AGENT');
  if (!agent || !agentExtras[id]) throw new MockError('Field agent not found', 404);
  agentExtras[id].passwordResetRequestedAt = null;
  return agentRow(agent);
});

/* --- agent: Reports (FR-U14) --- */

function requireReportsAgent() {
  const agent = requireUser();
  if (!agentExtras[agent.id]?.reportsEnabled) {
    throw new MockError('The Reports page is not switched on for your account', 403);
  }
  return agent;
}

/** One entry per live grant, with the booths and wards it covers (AR-2, AR-4). */
function reportScopes(agentId: string) {
  return grantsFor(agentId).map((grant) => {
    const unit = unitById(grant.unitId)!;
    const path = (unit.path ?? '').split(' > ');
    const panchayat = unit.level === 'BOOTH' ? unitById(unit.parentId!)! : unit;
    const boothIds = boothIdsUnder(grant.unitId);
    const booths = boothIds.map((id) => ({ id, name: unitById(id)!.name }));
    const wards = [
      ...new Set(voters.filter((v) => boothIds.includes(v.boothId) && v.wardNo).map((v) => v.wardNo as number)),
    ].sort((a, b) => a - b);

    return {
      panchayatId: panchayat.id,
      panchayatName: panchayat.name,
      blockName: path[1] ?? null,
      districtName: path[0] ?? null,
      candidateId: grant.candidateId,
      candidateName: grant.candidateName,
      expiresAt: grant.expiresAt,
      booths,
      wards,
    };
  });
}

route('GET', '/api/agent/reports/scope', () => reportScopes(requireReportsAgent().id));

route('GET', '/api/agent/reports/voters.pdf', (req) => {
  const agent = requireReportsAgent();
  // AG-10: the second switch is what lets named voters leave the building.
  if (!agent.downloadEnabled) throw new MockError('Voter-details download is turned off for you', 403);

  const panchayatId = q(req, 'panchayatId');
  const scope = reportScopes(agent.id).find((s) => s.panchayatId === panchayatId);
  if (!scope) throw new MockError('You have no approved access to that panchayat', 403);

  const chosenBooths = (q(req, 'booth') ?? '').split(',').filter(Boolean);
  const wanted = (q(req, 'sentiment') ?? '').split(',').filter(Boolean);
  const rows = scope.booths
    .filter((booth) => chosenBooths.length === 0 || chosenBooths.includes(booth.id))
    .flatMap((booth) =>
      voters
        .filter((v) => v.boothId === booth.id)
        .map((v) => {
          const entry = sentimentEntries.find(
            (e) => e.voterId === v.id && e.candidateId === scope.candidateId,
          );
          return { booth: booth.name, voter: v, mark: entry?.sentiment ?? 'NOT_RECORDED' };
        })
        .filter((row) => wanted.length === 0 || wanted.includes(row.mark)),
    );
  if (rows.length === 0) throw new MockError('No voters match that selection');

  // The real endpoint answers with a watermarked PDF; the mock stands in with a
  // readable text file of the same rows so the flow can be demonstrated.
  const lines = rows.map(
    (row, i) =>
      `${i + 1}. ${row.booth} | ${row.voter.name} | ${row.voter.relation} | ${row.voter.age} | ` +
      `${row.voter.gender} | ${row.voter.houseNo ?? ''} | ${row.mark}`,
  );
  const text = [`${scope.panchayatName} — voter details (demo)`, agent.name, '', ...lines].join('\n');
  return new Blob([text + '\n'], { type: 'application/pdf' });
});

route('GET', '/api/agent/reports', (req) => {
  const agent = requireReportsAgent();
  const panchayatId = q(req, 'panchayatId');
  const candidateId = q(req, 'candidateId');
  const scope = reportScopes(agent.id).find(
    (s) => s.panchayatId === panchayatId && (!candidateId || s.candidateId === candidateId),
  );
  if (!scope) throw new MockError('You have no approved access to that panchayat', 403);

  const confidence = (q(req, 'confidence') ?? '').split(',').filter(Boolean);
  const wards = (q(req, 'ward') ?? '').split(',').filter(Boolean).map(Number);
  const castes = (q(req, 'caste') ?? '').split(',').filter(Boolean);
  const chosenBooths = (q(req, 'booth') ?? '').split(',').filter(Boolean);
  const days = Number(q(req, 'days') ?? 0);
  const since = days > 0 ? Date.now() - days * 86400000 : null;

  // A booth outside the agent's own approvals is refused, not dropped.
  const outside = chosenBooths.filter((id) => !scope.booths.some((booth) => booth.id === id));
  if (outside.length > 0) throw new MockError('You have no approved access to that booth', 403);

  // Caste narrows the roll, not the entry: it is held on the voter, so the
  // recorded and not-recorded figures move together.
  const casteMatches = (voterCaste: string | null) =>
    castes.length === 0 || (voterCaste === null ? castes.includes('NONE') : castes.includes(voterCaste));

  const rows = scope.booths
    .filter((booth) => chosenBooths.length === 0 || chosenBooths.includes(booth.id))
    .map((booth) => {
    const roll = voters.filter(
      (v) =>
        v.boothId === booth.id &&
        (wards.length === 0 || (v.wardNo !== null && wards.includes(v.wardNo))) &&
        casteMatches(v.caste),
    );
    const ids = new Set(roll.map((v) => v.id));
    const entries = sentimentEntries.filter((e) => ids.has(e.voterId) && e.candidateId === scope.candidateId);
    const counted = entries
      .filter((e) => confidence.length === 0 || confidence.includes(e.confidence))
      .filter((e) => !since || new Date(e.updatedAt).getTime() >= since);

    return {
      boothId: booth.id,
      boothName: booth.name,
      positive: counted.filter((e) => e.sentiment === 'POSITIVE').length,
      neutral: counted.filter((e) => e.sentiment === 'NEUTRAL').length,
      negative: counted.filter((e) => e.sentiment === 'NEGATIVE').length,
      // Never narrowed by confidence or date: a voter seen last month is not
      // waiting to be seen (AgentReportDto).
      notRecorded: Math.max(0, roll.length - new Set(entries.map((e) => e.voterId)).size),
      voters: roll.length,
    };
  });

  const sum = (pick: (row: (typeof rows)[number]) => number) => rows.reduce((total, row) => total + pick(row), 0);
  return {
    booths: rows,
    totals: {
      boothId: scope.panchayatId,
      boothName: scope.panchayatName,
      positive: sum((r) => r.positive),
      neutral: sum((r) => r.neutral),
      negative: sum((r) => r.negative),
      notRecorded: sum((r) => r.notRecorded),
      voters: sum((r) => r.voters),
    },
    boothCount: rows.length,
  };
});

/* --- agent: their own sign-in (FA-24) --- */

function passwordStatus(agentId: string) {
  const extra = agentExtras[agentId];
  return {
    passwordSet: Boolean(extra?.passwordSet),
    mustChangePassword: Boolean(extra?.mustChangePassword),
    resetRequestedAt: extra?.passwordResetRequestedAt ?? null,
  };
}

route('GET', '/api/agent/password', () => passwordStatus(requireUser().id));

route('POST', '/api/agent/password/reset-request', () => {
  const agent = requireUser();
  const extra = agentExtras[agent.id];
  // Asking twice leaves the one ask standing, as the server does.
  if (extra && !extra.passwordResetRequestedAt) extra.passwordResetRequestedAt = nowIso();
  return passwordStatus(agent.id);
});

route('DELETE', '/api/agent/password/reset-request', () => {
  const agent = requireUser();
  if (agentExtras[agent.id]) agentExtras[agent.id].passwordResetRequestedAt = null;
  return passwordStatus(agent.id);
});

route('PUT', '/api/agent/password', (req) => {
  const agent = requireUser();
  const extra = agentExtras[agent.id];
  const { currentPassword, newPassword } = body<{ currentPassword?: string; newPassword: string }>(req);

  if (extra?.passwordSet) {
    const held = agent.email ? agentPasswords.get(agent.email.toLowerCase()) : undefined;
    if (!currentPassword || (held && currentPassword !== held)) {
      throw new MockError('That is not your current password');
    }
  }
  if (newPassword.length < 8 || !/[A-Za-z]/.test(newPassword) || !/\d/.test(newPassword) || /\s/.test(newPassword)) {
    throw new MockError('Choose at least 8 characters with a letter and a number, and no spaces');
  }

  if (agent.email) agentPasswords.set(agent.email.toLowerCase(), newPassword);
  if (extra) {
    extra.passwordSet = true;
    extra.mustChangePassword = false;
    extra.passwordResetRequestedAt = null;
  }
  currentUser = { ...agent, mustChangePassword: false };
  return passwordStatus(agent.id);
});

route('GET', '/api/admin/field-agents/export.csv', (req) => {
  const header =
    'Agent ID,Name,Mobile,Email,Address,District,Block,Panchayat,Candidate,Access status,' +
    'Access expiry,Voters recorded,Joined,Last active,Paid,Amount,Paid on,Mode,Reference';
  const lines = sortAgents(matchingAgents(req), req).map((row) =>
    [
      row.agentCode,
      row.name,
      row.phone,
      row.email,
      row.address,
      row.districtName,
      row.blockName,
      row.panchayatName,
      row.candidateName,
      row.access,
      row.accessExpiresAt,
      row.votersRecorded,
      row.joined,
      row.lastActive,
      row.paid ? 'Yes' : 'No',
      row.paidAmount,
      row.paidOn,
      row.paidMode,
      row.paidRef,
    ]
      .map((cell) => (cell == null ? '' : `"${String(cell).replace(/"/g, '""')}"`))
      .join(','),
  );
  // The real endpoint answers with a file, so the mock has to as well.
  return new Blob([`\uFEFF${[header, ...lines].join('\r\n')}\r\n`], { type: 'text/csv' });
});

route('GET', '/api/admin/voter-changes', (req) => {
  const status = q(req, 'status');
  return voterChangeRequests
    .filter((r) => !status || status === 'ALL' || r.status === status)
    .slice()
    .sort((a, b) => b.proposedAt.localeCompare(a.proposedAt));
});

route('POST', '/api/admin/voter-changes/:id/approve', (req, [id]) => {
  const change = voterChangeRequests.find((r) => r.id === id);
  if (!change) throw new MockError('Change request not found', 404);
  const { note } = body<{ note?: string }>(req);

  // Approval is the only path that writes to the voter roll (FR-A8 / FR-U11).
  if (change.type === 'ADD') {
    const booth = unitById(change.boothId)!;
    voters.push({
      id: nextId('voter'),
      epicNo: change.payload.epicNo ?? nextId('EPIC').toUpperCase(),
      name: change.payload.name ?? 'Unnamed voter',
      relation: change.payload.relation ?? '',
      houseNo: change.payload.houseNo ?? null,
      age: change.payload.age ?? 18,
      gender: change.payload.gender ?? 'OTHER',
      boothId: booth.id,
      boothName: booth.name,
      wardNo: change.payload.wardNo ?? null,
      caste: null,
      sentiment: null,
      confidence: null,
    });
  } else if (change.type === 'EDIT' && change.voterId) {
    const voter = voters.find((v) => v.id === change.voterId);
    if (voter) {
      Object.assign(voter, {
        epicNo: change.payload.epicNo ?? voter.epicNo,
        name: change.payload.name ?? voter.name,
        relation: change.payload.relation ?? voter.relation,
        houseNo: change.payload.houseNo ?? voter.houseNo,
        age: change.payload.age ?? voter.age,
        gender: change.payload.gender ?? voter.gender,
        wardNo: change.payload.wardNo ?? voter.wardNo,
      });
    }
  } else if (change.type === 'DELETE' && change.voterId) {
    const index = voters.findIndex((v) => v.id === change.voterId);
    if (index !== -1) voters.splice(index, 1);
  }

  change.status = 'APPROVED';
  change.decidedAt = nowIso();
  change.reviewerNote = note ?? null;
  return change;
});

route('POST', '/api/admin/voter-changes/:id/reject', (req, [id]) => {
  const change = voterChangeRequests.find((r) => r.id === id);
  if (!change) throw new MockError('Change request not found', 404);
  change.status = 'REJECTED';
  change.decidedAt = nowIso();
  change.reviewerNote = body<{ note?: string }>(req).note ?? null;
  return change;
});

/* --- admin: users --- */

route('GET', '/api/admin/users', (req) => {
  const role = q(req, 'role');
  return users.filter((u) => !role || role === 'ALL' || u.role === role);
});

route('POST', '/api/admin/users/admins', (req) => {
  const caller = requireUser();
  if (caller.role !== 'SUPER_ADMIN') throw new MockError('Only a super admin can manage admin accounts', 403);
  const payload = body<{
    name: string;
    email: string;
    phone?: string;
    superAdmin: boolean;
    scopeLevel?: UnitLevel;
    scopeUnitId?: string;
  }>(req);
  if (users.some((u) => u.email?.toLowerCase() === payload.email.toLowerCase())) {
    throw new MockError('That email is already registered', 409);
  }
  if (!payload.superAdmin && !payload.scopeUnitId) {
    throw new MockError('Pick the district, block or panchayat this admin looks after', 400);
  }
  const unit = payload.scopeUnitId ? unitById(payload.scopeUnitId) : undefined;
  if (unit && unit.level === 'BOOTH') {
    throw new MockError('An admin is scoped to a district, block or panchayat — not a booth', 400);
  }
  const account: AuthUser = {
    id: nextId('admin'),
    name: payload.name,
    email: payload.email,
    phone: payload.phone ?? null,
    role: payload.superAdmin ? 'SUPER_ADMIN' : 'ADMIN',
    active: true,
    scopeLevel: payload.superAdmin ? null : (payload.scopeLevel ?? null),
    scopeUnitId: payload.superAdmin ? null : (payload.scopeUnitId ?? null),
    scopeUnitName: payload.superAdmin ? null : (unit?.name ?? null),
  };
  users.push(account);
  return account;
});

route('PATCH', '/api/admin/users/admins/:id/scope', (req, [id]) => {
  const caller = requireUser();
  if (caller.role !== 'SUPER_ADMIN') throw new MockError('Only a super admin can manage admin accounts', 403);
  const account = users.find((u) => u.id === id);
  if (!account) throw new MockError('User not found', 404);
  if (account.role !== 'ADMIN') throw new MockError('A super admin sees everything, so there is no area to set', 400);
  const payload = body<{ scopeLevel: UnitLevel; scopeUnitId: string }>(req);
  const unit = unitById(payload.scopeUnitId);
  if (!unit || unit.level === 'BOOTH') {
    throw new MockError('An admin is scoped to a district, block or panchayat — not a booth', 400);
  }
  account.scopeLevel = payload.scopeLevel;
  account.scopeUnitId = unit.id;
  account.scopeUnitName = unit.name;
  return account;
});

route('POST', '/api/admin/users', (req) => {
  const payload = body<{
    name: string;
    email: string;
    phone: string;
    referralNo?: string;
    role?: AuthUser['role'];
  }>(req);
  if (users.some((u) => u.phone === payload.phone)) throw new MockError('That phone number is already registered', 409);
  // Refused before the account exists, as the server does it.
  const referralNo = normaliseReferral(payload.referralNo, payload.phone);
  const user: AuthUser = {
    id: nextId('agent'),
    name: payload.name,
    email: payload.email,
    phone: payload.phone,
    role: payload.role ?? 'FIELD_AGENT',
    active: true,
  };
  users.push(user);
  if (user.role === 'FIELD_AGENT') {
    registerAgentExtra(user.id, referralNo);
  }
  return user;
});

route('PUT', '/api/admin/agents/:id/download-enabled', (req, [id]) => {
  const agent = users.find((u) => u.id === id);
  if (!agent) throw new MockError('User not found', 404);
  if (agent.role !== 'FIELD_AGENT') throw new MockError('Only a field agent downloads voter lists', 400);
  agent.downloadEnabled = body<{ enabled: boolean }>(req).enabled;
  return agent;
});

route('PATCH', '/api/admin/users/:id/status', (req, [id]) => {
  const caller = requireUser();
  const user = users.find((u) => u.id === id);
  if (!user) throw new MockError('User not found', 404);
  if (isAdminRole(user.role) && caller.role !== 'SUPER_ADMIN') {
    throw new MockError('Only a super admin can manage admin accounts', 403);
  }
  const { active } = body<{ active: boolean }>(req);
  // FR-A13: someone must always be able to administer K-Pulse.
  if (!active && user.role === 'SUPER_ADMIN' && users.filter((u) => u.role === 'SUPER_ADMIN' && u.active).length <= 1) {
    throw new MockError(
      'This is the last active super admin — create another one first, or no one could administer K-Pulse',
      409,
    );
  }
  user.active = active;
  return user;
});

/* --- admin: reports --- */

route('GET', '/api/admin/reports/summary', (req) => {
  const level = (q(req, 'level') ?? 'BOOTH') as UnitLevel;
  const filters: SummaryFilters = {
    candidateId: q(req, 'candidateId'),
    sentiment: q(req, 'sentiment'),
    confidence: q(req, 'confidence'),
    from: q(req, 'from'),
    to: q(req, 'to'),
  };
  return { rows: summarise(level, filters), confidence: confidenceSummary(filters) };
});

route('GET', '/api/admin/reports/export', (req) => {
  const format = (q(req, 'format') ?? 'excel').toLowerCase();
  const level = (q(req, 'level') ?? 'BOOTH') as UnitLevel;
  const rows = summarise(level, {
    candidateId: q(req, 'candidateId'),
    sentiment: q(req, 'sentiment'),
    confidence: q(req, 'confidence'),
    from: q(req, 'from'),
    to: q(req, 'to'),
  });
  // Mock export is a CSV blob; the backend returns a real .pdf / .xlsx.
  const header = 'Unit,Level,Positive,Neutral,Negative,Not recorded,Total\n';
  const csv = rows
    .map((r) => [r.unitName, r.unitLevel, r.positive, r.neutral, r.negative, r.notRecorded, r.total].join(','))
    .join('\n');
  return new Blob([`K-Pulse ${format} report (mock)\n${header}${csv}\n`], { type: 'text/csv' });
});

/* --- agent: voter list PDF (FR-U13) --- */

const EXPORT_FILTERS = ['POSITIVE', 'NEUTRAL', 'NEGATIVE', 'NOT_RECORDED'] as const;
type ExportFilter = (typeof EXPORT_FILTERS)[number];

const EXPORT_TOKENS: Record<ExportFilter, string> = {
  POSITIVE: 'Positive',
  NEUTRAL: 'Neutral',
  NEGATIVE: 'Negative',
  NOT_RECORDED: 'NotRecorded',
};

/** The same 28 rows a page the server lays the file out at. */
const ROWS_PER_PAGE = 28;
const MAX_EXPORT_ROWS = 800;

function exportFilters(req: MockRequest): ExportFilter[] {
  const raw = q(req, 'sentiment') ?? '';
  const asked = raw
    .split(',')
    .map((part) => part.trim().toUpperCase())
    .filter(Boolean);
  return EXPORT_FILTERS.filter((filter) => asked.includes(filter));
}

/**
 * The agent's booth, the candidate their grant names, and the refusals the real
 * server would give first — the phone must behave the same in demo mode.
 */
function exportContext(boothId: string) {
  const agent = requireUser();
  const booth = unitById(boothId);
  const grant = grantsFor(agent.id).find((g) => boothIdsUnder(g.unitId).includes(boothId));
  if (!booth || !grant) {
    const expired = accessRequests.find(
      (r) =>
        r.agentId === agent.id &&
        r.status === 'APPROVED' &&
        r.expiresAt !== null &&
        boothIdsUnder(r.unitId).includes(boothId),
    );
    if (expired?.expiresAt) {
      throw new MockError(`Your access to this booth expired on ${formatShortDate(expired.expiresAt)}`, 403);
    }
    throw new MockError('You do not have access to that booth', 403);
  }
  if (!users.find((u) => u.id === agent.id)?.downloadEnabled) {
    throw new MockError('Downloads are turned off for your account. Ask your admin.', 403);
  }
  return { agent, booth, grant };
}

function exportRows(boothId: string, candidateId: string, chosen: ExportFilter[]) {
  return voters
    .filter((voter) => voter.boothId === boothId)
    .map((voter) => {
      const entry = sentimentEntries.find((e) => e.voterId === voter.id && e.candidateId === candidateId);
      return { voter, entry, sentiment: (entry?.sentiment ?? 'NOT_RECORDED') as ExportFilter };
    })
    .filter((row) => chosen.includes(row.sentiment))
    .sort((a, b) => {
      // Roll order: by house number, numerically, as the server sorts it.
      const house = (Number(a.voter.houseNo) || 0) - (Number(b.voter.houseNo) || 0);
      return house !== 0 ? house : a.voter.name.localeCompare(b.voter.name);
    });
}

function exportFileName(boothName: string, chosen: ExportFilter[]) {
  const digits = boothName.replace(/\D+/g, '');
  const label = digits ? `Booth${digits}` : boothName.replace(/[^A-Za-z0-9]+/g, '');
  const tokens =
    chosen.length === EXPORT_FILTERS.length
      ? 'All'
      : EXPORT_FILTERS.filter((f) => chosen.includes(f))
          .map((f) => EXPORT_TOKENS[f])
          .join('-');
  return `${label}_${tokens}_${nowIso().slice(0, 10)}.pdf`;
}

route('GET', '/api/agent/voter-list/export/count', (req) => {
  const boothId = q(req, 'unitId') ?? '';
  const { booth, grant } = exportContext(boothId);
  const chosen = exportFilters(req);
  const matches = exportRows(boothId, grant.candidateId, chosen);
  const inBooth = voters.filter((v) => v.boothId === boothId);
  const count = (filter: ExportFilter) =>
    exportRows(boothId, grant.candidateId, [filter]).length;

  const [, blockName = null] = booth.path.split(' > ');
  return {
    boothId,
    boothName: booth.name,
    blockName,
    boothPath: booth.path,
    candidateId: grant.candidateId,
    candidateName: grant.candidateName,
    totalVoters: inBooth.length,
    counts: {
      positive: count('POSITIVE'),
      neutral: count('NEUTRAL'),
      negative: count('NEGATIVE'),
      notRecorded: count('NOT_RECORDED'),
    },
    matching: matches.length,
    pages: Math.ceil(matches.length / ROWS_PER_PAGE),
    preview: matches.slice(0, 3).map((row) => ({
      name: row.voter.name,
      houseNo: row.voter.houseNo,
      sentiment: row.sentiment,
    })),
    fileName: exportFileName(booth.name, chosen),
    maxRows: MAX_EXPORT_ROWS,
  };
});

route('GET', '/api/agent/voter-list/export.pdf', (req) => {
  const boothId = q(req, 'unitId') ?? '';
  const { agent, booth, grant } = exportContext(boothId);
  const chosen = exportFilters(req);
  if (chosen.length === 0) throw new MockError('Choose at least one sentiment to download', 400);

  const matches = exportRows(boothId, grant.candidateId, chosen);
  if (matches.length === 0) throw new MockError('No voters match that selection', 400);
  if (matches.length > MAX_EXPORT_ROWS) {
    throw new MockError('That is more voters than a single file holds — narrow the sentiment selection', 422);
  }

  // Demo mode has no PDF engine, so this is a text stand-in with the same name
  // and the same watermark line the real file carries on every page.
  const lines = matches.map(
    (row, i) =>
      `${i + 1}. ${row.voter.name} · ${row.voter.relation} · ${row.voter.age} · ${row.voter.gender} · H${row.voter.houseNo} · ${row.sentiment}`,
  );
  const body = [
    `${booth.name} — voter list (demo)`,
    booth.path,
    `Candidate ${grant.candidateName} · ${matches.length} voter(s)`,
    '',
    ...lines,
    '',
    `Downloaded by ${agent.name} · ${nowIso()} · Not for sharing`,
  ].join('\n');
  return new Blob([body], { type: 'application/pdf' });
});

/* --- agent --- */

route('GET', '/api/agent/access-requests', () => {
  const agent = requireUser();
  return accessRequests
    .filter((r) => r.agentId === agent.id)
    .slice()
    .sort((a, b) => b.requestedAt.localeCompare(a.requestedAt));
});

/**
 * The candidate behind a name an agent typed, added if the name is new -- the
 * same matching the server does, on the trimmed name ignoring case.
 */
function findOrCreateCandidate(name: string): Candidate {
  const clean = (name ?? '').trim().replace(/\s+/g, ' ');
  if (!clean) throw new MockError("Enter the candidate's name", 400);
  const existing = candidates.find((c) => c.name.toLowerCase() === clean.toLowerCase());
  if (existing) return existing;
  const candidate: Candidate = { id: nextId('cand'), name: clean, party: null, unitId: null, unitName: null };
  candidates.push(candidate);
  return candidate;
}

route('POST', '/api/agent/access-requests', (req) => {
  const agent = requireUser();
  const { unitId, candidateName } = body<{ unitId: string; candidateName: string }>(req);
  const unit = unitById(unitId);
  if (!unit) throw new MockError('Pick a unit and a candidate', 400);
  // The server grants a panchayat at a time, whatever a stale client sends.
  if (unit.level !== 'PANCHAYAT') {
    throw new MockError(
      `Access is requested one panchayat at a time, and ${unit.name} is a ${unit.level.toLowerCase()}`,
      400,
    );
  }
  const candidate = findOrCreateCandidate(candidateName);
  const duplicate = accessRequests.find(
    (r) => r.agentId === agent.id && r.unitId === unitId && r.candidateId === candidate.id && r.status === 'PENDING',
  );
  if (duplicate) throw new MockError('You already have a pending request for that unit and candidate', 409);
  const request: AccessRequest = {
    id: nextId('acc'),
    agentId: agent.id,
    agentName: agent.name,
    unitId,
    unitLevel: unit.level,
    unitName: unit.name,
    unitPath: unit.path,
    candidateId: candidate.id,
    candidateName: candidate.name,
    status: 'PENDING',
    requestedAt: nowIso(),
    decidedAt: null,
    expiresAt: null,
    reviewerNote: null,
  };
  accessRequests.unshift(request);
  return request;
});

/** Booths the signed-in agent may actually open, with the candidate attached. */
route('GET', '/api/agent/booths', () => {
  const agent = requireUser();
  return grantsFor(agent.id).flatMap((grant) =>
    boothIdsUnder(grant.unitId)
      .map((id) => unitById(id))
      .filter((u): u is Unit => Boolean(u))
      .map((booth) => {
        // Same split the server does, off the same root-first path.
        const [districtName = null, blockName = null, panchayatName = null] = booth.path.split(' > ');
        return {
          boothId: booth.id,
          boothName: booth.name,
          path: booth.path,
          districtName,
          blockName,
          panchayatName,
          candidateId: grant.candidateId,
          candidateName: grant.candidateName,
          accessRequestId: grant.id,
          expiresAt: grant.expiresAt,
        };
      }),
  );
});

/* ---------------- FR-U12: pre-election, house-level sentiment ---------------- */

function requireHouseGrant(boothId: string, candidateId: string) {
  const agent = requireUser();
  const grant = accessibleBooths(agent.id).find((b) => b.id === boothId);
  if (!grant) throw new MockError('You do not have access to that booth', 403);
  if (candidateId && candidateId !== 'cand-1') {
    throw new MockError('Your access to this booth records sentiment for another candidate', 400);
  }
  return agent;
}

route('GET', '/api/agent/houses', (req) => {
  const boothId = q(req, 'boothId') ?? '';
  const candidateId = q(req, 'candidateId') ?? '';
  requireHouseGrant(boothId, candidateId);
  return houseSentimentEntries
    .filter((h) => h.boothId === boothId && h.candidateId === candidateId)
    .slice()
    .sort((a, b) => b.updatedAt.localeCompare(a.updatedAt));
});

function houseSummary(req: MockRequest) {
  requireUser();
  const level = (q(req, 'level') ?? 'BOOTH') as UnitLevel;
  const unitId = q(req, 'unitId');
  const candidateId = q(req, 'candidateId');
  const scope = unitId ? new Set(boothIdsUnder(unitId)) : null;
  const confidence = q(req, 'confidence');
  const from = q(req, 'from');
  const to = q(req, 'to');
  const pool = houseSentimentEntries.filter(
    (h) =>
      (!scope || scope.has(h.boothId)) &&
      (!candidateId || h.candidateId === candidateId) &&
      (!confidence || confidence === 'ALL' || h.confidence === confidence) &&
      (!from || h.recordedAt >= from) &&
      (!to || h.recordedAt <= to),
  );

  const rows: HouseUnitSummary[] = units
    .filter((u) => u.level === level)
    // A unit with no booth inside the chosen unit is not part of this report,
    // the same rule the server applies.
    .filter((u) => !scope || boothIdsUnder(u.id).some((id) => scope.has(id)))
    .map((unit) => {
      const booths = new Set(boothIdsUnder(unit.id));
      const mine = pool.filter((h) => booths.has(h.boothId));
      const sum = (pick: (h: (typeof mine)[number]) => number) => mine.reduce((total, h) => total + pick(h), 0);
      return {
        unitId: unit.id,
        unitName: unit.name,
        unitLevel: unit.level,
        houses: mine.length,
        people: sum((h) => h.headcount),
        residents: sum((h) => h.residentialCount),
        positive: sum((h) => h.positiveCount),
        neutral: sum((h) => h.neutralCount),
        negative: sum((h) => h.negativeCount),
      };
    })
    .filter((row) => boothIdsUnder(row.unitId).length > 0)
    .sort((a, b) => b.houses - a.houses);

  const total = (pick: (r: HouseUnitSummary) => number) => rows.reduce((sum, r) => sum + pick(r), 0);
  const levelOf = (value: string) => pool.filter((h) => h.confidence === value).length;
  return {
    rows,
    totals: {
      houses: total((r) => r.houses),
      people: total((r) => r.people),
      residents: total((r) => r.residents),
      positive: total((r) => r.positive),
      neutral: total((r) => r.neutral),
      negative: total((r) => r.negative),
      confidence: { high: levelOf('HIGH'), medium: levelOf('MEDIUM'), low: levelOf('LOW') },
    },
  };
}

route('GET', '/api/admin/house-sentiment/summary', (req) => houseSummary(req));

function houseRows(req: MockRequest) {
  requireUser();
  const unitId = q(req, 'unitId') ?? '';
  const candidateId = q(req, 'candidateId');
  const confidence = q(req, 'confidence');
  const from = q(req, 'from');
  const to = q(req, 'to');
  const scope = new Set(boothIdsUnder(unitId));
  const numeric = (houseNo: string) => {
    const digits = /^\d+/.exec(houseNo);
    return digits ? Number(digits[0]) : Number.POSITIVE_INFINITY;
  };
  return houseSentimentEntries
    .filter(
      (h) =>
        scope.has(h.boothId) &&
        (!candidateId || h.candidateId === candidateId) &&
        (!confidence || confidence === 'ALL' || h.confidence === confidence) &&
        (!from || h.recordedAt >= from) &&
        (!to || h.recordedAt <= to),
    )
    .map((h): HouseEntryRow => {
      const booth = unitById(h.boothId);
      return {
        ...h,
        boothName: booth?.name ?? 'Booth',
        boothPath: booth?.path ?? '',
        candidateName: candidates.find((c) => c.id === h.candidateId)?.name ?? 'Candidate',
      };
    })
    .sort((a, b) => numeric(a.houseNo) - numeric(b.houseNo) || a.houseNo.localeCompare(b.houseNo));
}

route('GET', '/api/admin/house-sentiment/houses', (req) => houseRows(req));

route('GET', '/api/admin/house-sentiment/houses/export', (req) => {
  const format = (q(req, 'format') ?? 'excel').toLowerCase();
  const rows = houseRows(req);
  const unit = unitById(q(req, 'unitId') ?? '');
  // Mock export is a CSV blob; the backend returns a real .pdf / .xlsx.
  const header = 'House no.,House name,Booth,Ward,People,Residents,Positive,Neutral,Negative,Confidence,Recorded by\n';
  const csv = rows
    .map((r) =>
      [
        r.houseNo,
        r.houseName,
        r.boothName,
        r.wardNo ?? '',
        r.headcount,
        r.residentialCount,
        r.positiveCount,
        r.neutralCount,
        r.negativeCount,
        r.confidence,
        r.recordedByName,
      ].join(','),
    )
    .join('\n');
  return new Blob([`K-Pulse houses in ${unit?.name ?? 'unit'} (${format} mock)\n${header}${csv}\n`], {
    type: 'text/csv',
  });
});

route('GET', '/api/admin/house-sentiment/export', (req) => {
  const format = (q(req, 'format') ?? 'excel').toLowerCase();
  const summary = houseSummary(req);
  // Mock export is a CSV blob; the backend returns a real .pdf / .xlsx.
  const header = 'Unit,Level,Houses,People,Residents,Positive,Neutral,Negative\n';
  const csv = summary.rows
    .map((r) => [r.unitName, r.unitLevel, r.houses, r.people, r.residents, r.positive, r.neutral, r.negative].join(','))
    .join('\n');
  return new Blob([`K-Pulse pre-election ${format} report (mock)\n${header}${csv}\n`], { type: 'text/csv' });
});

/* ------------------------ FR-A15: candidate sentiment ------------------------ */

/** Same rule as the server: positive share minus negative share, neutrals dilute. */
function splitOf(positive: number, neutral: number, negative: number) {
  const total = positive + neutral + negative;
  const pct = (part: number) => (total === 0 ? 0 : (part * 100) / total);
  const netLead = pct(positive) - pct(negative);
  return {
    positive,
    neutral,
    negative,
    total,
    positivePercent: pct(positive),
    neutralPercent: pct(neutral),
    negativePercent: pct(negative),
    netLead,
    verdict: total === 0 ? 'NO_DATA' : netLead > 0 ? 'POSITIVE' : 'NEGATIVE',
  } as const;
}

function statusOf(split: ReturnType<typeof splitOf>) {
  if (split.total === 0) return 'NO_DATA';
  if (split.netLead >= 20) return 'SAFE';
  return split.netLead >= 5 ? 'WATCH' : 'AT_RISK';
}

/** Booth tallies for one candidate, from both datasets, as the server combines them. */
function candidateTallies(candidateId: string, source: string, from?: string | null) {
  const perBooth = new Map<string, { positive: number; neutral: number; negative: number }>();
  const bucket = (boothId: string) => {
    if (!perBooth.has(boothId)) perBooth.set(boothId, { positive: 0, neutral: 0, negative: 0 });
    return perBooth.get(boothId)!;
  };

  if (source !== 'HOUSE') {
    sentimentEntries
      .filter((e) => e.candidateId === candidateId && (!from || e.recordedAt >= from))
      .forEach((entry) => {
        const voter = voters.find((v) => v.id === entry.voterId);
        if (!voter) return;
        const tally = bucket(voter.boothId);
        if (entry.sentiment === 'POSITIVE') tally.positive += 1;
        else if (entry.sentiment === 'NEUTRAL') tally.neutral += 1;
        else tally.negative += 1;
      });
  }
  if (source !== 'VOTER') {
    houseSentimentEntries
      .filter((h) => h.candidateId === candidateId && (!from || h.recordedAt >= from))
      .forEach((house) => {
        const tally = bucket(house.boothId);
        // People, not houses: a house of six weighs six.
        tally.positive += house.positiveCount;
        tally.neutral += house.neutralCount;
        tally.negative += house.negativeCount;
      });
  }
  return perBooth;
}

route('GET', '/api/admin/candidate-sentiment/candidates', () => {
  requireUser();
  const withData = new Set([
    ...sentimentEntries.map((e) => e.candidateId),
    ...houseSentimentEntries.map((h) => h.candidateId),
  ]);
  return candidates.filter((c) => withData.has(c.id));
});

route('GET', '/api/admin/candidate-sentiment/:id', (req, [id]) => {
  requireUser();
  const candidate = candidates.find((c) => c.id === id);
  if (!candidate) throw new MockError('Unknown candidate', 404);

  const asked = q(req, 'level') as UnitLevel | null;
  // With nothing asked for, start at the panchayat this candidate contests.
  const startedAtCandidateUnit = !q(req, 'parentUnitId') && !asked && Boolean(candidate.unitId);
  const parentUnitId = q(req, 'parentUnitId') ?? (startedAtCandidateUnit ? candidate.unitId : null);
  const source = q(req, 'source') ?? 'ALL';
  const from = q(req, 'from');
  const parent = parentUnitId ? unitById(parentUnitId) : undefined;

  const level = ((): UnitLevel => {
    if (asked) return asked;
    if (startedAtCandidateUnit) return 'BOOTH';
    if (!parent) return 'DISTRICT';
    if (parent.level === 'DISTRICT') return 'BLOCK';
    if (parent.level === 'BLOCK') return 'PANCHAYAT';
    return 'BOOTH';
  })();

  const perBooth = candidateTallies(candidate.id, source, from);
  const inScope = parentUnitId ? new Set(boothIdsUnder(parentUnitId)) : null;

  const rows = units
    .filter((u) => u.level === level)
    .filter((u) => !parentUnitId || boothIdsUnder(u.id).some((b) => inScope!.has(b)))
    .map((unit) => {
      const booths = boothIdsUnder(unit.id).filter((b) => !inScope || inScope.has(b));
      const sum = booths.reduce(
        (acc, boothId) => {
          const tally = perBooth.get(boothId);
          return tally
            ? {
                positive: acc.positive + tally.positive,
                neutral: acc.neutral + tally.neutral,
                negative: acc.negative + tally.negative,
              }
            : acc;
        },
        { positive: 0, neutral: 0, negative: 0 },
      );
      const split = splitOf(sum.positive, sum.neutral, sum.negative);
      return {
        unitId: unit.id,
        unitName: unit.name,
        unitLevel: unit.level,
        split,
        netLead: split.netLead,
        status: statusOf(split),
        drillable: unit.level !== 'BOOTH',
      };
    })
    .filter((row) => boothIdsUnder(row.unitId).length > 0);

  const totals = rows.reduce(
    (acc, r) => ({
      positive: acc.positive + r.split.positive,
      neutral: acc.neutral + r.split.neutral,
      negative: acc.negative + r.split.negative,
    }),
    { positive: 0, neutral: 0, negative: 0 },
  );

  return {
    candidateId: candidate.id,
    candidateName: candidate.name,
    candidateUnitId: candidate.unitId ?? null,
    candidateUnitName: candidate.unitId ? (unitById(candidate.unitId)?.name ?? null) : null,
    level,
    parentUnitId: parentUnitId ?? null,
    parentUnitPath: parent?.path ?? null,
    totals: splitOf(totals.positive, totals.neutral, totals.negative),
    rows,
  };
});

/* ------------------------ account deletion (public + admin) ------------------ */

function fileDeletion(agent: AuthUser, reason?: string): AccountDeletionRequest {
  const pending = accountDeletionRequests.find((d) => d.agentPhone === agent.phone && d.status === 'PENDING');
  if (pending) return pending;
  const filed: AccountDeletionRequest = {
    id: nextId('del'),
    userId: agent.id,
    agentName: agent.name,
    agentPhone: agent.phone ?? '',
    agentEmail: agent.email ?? null,
    reason: reason?.trim() || null,
    status: 'PENDING',
    requestedAt: nowIso(),
    reviewedAt: null,
    reviewedByName: null,
    reviewerNote: null,
    deletedEntries: null,
  };
  accountDeletionRequests.push(filed);
  return filed;
}

route('POST', '/api/auth/account-deletion', (req) => {
  const { phone, reason } = body<{ phone: string; reason?: string }>(req);
  const agent = users.find((u) => u.role === 'FIELD_AGENT' && u.phone === phone);
  // Same answer either way, so the page cannot be used to test a number.
  if (agent) fileDeletion(agent, reason);
  return null;
});

route('POST', '/api/agent/account-deletion', (req) => {
  const { note } = body<{ note?: string }>(req);
  return fileDeletion(requireUser(), note);
});

route('GET', '/api/admin/account-deletions', (req) => {
  requireUser();
  const status = q(req, 'status');
  return accountDeletionRequests
    .filter((d) => !status || d.status === status)
    .slice()
    .sort((a, b) => b.requestedAt.localeCompare(a.requestedAt));
});

route('POST', '/api/admin/account-deletions/:id/approve', (req, [id]) => {
  const admin = requireUser();
  const request = accountDeletionRequests.find((d) => d.id === id);
  if (!request) throw new MockError('Deletion request not found', 404);
  if (request.status !== 'PENDING') throw new MockError(`This request was already ${request.status.toLowerCase()}`, 409);

  let erased = 0;
  const drop = <T,>(list: T[], match: (item: T) => boolean) => {
    for (let i = list.length - 1; i >= 0; i--) {
      if (match(list[i])) {
        list.splice(i, 1);
        erased += 1;
      }
    }
  };
  if (request.userId) {
    const agentId = request.userId;
    drop(sentimentEntries, (e) => e.recordedById === agentId);
    drop(houseSentimentEntries, (h) => h.recordedById === agentId);
    drop(voterChangeRequests, (c) => c.agentId === agentId);
    drop(accessRequests, (a) => a.agentId === agentId);
    const index = users.findIndex((u) => u.id === agentId);
    if (index !== -1) users.splice(index, 1);
  }
  request.userId = null;
  request.status = 'APPROVED';
  request.reviewedAt = nowIso();
  request.reviewedByName = admin.name;
  request.reviewerNote = body<{ note?: string }>(req)?.note?.trim() || null;
  request.deletedEntries = erased;
  return request;
});

route('POST', '/api/admin/account-deletions/:id/reject', (req, [id]) => {
  const admin = requireUser();
  const request = accountDeletionRequests.find((d) => d.id === id);
  if (!request) throw new MockError('Deletion request not found', 404);
  if (request.status !== 'PENDING') throw new MockError(`This request was already ${request.status.toLowerCase()}`, 409);
  request.status = 'REJECTED';
  request.reviewedAt = nowIso();
  request.reviewedByName = admin.name;
  request.reviewerNote = body<{ note?: string }>(req)?.note?.trim() || null;
  return request;
});

route('GET', '/api/agent/houses/insights', (req) => {
  const agent = requireUser();
  const boothId = q(req, 'boothId');
  const candidateId = q(req, 'candidateId');
  const allowed = accessibleBooths(agent.id).map((b) => b.id);
  if (boothId && !allowed.includes(boothId)) throw new MockError('You do not have access to that booth', 403);
  const scope = boothId ? [boothId] : allowed;
  const rows = houseSentimentEntries.filter(
    (h) => scope.includes(h.boothId) && (!candidateId || h.candidateId === candidateId),
  );
  const sum = (pick: (h: HouseSentimentEntry) => number) => rows.reduce((total, h) => total + pick(h), 0);
  const level = (value: string) => rows.filter((h) => h.confidence === value).length;
  return {
    houses: rows.length,
    people: sum((h) => h.headcount),
    residents: sum((h) => h.residentialCount),
    positive: sum((h) => h.positiveCount),
    neutral: sum((h) => h.neutralCount),
    negative: sum((h) => h.negativeCount),
    confidence: { high: level('HIGH'), medium: level('MEDIUM'), low: level('LOW') },
  };
});

route('POST', '/api/agent/houses', (req) => {
  const payload = body<Omit<HouseSentimentEntry, 'id' | 'recordedById' | 'recordedByName' | 'recordedAt' | 'updatedAt'>>(req);
  const agent = requireHouseGrant(payload.boothId, payload.candidateId);
  if (payload.residentialCount > payload.headcount) {
    throw new MockError('A house cannot have more residents than people', 400);
  }
  // The breakdown covers the residents, not everyone counted at the house.
  const split = payload.positiveCount + payload.neutralCount + payload.negativeCount;
  if (split !== payload.residentialCount) {
    throw new MockError(
      `Positive, neutral and negative add up to ${split}, but the house has ${payload.residentialCount} resident(s)`,
      400,
    );
  }
  const houseNo = payload.houseNo.trim().toUpperCase();
  // Recording the same house again overwrites that tally, as on the server.
  const existing = houseSentimentEntries.find(
    (h) => h.boothId === payload.boothId && h.candidateId === payload.candidateId && h.houseNo === houseNo,
  );
  const entry: HouseSentimentEntry = {
    ...(existing ?? { id: nextId('hse'), recordedAt: nowIso() }),
    ...payload,
    houseNo,
    houseName: payload.houseName.trim(),
    recordedById: agent.id,
    recordedByName: agent.name,
    recordedAt: existing?.recordedAt ?? nowIso(),
    updatedAt: nowIso(),
  };
  if (existing) houseSentimentEntries[houseSentimentEntries.indexOf(existing)] = entry;
  else houseSentimentEntries.push(entry);
  return entry;
});

route('DELETE', '/api/agent/houses/:id', (_req, [id]) => {
  const index = houseSentimentEntries.findIndex((h) => h.id === id);
  if (index === -1) throw new MockError('House entry not found', 404);
  requireHouseGrant(houseSentimentEntries[index].boothId, houseSentimentEntries[index].candidateId);
  houseSentimentEntries.splice(index, 1);
  return null;
});

route('GET', '/api/agent/voters', (req) => {
  const agent = requireUser();
  const allowed = new Set(accessibleBooths(agent.id).map((b) => b.id));
  const boothId = q(req, 'boothId');
  if (boothId && !allowed.has(boothId)) throw new MockError('You do not have access to that booth', 403);
  const pool = voters.filter((v) => allowed.has(v.boothId));
  const page = Number(q(req, 'page') ?? 0);
  const size = Number(q(req, 'size') ?? 50);
  return paginate(filterVoters(req, pool, 'HOUSE_NO'), page, size);
});

route('GET', '/api/agent/voters/:id', (_req, [id]) => {
  const agent = requireUser();
  const voter = voters.find((v) => v.id === id);
  if (!voter) throw new MockError('Voter not found', 404);
  const allowed = new Set(accessibleBooths(agent.id).map((b) => b.id));
  if (!allowed.has(voter.boothId)) throw new MockError('You do not have access to that booth', 403);
  const entry = sentimentEntries.find((e) => e.voterId === voter.id);
  return { voter, entry: entry ?? null };
});

route('POST', '/api/agent/voters/:id/sentiment', (req, [id]) => {
  const agent = requireUser();
  const voter = voters.find((v) => v.id === id);
  if (!voter) throw new MockError('Voter not found', 404);
  const payload = body<{
    candidateId: string;
    sentiment: SentimentValue;
    confidence: 'HIGH' | 'MEDIUM' | 'LOW';
    resident: boolean;
    wardNo?: number;
    caste?: Voter['caste'];
  }>(req);

  const existing = sentimentEntries.find((e) => e.voterId === voter.id && e.candidateId === payload.candidateId);
  voter.sentiment = payload.sentiment;
  voter.confidence = payload.confidence;
  if (payload.wardNo) voter.wardNo = payload.wardNo;
  // Unanswered leaves the voter's caste alone, as the server does.
  if (payload.caste) voter.caste = payload.caste;

  if (existing) {
    // FR-U10: entries stay editable forever, no time window.
    Object.assign(existing, {
      sentiment: payload.sentiment,
      confidence: payload.confidence,
      resident: payload.resident,
      wardNo: payload.wardNo ?? existing.wardNo,
      updatedAt: nowIso(),
    });
    return existing;
  }

  const entry = {
    id: nextId('se'),
    voterId: voter.id,
    candidateId: payload.candidateId,
    sentiment: payload.sentiment,
    confidence: payload.confidence,
    resident: payload.resident,
    wardNo: payload.wardNo ?? voter.wardNo,
    recordedById: agent.id,
    recordedByName: agent.name,
    recordedAt: nowIso(),
    updatedAt: nowIso(),
  };
  sentimentEntries.push(entry);
  return entry;
});

route('GET', '/api/agent/voter-changes', () => {
  const agent = requireUser();
  return voterChangeRequests
    .filter((r) => r.agentId === agent.id)
    .slice()
    .sort((a, b) => b.proposedAt.localeCompare(a.proposedAt));
});

route('POST', '/api/agent/voter-changes', (req) => {
  const agent = requireUser();
  const payload = body<{
    type: VoterChangeRequest['type'];
    voterId?: string | null;
    boothId: string;
    payload: VoterChangePayload;
  }>(req);
  const booth = unitById(payload.boothId);
  if (!booth) throw new MockError('Unknown booth', 400);
  const change: VoterChangeRequest = {
    id: nextId('vcr'),
    type: payload.type,
    status: 'PENDING',
    agentId: agent.id,
    agentName: agent.name,
    voterId: payload.voterId ?? null,
    voterName: voters.find((v) => v.id === payload.voterId)?.name ?? null,
    boothId: booth.id,
    unitPath: booth.path,
    payload: payload.payload,
    proposedAt: nowIso(),
    decidedAt: null,
    reviewerNote: null,
  };
  voterChangeRequests.unshift(change);
  return change;
});

route('GET', '/api/agent/insights', (req) => {
  const agent = requireUser();
  const allowed = accessibleBooths(agent.id).map((b) => b.id);
  const boothId = q(req, 'boothId');
  const scope = new Set(boothId ? [boothId] : allowed);
  const filters: SummaryFilters = {
    candidateId: q(req, 'candidateId'),
    residentsOnly: q(req, 'residentsOnly') === 'true',
  };
  return {
    rows: summarise('BOOTH', filters, scope),
    confidence: confidenceSummary(filters, scope),
  };
});

/* ----------------------------- entry point ------------------------------ */

export function handleMock<T>(method: string, path: string, req: MockRequest): Promise<T> {
  const match = routes.find((r) => r.method === method && r.pattern.test(path));
  if (!match) {
    return Promise.reject(new MockError(`No mock route for ${method} ${path}`, 404));
  }
  const params = match.pattern.exec(path)!.slice(1);
  try {
    return delay(match.handler(req, params) as T);
  } catch (error) {
    return Promise.reject(error);
  }
}

export function mockSignOut() {
  currentUser = null;
}

export function setMockUser(user: AuthUser | null) {
  currentUser = user;
}
