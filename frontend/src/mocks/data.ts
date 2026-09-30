import type {
  AccessRequest,
  AuthUser,
  Candidate,
  ConfidenceLevel,
  AccountDeletionRequest,
  HouseSentimentEntry,
  SentimentEntry,
  SentimentValue,
  Unit,
  Voter,
  VoterChangeRequest,
  VoterUpload,
} from '@/shared/types';

/* ------------------------------------------------------------------ *
 * Seed dataset: Bhadrak district, Odisha — the S18-98 sample region.
 * Deterministic on purpose, so demos and screenshots stay stable.
 * ------------------------------------------------------------------ */

let seed = 20260909;
function rand() {
  // Mulberry32 — small, deterministic, good enough for demo data.
  seed |= 0;
  seed = (seed + 0x6d2b79f5) | 0;
  let t = Math.imul(seed ^ (seed >>> 15), 1 | seed);
  t = (t + Math.imul(t ^ (t >>> 7), 61 | t)) ^ t;
  return ((t ^ (t >>> 14)) >>> 0) / 4294967296;
}
function pick<T>(items: readonly T[]): T {
  return items[Math.floor(rand() * items.length)];
}
function int(min: number, max: number) {
  return min + Math.floor(rand() * (max - min + 1));
}

export const candidates: Candidate[] = [
  { id: 'cand-1', name: 'R. Mohanty', party: 'Independent', unitId: null, unitName: null },
  { id: 'cand-2', name: 'K. Pradhan', party: 'Independent', unitId: null, unitName: null },
];

interface BoothSpec {
  name: string;
  voters: number;
}
interface PanchayatSpec {
  name: string;
  booths: BoothSpec[];
}
interface BlockSpec {
  name: string;
  panchayats: PanchayatSpec[];
}

const districtName = 'Bhadrak';

const blockSpecs: BlockSpec[] = [
  {
    name: 'Tihidi',
    panchayats: [
      { name: 'Kansabansa', booths: [{ name: 'Booth 12', voters: 178 }, { name: 'Booth 13', voters: 142 }] },
      { name: 'Erein', booths: [{ name: 'Booth 27', voters: 164 }] },
    ],
  },
  {
    name: 'Chandbali',
    panchayats: [
      { name: 'Gadiali', booths: [{ name: 'Booth 42', voters: 156 }] },
      { name: 'Barikpur', booths: [{ name: 'Booth 58', voters: 133 }] },
    ],
  },
  {
    name: 'Basudevpur',
    panchayats: [{ name: 'Eram', booths: [{ name: 'Booth 71', voters: 121 }] }],
  },
];

export const units: Unit[] = [];

const district: Unit = {
  id: 'dist-1',
  level: 'DISTRICT',
  name: districtName,
  path: districtName,
  parentId: null,
};
units.push(district);

blockSpecs.forEach((block, bi) => {
  const blockUnit: Unit = {
    id: `block-${bi + 1}`,
    level: 'BLOCK',
    name: block.name,
    path: `${districtName} > ${block.name}`,
    parentId: district.id,
  };
  units.push(blockUnit);
  block.panchayats.forEach((pan, pi) => {
    const panUnit: Unit = {
      id: `pan-${bi + 1}-${pi + 1}`,
      level: 'PANCHAYAT',
      name: pan.name,
      path: `${blockUnit.path} > ${pan.name}`,
      parentId: blockUnit.id,
    };
    units.push(panUnit);
    pan.booths.forEach((booth, oi) => {
      units.push({
        id: `booth-${bi + 1}-${pi + 1}-${oi + 1}`,
        level: 'BOOTH',
        name: booth.name,
        path: `${panUnit.path} > ${booth.name}`,
        parentId: panUnit.id,
      });
    });
  });
});

export const booths = units.filter((u) => u.level === 'BOOTH');

const boothSizes = new Map<string, number>();
blockSpecs.forEach((block, bi) =>
  block.panchayats.forEach((pan, pi) =>
    pan.booths.forEach((booth, oi) => boothSizes.set(`booth-${bi + 1}-${pi + 1}-${oi + 1}`, booth.voters)),
  ),
);

const maleFirst = [
  'Bijay Kumar',
  'Ranjit Kumar',
  'Sanjay',
  'Prakash',
  'Dillip',
  'Sushant',
  'Kunja Bihari',
  'Ashok',
  'Debendra',
  'Niranjan',
];
const femaleFirst = [
  'Sabitri',
  'Manju Rani',
  'Sasmita',
  'Pramila',
  'Anita',
  'Basanti',
  'Jyotsna',
  'Kabita',
  'Sunita',
  'Rashmita',
];
const surnames = ['Behera', 'Nayak', 'Swain', 'Jena', 'Sahoo', 'Das', 'Patra', 'Mohanty', 'Rout', 'Sethi', 'Pradhan', 'Barik'];
const relationHeads = ['Sridhar', 'Ranjit', 'Gopal', 'Bhagaban', 'Dasarathi', 'Narayan', 'Trilochan', 'Padmalochan'];

export const voters: Voter[] = [];
let voterSeq = 0;

