import { request } from '@/lib/apiClient';
import type { AgentPasswordStatus } from '@/shared/types';

/** FA-24: the agent's own sign-in settings. Always about whoever is signed in. */
export const agentPasswordApi = {
  status: () => request<AgentPasswordStatus>('/api/agent/password'),

  /** Asks the admin for a new password; they set it and pass it on in person. */
  requestReset: () => request<AgentPasswordStatus>('/api/agent/password/reset-request', { method: 'POST' }),

  withdrawRequest: () => request<AgentPasswordStatus>('/api/agent/password/reset-request', { method: 'DELETE' }),

  change: (payload: { currentPassword?: string; newPassword: string }) =>
    request<AgentPasswordStatus>('/api/agent/password', { method: 'PUT', body: payload }),
};
