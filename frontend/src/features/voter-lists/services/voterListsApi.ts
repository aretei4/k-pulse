import { request } from '@/lib/apiClient';
import type { Page, Voter, VoterChangePayload, VoterUpload } from '@/shared/types';

export interface BoothCleared {
  boothId: string;
  boothName: string;
  voters: number;
  sentimentEntries: number;
}

export interface VoterQuery {
  boothId?: string;
  search?: string;
  page?: number;
  size?: number;
}

export const voterListsApi = {
  list: (query: VoterQuery) =>
    request<Page<Voter>>('/api/admin/voters', {
      query: { boothId: query.boothId, search: query.search, page: query.page ?? 0, size: query.size ?? 25 },
    }),

  create: (payload: VoterChangePayload & { boothId: string }) =>
    request<Voter>('/api/admin/voters', { method: 'POST', body: payload }),

  update: (id: string, payload: VoterChangePayload) =>
    request<Voter>(`/api/admin/voters/${id}`, { method: 'PUT', body: payload }),

  remove: (id: string) => request<void>(`/api/admin/voters/${id}`, { method: 'DELETE' }),

  /** Deletes every voter in one booth, and the sentiment recorded against them. */
  clearBooth: (boothId: string) =>
    request<BoothCleared>(`/api/admin/voters/booth/${boothId}`, { method: 'DELETE' }),

  uploads: () => request<VoterUpload[]>('/api/admin/voters/uploads'),

  import: (file: File, unitId: string) => {
    const formData = new FormData();
    formData.append('file', file);
    formData.append('unitId', unitId);
    return request<VoterUpload>('/api/admin/voters/import', { method: 'POST', formData });
  },
};
