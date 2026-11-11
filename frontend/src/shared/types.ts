/** Domain vocabulary shared by both route groups. Mirrors the Spring Boot enums. */

/** SUPER_ADMIN is unrestricted; ADMIN is scoped to one district, block or panchayat (FR-A9). */
export type Role = 'SUPER_ADMIN' | 'ADMIN' | 'FIELD_AGENT';

/** True for both admin kinds — the /admin route group, not the agent one. */
export function isAdminRole(role: Role) {
  return role === 'SUPER_ADMIN' || role === 'ADMIN';
}

export type UnitLevel = 'BOOTH' | 'PANCHAYAT' | 'BLOCK' | 'DISTRICT';

export type SentimentValue = 'POSITIVE' | 'NEUTRAL' | 'NEGATIVE';

export type ConfidenceLevel = 'HIGH' | 'MEDIUM' | 'LOW';

export type AccessRequestStatus = 'PENDING' | 'APPROVED' | 'REJECTED' | 'EXPIRED' | 'REVOKED';

export type VoterChangeType = 'ADD' | 'EDIT' | 'DELETE';

export type VoterChangeStatus = 'PENDING' | 'APPROVED' | 'REJECTED';

export type Gender = 'M' | 'F' | 'OTHER';

/** Reservation categories, not individual caste names. See shared/utils/caste. */
export type Caste = 'GENERAL' | 'SEBC' | 'SC' | 'ST' | 'MINORITY' | 'OTHER';

export interface AuthUser {
  id: string;
  name: string;
  email: string | null;
  phone: string | null;
  role: Role;
  active: boolean;
  /** FR-U13: field agents only — whether they may download a booth's voter list. */
  downloadEnabled?: boolean;
  /** FA-25: an admin has set a temporary password; choose a new one before anything else. */
  mustChangePassword?: boolean;
  /** FR-U14: whether this agent has the Reports page. The server checks again on every call. */
  reportsEnabled?: boolean;
  /** Set on ADMIN accounts only — a super admin sees every unit, an agent has none (FR-A9). */
  scopeLevel?: UnitLevel | null;
  scopeUnitId?: string | null;
  scopeUnitName?: string | null;
}

export interface AuthSession {
  token: string;
  user: AuthUser;
}

export interface Candidate {
  id: string;
  name: string;
  party: string | null;
  /** The panchayat they contest in; null for a candidate standing constituency-wide. */
  unitId: string | null;
  unitName: string | null;
}

export interface Unit {
  id: string;
  level: UnitLevel;
  name: string;
  /** Human-readable trail, e.g. "Bhadrak › Tihidi › Kansabansa › Booth 12". */
  path: string;
  parentId: string | null;
}

/** An account as the admin screens list it. */
export type AccountUser = AuthUser;

export interface Voter {
  id: string;
  epicNo: string;
  name: string;
  relation: string;
  houseNo: string | null;
  age: number;
  gender: Gender;
  boothId: string;
  boothName: string;
  wardNo: number | null;
  /** The agent's finding; null until one records it, since the roll has no caste. */
  caste: Caste | null;
  sentiment: SentimentValue | null;
  confidence: ConfidenceLevel | null;
}

export interface SentimentEntry {
  id: string;
  voterId: string;
  candidateId: string;
  sentiment: SentimentValue;
  confidence: ConfidenceLevel;
  resident: boolean;
  wardNo: number | null;
  recordedById: string;
  recordedByName: string;
  recordedAt: string;
  updatedAt: string;
}

/** FR-U12: a household tally, deliberately holding no per-person identity. */
export interface HouseSentimentEntry {
  id: string;
  boothId: string;
  candidateId: string;
  houseNo: string;
  houseName: string;
  wardNo: number | null;
  headcount: number;
  residentialCount: number;
  positiveCount: number;
  neutralCount: number;
  negativeCount: number;
  confidence: ConfidenceLevel;
  recordedById: string;
  recordedByName: string;
  recordedAt: string;
  updatedAt: string;
}

