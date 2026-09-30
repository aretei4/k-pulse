/** One booth the signed-in agent may open, carrying the candidate its grant is tied to. */
export interface AgentBooth {
  boothId: string;
  boothName: string;
  path: string;
  candidateId: string;
  candidateName: string;
  accessRequestId: string;
  expiresAt: string | null;
}
