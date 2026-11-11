import { request } from '@/lib/apiClient';
import type { AccountUser, AuthUser, Role, UnitLevel } from '@/shared/types';

export interface CreateAdminPayload {
  name: string;
  email: string;
  phone?: string;
  password: string;
  superAdmin: boolean;
  scopeLevel?: UnitLevel;
  scopeUnitId?: string;
}

export const usersApi = {
  list: (role: Role | 'ALL' = 'FIELD_AGENT') => request<AuthUser[]>('/api/admin/users', { query: { role } }),

  create: (payload: { name: string; email: string; phone: string; referralNo?: string }) =>
    request<AuthUser>('/api/admin/users', { method: 'POST', body: { ...payload, role: 'FIELD_AGENT' } }),

  /** Admin + super-admin accounts; the server refuses this to anyone but a super admin. */
  admins: () => request<AccountUser[]>('/api/admin/users', { query: { role: 'ALL' } }),

  createAdmin: (payload: CreateAdminPayload) =>
    request<AccountUser>('/api/admin/users/admins', { method: 'POST', body: payload }),

  updateScope: (id: string, scopeLevel: UnitLevel, scopeUnitId: string) =>
    request<AccountUser>(`/api/admin/users/admins/${id}/scope`, {
      method: 'PATCH',
      body: { scopeLevel, scopeUnitId },
    }),

  setActive: (id: string, active: boolean) =>
    request<AuthUser>(`/api/admin/users/${id}/status`, { method: 'PATCH', body: { active } }),

  /** FR-U13 / DL-13. Off for a new agent; a scoped admin may only reach their own. */
  setDownloadEnabled: (id: string, enabled: boolean) =>
    request<AuthUser>(`/api/admin/agents/${id}/download-enabled`, { method: 'PUT', body: { enabled } }),
};
