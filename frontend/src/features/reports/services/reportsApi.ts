import { request } from '@/lib/apiClient';
import type { ConfidenceSummary, ReportFilter, SentimentSummary } from '@/shared/types';

function toQuery(filter: ReportFilter) {
  return {
    level: filter.level,
    unitId: filter.unitId,
    candidateId: filter.candidateId,
    sentiment: filter.sentiment,
    confidence: filter.confidence,
    from: filter.from,
    to: filter.to,
  };
}

export const reportsApi = {
  summary: (filter: ReportFilter) =>
    request<{ rows: SentimentSummary[]; confidence: ConfidenceSummary }>('/api/admin/reports/summary', {
      query: toQuery(filter),
    }),

  export: (filter: ReportFilter, format: 'pdf' | 'excel') =>
    request<Blob>('/api/admin/reports/export', { query: { ...toQuery(filter), format }, blob: true }),
};
