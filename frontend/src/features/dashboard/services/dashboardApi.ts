import { request } from '@/lib/apiClient';
import type { ConfidenceLevel, DashboardSummary, SentimentValue, UnitLevel } from '@/shared/types';

export interface DashboardFilters {
  level: UnitLevel;
  candidateId?: string;
  sentiment?: SentimentValue | 'ALL';
  confidence?: ConfidenceLevel | 'ALL';
  from?: string;
  to?: string;
}

export const dashboardApi = {
  summary: (filters: DashboardFilters) =>
    request<DashboardSummary>('/api/admin/dashboard', {
      query: {
        level: filters.level,
        candidateId: filters.candidateId,
        sentiment: filters.sentiment,
        confidence: filters.confidence,
        from: filters.from,
        to: filters.to,
      },
    }),
};
