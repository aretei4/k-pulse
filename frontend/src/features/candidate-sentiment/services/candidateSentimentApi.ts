import { request } from '@/lib/apiClient';
import type { Candidate, CandidateSentiment, SentimentSource, UnitLevel } from '@/shared/types';

export interface CandidateSentimentQuery {
  level?: UnitLevel;
  parentUnitId?: string;
  source?: SentimentSource;
  from?: string;
  to?: string;
}

/** FR-A15. Scope is applied server-side; nothing here widens what comes back. */
export const candidateSentimentApi = {
  /** Only candidates with sentiment recorded inside the admin's own area. */
  candidates: () => request<Candidate[]>('/api/admin/candidate-sentiment/candidates'),

  report: (candidateId: string, query: CandidateSentimentQuery = {}) =>
    request<CandidateSentiment>(`/api/admin/candidate-sentiment/${candidateId}`, {
      query: {
        level: query.level,
        parentUnitId: query.parentUnitId,
        source: query.source,
        from: query.from,
        to: query.to,
      },
    }),
};
