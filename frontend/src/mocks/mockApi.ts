import { isAdminRole } from '@/shared/types';
import type {
  AccessRequest,
  AccountDeletionRequest,
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
      const positive = scoped.filter((e) => e.sentiment === 'POSITIVE').length;
      const neutral = scoped.filter((e) => e.sentiment === 'NEUTRAL').length;
      const negative = scoped.filter((e) => e.sentiment === 'NEGATIVE').length;
      return {
        unitId: unit.id,
        unitName: unit.name,
        unitLevel: unit.level,
        positive,
        neutral,
        negative,
        notRecorded: Math.max(0, totalVoters - scoped.length),
        total: totalVoters,
      } satisfies SentimentSummary;
    })
    .filter((s): s is SentimentSummary => s !== null);
}

function confidenceSummary(filters: SummaryFilters, restrictToBooths?: Set<string>): ConfidenceSummary {
  const entries = entriesMatching(filters).filter((e) => {
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
  currentUser = agent;
  return { token: `mock.${agent.id}`, user: agent } satisfies AuthSession;
});

route('POST', '/api/auth/agent/signup', (req) => {
  const payload = body<{ name: string; email: string; phone: string; address: string }>(req);
  if (users.some((u) => u.phone === payload.phone)) {
    throw new MockError('That phone number is already registered', 409);
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
  return { message: 'Account created. Sign in with the OTP sent to your phone.', devOtp: DEV_OTP };
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
  const payload = body<{ name: string; email: string; phone: string; role?: AuthUser['role'] }>(req);
  if (users.some((u) => u.phone === payload.phone)) throw new MockError('That phone number is already registered', 409);
  const user: AuthUser = {
    id: nextId('agent'),
    name: payload.name,
    email: payload.email,
    phone: payload.phone,
    role: payload.role ?? 'FIELD_AGENT',
    active: true,
  };
  users.push(user);
  return user;
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

/* --- agent --- */

route('GET', '/api/agent/access-requests', () => {
  const agent = requireUser();
  return accessRequests
    .filter((r) => r.agentId === agent.id)
    .slice()
    .sort((a, b) => b.requestedAt.localeCompare(a.requestedAt));
});

route('POST', '/api/agent/access-requests', (req) => {
  const agent = requireUser();
  const { unitId, candidateId } = body<{ unitId: string; candidateId: string }>(req);
  const unit = unitById(unitId);
  const candidate = candidates.find((c) => c.id === candidateId);
  if (!unit || !candidate) throw new MockError('Pick a unit and a candidate', 400);
  const duplicate = accessRequests.find(
    (r) => r.agentId === agent.id && r.unitId === unitId && r.candidateId === candidateId && r.status === 'PENDING',
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
    candidateId,
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
      .map((booth) => ({
        boothId: booth.id,
        boothName: booth.name,
        path: booth.path,
        candidateId: grant.candidateId,
        candidateName: grant.candidateName,
        accessRequestId: grant.id,
        expiresAt: grant.expiresAt,
      })),
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
  }>(req);

  const existing = sentimentEntries.find((e) => e.voterId === voter.id && e.candidateId === payload.candidateId);
  voter.sentiment = payload.sentiment;
  voter.confidence = payload.confidence;
  if (payload.wardNo) voter.wardNo = payload.wardNo;

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
  const filters: SummaryFilters = { candidateId: q(req, 'candidateId') };
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
