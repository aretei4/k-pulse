import { request } from '@/lib/apiClient';
import type { AccessRequest } from '@/shared/types';

export const agentAccessApi = {
  mine: () => request<AccessRequest[]>('/api/agent/access-requests'),

  /**
   * The unit is always a panchayat, and the candidate is a typed name: the
   * server matches it to a candidate ignoring case, adding one if the name is
   * new.
   */
  create: (payload: { unitId: string; candidateName: string }) =>
    request<AccessRequest>('/api/agent/access-requests', { method: 'POST', body: payload }),
};
