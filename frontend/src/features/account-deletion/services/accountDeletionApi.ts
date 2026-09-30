import { request } from '@/lib/apiClient';
import type { AccountDeletionRequest, DeletionStatus } from '@/shared/types';

/**
 * Account deletion. Filing is open to anyone (the public page Google Play
 * requires); approving is admin-only and is what actually deletes the account.
 */
export const accountDeletionApi = {
  /** Public page: no sign-in, answers the same way whether or not the number is registered. */
  requestPublic: (payload: { phone: string; reason?: string }) =>
    request<null>('/api/auth/account-deletion', { method: 'POST', body: payload }),

  /** In-app, for an agent who is already signed in. */
  requestMine: (note?: string) =>
    request<AccountDeletionRequest>('/api/agent/account-deletion', { method: 'POST', body: { note } }),

  list: (status?: DeletionStatus) =>
    request<AccountDeletionRequest[]>('/api/admin/account-deletions', { query: { status } }),

  approve: (id: string, note?: string) =>
    request<AccountDeletionRequest>(`/api/admin/account-deletions/${id}/approve`, {
      method: 'POST',
      body: { note },
    }),

  reject: (id: string, note?: string) =>
    request<AccountDeletionRequest>(`/api/admin/account-deletions/${id}/reject`, {
      method: 'POST',
      body: { note },
    }),
};