export interface AccessRequest {
  id: string;
  agentId: string;
  agentName: string;
  unitId: string;
  unitLevel: UnitLevel;
  unitName: string;
  unitPath: string;
  candidateId: string;
  candidateName: string;
  status: AccessRequestStatus;
  requestedAt: string;
  decidedAt: string | null;
  expiresAt: string | null;
  reviewerNote: string | null;
}

export interface VoterChangeRequest {
  id: string;
  type: VoterChangeType;
  status: VoterChangeStatus;
  agentId: string;
  agentName: string;
  voterId: string | null;
  /** Name of the voter the proposal targets — null for an ADD. */
  voterName: string | null;
  boothId: string;
  unitPath: string;
  payload: VoterChangePayload;
  proposedAt: string;
  decidedAt: string | null;
  reviewerNote: string | null;
}

export interface VoterChangePayload {
  epicNo?: string;
  name?: string;
  relation?: string;
  houseNo?: string;
  age?: number;
  gender?: Gender;
  wardNo?: number;
  reason?: string;
}

/** FR-A20: what the Field agents page says about an agent's access. */
export type AgentAccessState = 'APPROVED' | 'PENDING' | 'EXPIRED' | 'NONE';

export type PaymentMode = 'UPI' | 'BANK' | 'CASH';

/** One row of the Field agents page, and everything its detail panel shows. */
export interface FieldAgent {
  id: string;
  /** The readable id, A-00272. */
  agentCode: string | null;
  name: string;
  phone: string | null;
  email: string | null;
  address: string | null;
  /** Who the agent said sent them, at sign-up. Null for most. */
  referralNo: string | null;
  active: boolean;
  /** The unit the grant behind Area and Access was given for. */
  areaUnitId: string | null;
  panchayatName: string | null;
  blockName: string | null;
  districtName: string | null;
  candidateName: string | null;
  access: AgentAccessState;
  accessExpiresAt: string | null;
  votersRecorded: number;
  lastActive: string | null;
  joined: string | null;
  downloadEnabled: boolean;
  /** FR-A22: whether the agent has the Reports page in the app. */
  reportsEnabled: boolean;
  /** FA-25: whether a password exists at all. Never the password. */
  passwordSet: boolean;
  mustChangePassword: boolean;
  /** FA-24: when the agent asked for a reset; null when nobody is waiting. */
  passwordResetRequestedAt: string | null;
  paid: boolean;
  paidAmount: number | null;
  paidOn: string | null;
  paidMode: PaymentMode | null;
  paidRef: string | null;
  paidUpdatedAt: string | null;
  paidUpdatedByName: string | null;
}

/** The agent's own view of their sign-in (FA-24). */
export interface AgentPasswordStatus {
  passwordSet: boolean;
  mustChangePassword: boolean;
  resetRequestedAt: string | null;
}

/** FR-U14: one panchayat an agent may report on, and the candidate it was approved for. */
export interface AgentReportScope {
  panchayatId: string;
  panchayatName: string;
  blockName: string | null;
  districtName: string | null;
  candidateId: string;
  candidateName: string;
  expiresAt: string | null;
  booths: Array<{ id: string; name: string }>;
  wards: number[];
}

export interface AgentReportRow {
  boothId: string;
  boothName: string;
  positive: number;
  neutral: number;
  negative: number;
  /** Roll minus everyone with an entry at all — never narrowed by the date or confidence choice. */
  notRecorded: number;
  voters: number;
}

export interface AgentReport {
  booths: AgentReportRow[];
  /** The agent's booths together, not the whole panchayat. */
  totals: AgentReportRow;
  boothCount: number;
}

export interface FieldAgentPage {
  items: FieldAgent[];
  page: number;
  size: number;
  /** Matching the current filters, the payment tab included. */
  total: number;
  /** The three tab counts, taken across every filter but the payment one. */
  allCount: number;
  paidCount: number;
  unpaidCount: number;
  totalPages: number;
}

/** One dropdown option: "Bhadrak (1,415)". */
export interface AreaCount {
  unitId: string;
  name: string;
  agents: number;
}

