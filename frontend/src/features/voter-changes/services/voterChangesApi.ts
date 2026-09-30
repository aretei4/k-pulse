import { request } from '@/lib/apiClient';
import type { VoterChangeRequest, VoterChangeStatus } from '@/shared/types';

export const voterChangesApi = {
  list: (status: VoterChangeStatus | 'ALL' = 'PENDING') =>
    request<VoterChangeRequest[]>('/api/admin/voter-changes', { query: { status } }),

  approve: (id: string, note?: string) =>
    request<VoterChangeRequest>(`/api/admin/voter-changes/${id}/approve`, { method: 'POST', body: { note } }),

  reject: (id: string, note?: string) =>
    request<VoterChangeRequest>(`/api/admin/voter-changes/${id}/reject`, { method: 'POST', body: { note } }),
};
