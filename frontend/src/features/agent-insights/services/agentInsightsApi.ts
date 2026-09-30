import { request } from '@/lib/apiClient';
import type { ConfidenceSummary, SentimentSummary } from '@/shared/types';

export const agentInsightsApi = {
  booth: (params: { boothId?: string; candidateId?: string }) =>
    request<{ rows: SentimentSummary[]; confidence: ConfidenceSummary }>('/api/agent/insights', {
      query: { boothId: params.boothId, candidateId: params.candidateId },
    }),
};