export interface VoterUpload {
  id: string;
  fileName: string;
  unitLevel: UnitLevel;
  unitName: string;
  rowCount: number;
  uploadedAt: string;
  uploadedByName: string;
  status: 'PROCESSED' | 'FAILED';
  message: string | null;
}

export interface SentimentSummary {
  unitId: string;
  unitName: string;
  unitLevel: UnitLevel;
  positive: number;
  neutral: number;
  negative: number;
  notRecorded: number;
  total: number;
  /** Recorded against someone found living elsewhere; reported whether or not they are counted above. */
  nonResident: number;
}

export interface ConfidenceSummary {
  high: number;
  medium: number;
  low: number;
}

/** FR-U12 chart totals: households and the people counted in them, not roll entries. */
export interface HouseInsights {
  houses: number;
  people: number;
  residents: number;
  positive: number;
  neutral: number;
  negative: number;
  confidence: ConfidenceSummary;
}

/** A unit's house tallies on the admin pre-election dashboard. */
export interface HouseUnitSummary {
  unitId: string;
  unitName: string;
  unitLevel: UnitLevel;
  houses: number;
  people: number;
  residents: number;
  positive: number;
  neutral: number;
  negative: number;
}

/** One recorded house on the admin's unit drill-down. */
export interface HouseEntryRow {
  id: string;
  houseNo: string;
  houseName: string;
  wardNo: number | null;
  headcount: number;
  residentialCount: number;
  positiveCount: number;
  neutralCount: number;
  negativeCount: number;
  confidence: ConfidenceLevel;
  boothId: string;
  boothName: string;
  boothPath: string;
  candidateName: string;
  recordedByName: string;
  recordedAt: string;
  updatedAt: string;
}

export interface HouseReport {
  rows: HouseUnitSummary[];
  totals: HouseInsights;
}

export type DeletionStatus = 'PENDING' | 'APPROVED' | 'REJECTED';

/** An agent's request to have their account and recorded entries removed. */
export interface AccountDeletionRequest {
  id: string;
  userId: string | null;
  agentName: string;
  agentPhone: string;
  agentEmail: string | null;
  reason: string | null;
  status: DeletionStatus;
  requestedAt: string;
  reviewedAt: string | null;
  reviewedByName: string | null;
  reviewerNote: string | null;
  deletedEntries: number | null;
}

/** FR-A15: one candidate's standing, computed server-side so the rule lives in one place. */
export type Verdict = 'POSITIVE' | 'NEGATIVE' | 'NO_DATA';
export type BoothStatus = 'SAFE' | 'WATCH' | 'AT_RISK' | 'NO_DATA';
export type SentimentSource = 'ALL' | 'VOTER' | 'HOUSE';

export interface SentimentSplit {
  positive: number;
  neutral: number;
  negative: number;
  total: number;
  positivePercent: number;
  neutralPercent: number;
  negativePercent: number;
  netLead: number;
  verdict: Verdict;
}

export interface CandidateSentimentRow {
  unitId: string;
  unitName: string;
  unitLevel: UnitLevel;
  split: SentimentSplit;
  netLead: number;
  status: BoothStatus;
  drillable: boolean;
}

export interface CandidateSentiment {
  candidateId: string;
  candidateName: string;
  /** The panchayat the candidate contests, when one is mapped. */
  candidateUnitId: string | null;
  candidateUnitName: string | null;
  level: UnitLevel;
  parentUnitId: string | null;
  parentUnitPath: string | null;
  totals: SentimentSplit;
  rows: CandidateSentimentRow[];
}

export interface DashboardSummary {
  entriesRecorded: number;
  activeAgents: number;
  pendingAccessRequests: number;
  pendingChangeRequests: number;
  byUnit: SentimentSummary[];
  confidence: ConfidenceSummary;
}

export interface ReportFilter {
  level: UnitLevel;
  unitId?: string;
  candidateId?: string;
  sentiment?: SentimentValue | 'ALL';
  confidence?: ConfidenceLevel | 'ALL';
  from?: string;
  to?: string;
}

export interface Page<T> {
  content: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}
