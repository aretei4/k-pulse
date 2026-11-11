import { request } from '@/lib/apiClient';
import type { AgentReport, AgentReportScope, ConfidenceLevel } from '@/shared/types';

export interface AgentReportQuery {
  panchayatId: string;
  candidateId?: string;
  /** A subset of the agent's approved booths; empty means all of them. */
  booth?: string[];
  confidence?: ConfidenceLevel[];
  ward?: number[];
  /** Caste names, with 'NONE' for voters who have none recorded. */
  caste?: string[];
  /** Days back; left out for the whole campaign. */
  days?: number;
}

/**
 * FR-U14. There is no unit parameter beyond a panchayat the agent already
 * holds an approval for — the server matches it against their own grants and
 * answers for those booths alone.
 */
export const agentReportsApi = {
  scope: () => request<AgentReportScope[]>('/api/agent/reports/scope'),

  /**
   * AG-9: the voters behind the drawn report. `sentiment` names the ticked
   * tiles, so the file holds exactly the people the charts counted.
   */
  voters: (query: AgentReportQuery & { sentiment: string[] }) =>
    request<Blob>('/api/agent/reports/voters.pdf', {
      blob: true,
      query: {
        panchayatId: query.panchayatId,
        candidateId: query.candidateId,
        booth: query.booth?.length ? query.booth.join(',') : undefined,
        sentiment: query.sentiment.join(','),
        confidence: query.confidence?.length ? query.confidence.join(',') : undefined,
        ward: query.ward?.length ? query.ward.join(',') : undefined,
        caste: query.caste?.length ? query.caste.join(',') : undefined,
        days: query.days,
      },
    }),

  report: (query: AgentReportQuery) =>
    request<AgentReport>('/api/agent/reports', {
      query: {
        panchayatId: query.panchayatId,
        candidateId: query.candidateId,
        // Repeated values go as one comma-joined parameter; Spring splits them.
        booth: query.booth?.length ? query.booth.join(',') : undefined,
        confidence: query.confidence?.length ? query.confidence.join(',') : undefined,
        ward: query.ward?.length ? query.ward.join(',') : undefined,
        caste: query.caste?.length ? query.caste.join(',') : undefined,
        days: query.days,
      },
    }),
};