booths.forEach((booth) => {
  const rollSize = boothSizes.get(booth.id) ?? 120;
  // Keep the demo dataset light — a representative slice of each booth roll.
  const generated = Math.min(rollSize, 40);
  for (let i = 0; i < generated; i += 1) {
    voterSeq += 1;
    const female = rand() > 0.5;
    const surname = pick(surnames);
    const name = `${female ? pick(femaleFirst) : pick(maleFirst)} ${surname}`;
    const relationPrefix = female ? (rand() > 0.5 ? 'W/O' : 'D/O') : 'S/O';
    voters.push({
      id: `voter-${voterSeq}`,
      epicNo: `ODA${String(1000000 + voterSeq).slice(1)}`,
      name,
      relation: `${relationPrefix} ${pick(relationHeads)} ${surname}`,
      houseNo: String(int(1, 220)),
      age: int(18, 82),
      gender: female ? 'F' : 'M',
      boothId: booth.id,
      boothName: booth.name,
      wardNo: int(1, 20),
      sentiment: null,
      confidence: null,
    });
  }
});

export const users: AuthUser[] = [
  // The bootstrap account is the super admin, as the migration makes it (FR-A13).
  { id: 'admin-1', name: 'Constituency Admin', email: 'admin@k-pulse.in', phone: '9861000001', role: 'SUPER_ADMIN', active: true },
  {
    id: 'admin-2',
    name: 'Tihidi Block Admin',
    email: 'tihidi@k-pulse.in',
    phone: '9861000002',
    role: 'ADMIN',
    active: true,
    scopeLevel: 'BLOCK',
    scopeUnitId: 'blk-1-1',
    scopeUnitName: 'Tihidi',
  },
  { id: 'agent-1', name: 'Prakash Sahoo', email: 'prakash@example.com', phone: '9861000011', role: 'FIELD_AGENT', active: true },
  { id: 'agent-2', name: 'Anita Das', email: 'anita@example.com', phone: '9861000012', role: 'FIELD_AGENT', active: true },
  { id: 'agent-3', name: 'Suresh Patra', email: 'suresh@example.com', phone: '9861000013', role: 'FIELD_AGENT', active: true },
  { id: 'agent-4', name: 'Manoj Barik', email: 'manoj@example.com', phone: '9861000014', role: 'FIELD_AGENT', active: false },
];

export function unitById(id: string): Unit | undefined {
  return units.find((u) => u.id === id);
}

const NOW = '2026-09-09T09:30:00+05:30';

function iso(daysAgo: number) {
  const d = new Date(NOW);
  d.setDate(d.getDate() - daysAgo);
  return d.toISOString();
}

function plusMonths(months: number) {
  const d = new Date(NOW);
  d.setMonth(d.getMonth() + months);
  return d.toISOString();
}

function makeAccessRequest(
  id: string,
  agentId: string,
  unitId: string,
  candidateId: string,
  status: AccessRequest['status'],
  requestedDaysAgo: number,
): AccessRequest {
  const unit = unitById(unitId)!;
  const agent = users.find((u) => u.id === agentId)!;
  const candidate = candidates.find((c) => c.id === candidateId)!;
  return {
    id,
    agentId,
    agentName: agent.name,
    unitId,
    unitLevel: unit.level,
    unitName: unit.name,
    unitPath: unit.path,
    candidateId,
    candidateName: candidate.name,
    status,
    requestedAt: iso(requestedDaysAgo),
    decidedAt: status === 'PENDING' ? null : iso(requestedDaysAgo - 1),
    expiresAt: status === 'APPROVED' ? plusMonths(6) : null,
    reviewerNote: null,
  };
}

export const accessRequests: AccessRequest[] = [
  makeAccessRequest('acc-1', 'agent-1', booths[0].id, 'cand-1', 'APPROVED', 12),
  makeAccessRequest('acc-2', 'agent-1', booths[2].id, 'cand-1', 'PENDING', 5),
  makeAccessRequest('acc-3', 'agent-2', 'pan-2-1', 'cand-1', 'PENDING', 4),
  makeAccessRequest('acc-4', 'agent-3', 'block-3', 'cand-2', 'PENDING', 3),
  makeAccessRequest('acc-5', 'agent-2', booths[1].id, 'cand-2', 'APPROVED', 20),
];

