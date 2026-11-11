/** One booth the signed-in agent may open, carrying the candidate its grant is tied to. */
export interface AgentBooth {
  boothId: string;
  boothName: string;
  path: string;
  /** Its place in the tree, so the picker can step down one level at a time. */
  districtName: string | null;
  blockName: string | null;
  panchayatName: string | null;
  candidateId: string;
  candidateName: string;
  accessRequestId: string;
  expiresAt: string | null;
}
