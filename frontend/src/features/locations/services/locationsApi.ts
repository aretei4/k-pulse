import { request } from '@/lib/apiClient';
import type { Unit, UnitLevel } from '@/shared/types';

export interface UnitPayload {
  level: UnitLevel;
  name: string;
  parentId: string | null;
}

/** Reading the tree is open to both route groups; editing it is admin only. */
export const locationsApi = {
  list: () => request<Unit[]>('/api/units'),

  create: (payload: UnitPayload) => request<Unit>('/api/admin/units', { method: 'POST', body: payload }),

  update: (id: string, payload: UnitPayload) =>
    request<Unit>(`/api/admin/units/${id}`, { method: 'PUT', body: payload }),

  remove: (id: string) => request<null>(`/api/admin/units/${id}`, { method: 'DELETE' }),
};
