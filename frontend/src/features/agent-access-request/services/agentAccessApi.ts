import { request } from '@/lib/apiClient';
import type { AccessRequest } from '@/shared/types';

export const agentAccessApi = {
  mine: () => request<AccessRequest[]>('/api/agent/access-requests'),

  create: (payload: { unitId: string; candidateId: string }) =>
    request<AccessRequest>('/api/agent/access-requests', { method: 'POST', body: payload }),
};
