import { request } from '@/lib/apiClient';
import type { Candidate, Unit, UnitLevel } from '@/shared/types';

/** Candidates and the District > Block > Panchayat > Booth tree — used by both route groups. */
export const referenceApi = {
  candidates: () => request<Candidate[]>('/api/candidates'),

  units: (params: { level?: UnitLevel; parentId?: string } = {}) =>
    request<Unit[]>('/api/units', { query: { level: params.level, parentId: params.parentId } }),
};
