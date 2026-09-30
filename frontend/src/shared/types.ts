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

export interface AuthUser {
  id: string;
  name: string;
  email: string | null;
  phone: string | null;
  role: Role;
  active: boolean;
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
