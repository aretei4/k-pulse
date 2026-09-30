import { request } from '@/lib/apiClient';
import type { AccessRequest, AccessRequestStatus } from '@/shared/types';

export const accessRequestsApi = {
  list: (status: AccessRequestStatus | 'ALL' = 'PENDING') =>
    request<AccessRequest[]>('/api/admin/access-requests', { query: { status } }),

  approve: (id: string, months: number, note?: string) =>
    request<AccessRequest>(`/api/admin/access-requests/${id}/approve`, { method: 'POST', body: { months, note } }),

  reject: (id: string, note?: string) =>
    request<AccessRequest>(`/api/admin/access-requests/${id}/reject`, { method: 'POST', body: { note } }),

  revoke: (id: string) => request<AccessRequest>(`/api/admin/access-requests/${id}/revoke`, { method: 'POST' }),
};