export const voterChangeRequests: VoterChangeRequest[] = [
  {
    id: 'vcr-1',
    type: 'EDIT',
    status: 'PENDING',
    agentId: 'agent-1',
    agentName: 'Prakash Sahoo',
    voterId: voters[1].id,
    voterName: voters[1].name,
    boothId: voters[1].boothId,
    unitPath: unitById(voters[1].boothId)!.path,
    payload: {
      name: voters[1].name,
      relation: voters[1].relation,
      age: voters[1].age + 1,
      gender: voters[1].gender,
      wardNo: 4,
      reason: 'Age corrected against the EPIC card',
    },
    proposedAt: iso(3),
    decidedAt: null,
    reviewerNote: null,
  },
  {
    id: 'vcr-2',
    type: 'ADD',
    status: 'PENDING',
    agentId: 'agent-2',
    agentName: 'Anita Das',
    voterId: null,
    voterName: null,
    boothId: booths[1].id,
    unitPath: booths[1].path,
    payload: {
      epicNo: 'ODA9911234',
      name: 'Kunja Bihari Sethi',
      relation: 'S/O Dasarathi Sethi',
      age: 34,
      gender: 'M',
      houseNo: '87',
      wardNo: 9,
      reason: 'New voter shifted into ward 9',
    },
    proposedAt: iso(2),
    decidedAt: null,
    reviewerNote: null,
  },
  {
    id: 'vcr-3',
    type: 'DELETE',
    status: 'PENDING',
    agentId: 'agent-1',
    agentName: 'Prakash Sahoo',
    voterId: voters[2].id,
    voterName: voters[2].name,
    boothId: voters[2].boothId,
    unitPath: unitById(voters[2].boothId)!.path,
    payload: { reason: 'Voter deceased — confirmed with the family' },
    proposedAt: iso(2),
    decidedAt: null,
    reviewerNote: null,
  },
];

export const voterUploads: VoterUpload[] = [
  {
    id: 'up-1',
    fileName: 'tihidi_booth_12.xlsx',
    unitLevel: 'BOOTH',
    unitName: 'Booth 12',
    rowCount: 842,
    uploadedAt: iso(12),
    uploadedByName: 'Constituency Admin',
    status: 'PROCESSED',
    message: null,
  },
  {
    id: 'up-2',
    fileName: 'chandbali_panchayat.xlsx',
    unitLevel: 'PANCHAYAT',
    unitName: 'Gadiali',
    rowCount: 6210,
    uploadedAt: iso(10),
    uploadedByName: 'Constituency Admin',
    status: 'PROCESSED',
    message: null,
  },
];

/* Pre-record sentiment for a slice of every booth, so no chart starts empty. */
export const sentimentEntries: SentimentEntry[] = [];
const confidenceValues: ConfidenceLevel[] = ['HIGH', 'MEDIUM', 'LOW'];

let entrySeq = 0;
booths.forEach((booth, index) => {
  const boothVoters = voters.filter((v) => v.boothId === booth.id);
  // Coverage and lean differ per booth — that variance is the story the charts tell.
  const coverage = [0.75, 0.6, 0.5, 0.65, 0.4, 0.55][index % 6];
  const positiveBias = [0.52, 0.4, 0.6, 0.3, 0.46, 0.5][index % 6];
  boothVoters.slice(0, Math.floor(boothVoters.length * coverage)).forEach((voter) => {
    const roll = rand();
    const sentiment: SentimentValue =
      roll < positiveBias ? 'POSITIVE' : roll < positiveBias + 0.12 ? 'NEUTRAL' : 'NEGATIVE';
    const confidence = pick(confidenceValues);
    voter.sentiment = sentiment;
    voter.confidence = confidence;
    entrySeq += 1;
    sentimentEntries.push({
      id: `se-${entrySeq}`,
      voterId: voter.id,
      candidateId: 'cand-1',
      sentiment,
      confidence,
      resident: rand() > 0.12,
      wardNo: voter.wardNo,
      recordedById: 'agent-1',
      recordedByName: 'Prakash Sahoo',
      recordedAt: iso(int(1, 20)),
      updatedAt: iso(int(0, 1)),
    });
  });
});

/**
 * FR-U12 house tallies — a handful per booth, enough for the demo list to look
 * lived-in. Deliberately unrelated to any voter row, as on the server.
 */
export const houseSentimentEntries: HouseSentimentEntry[] = [];
const houseNames = [
  'Bijay Kumar Behera',
  'Ranjit Nayak',
  'Gopal Swain',
  'Sarojini Jena',
  'Prafulla Mohanty',
];

let houseSeq = 0;
booths.forEach((booth) => {
  for (let i = 0; i < 3; i++) {
    const headcount = int(3, 6);
    // The breakdown covers the residents only, as the form requires.
    const residentialCount = int(1, headcount);
    const positiveCount = int(0, residentialCount);
    const neutralCount = int(0, residentialCount - positiveCount);
    houseSeq += 1;
    houseSentimentEntries.push({
      id: `hse-${houseSeq}`,
      boothId: booth.id,
      candidateId: 'cand-1',
      houseNo: String(40 + i * int(1, 3)),
      houseName: pick(houseNames),
      wardNo: int(1, 20),
      headcount,
      residentialCount,
      positiveCount,
      neutralCount,
      negativeCount: residentialCount - positiveCount - neutralCount,
      confidence: pick(confidenceValues),
      recordedById: 'agent-1',
      recordedByName: 'Prakash Sahoo',
      recordedAt: iso(int(1, 20)),
      updatedAt: iso(int(0, 1)),
    });
  }
});

/** Deletion requests start empty; the demo files them as you click. */
export const accountDeletionRequests: AccountDeletionRequest[] = [];

export function nextId(prefix: string) {
  return `${prefix}-${Math.random().toString(36).slice(2, 9)}`;
}

export function nowIso() {
  return new Date().toISOString();
}

export function addMonths(months: number) {
  const d = new Date();
  d.setMonth(d.getMonth() + months);
  return d.toISOString();
}
