import { request } from '@/lib/apiClient';
import type { ConfidenceSummary, SentimentSummary } from '@/shared/types';

export const agentInsightsApi = {
  /** `residentsOnly` leaves out the entries recorded against a voter living elsewhere. */
  booth: (params: { boothId?: string; candidateId?: string; residentsOnly?: boolean }) =>
    request<{ rows: SentimentSummary[]; confidence: ConfidenceSummary }>('/api/agent/insights', {
      query: {
        boothId: params.boothId,
        candidateId: params.candidateId,
        residentsOnly: params.residentsOnly ? 'true' : undefined,
      },
    }),
};
