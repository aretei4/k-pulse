import { request } from '@/lib/apiClient';
import type { Candidate } from '@/shared/types';

export interface CandidatePayload {
  name: string;
  party: string | null;
  unitId: string | null;
}

/** Reading candidates is open to both route groups; editing them is admin only. */
export const candidatesApi = {
  list: () => request<Candidate[]>('/api/candidates'),

  create: (payload: CandidatePayload) =>
    request<Candidate>('/api/admin/candidates', { method: 'POST', body: payload }),

  update: (id: string, payload: CandidatePayload) =>
    request<Candidate>(`/api/admin/candidates/${id}`, { method: 'PUT', body: payload }),

  remove: (id: string) => request<null>(`/api/admin/candidates/${id}`, { method: 'DELETE' }),
};
