import { request } from '@/lib/apiClient';
import type { Unit, UnitLevel } from '@/shared/types';

export interface UnitPayload {
  level: UnitLevel;
  name: string;
  parentId: string | null;
}

export interface LevelCount {
  level: UnitLevel;
  created: number;
  updated: number;
}

/** What an upload did, or would do when it was only a preview. */
export interface LocationImportResult {
  preview: boolean;
  rowsRead: number;
  created: number;
  updated: number;
  rowsSkipped: number;
  /** Units already held that the file did not mention. Never touched. */
  notInFile: number;
  levels: LevelCount[];
  problems: string[];
}

export type MasterSheet = 'hierarchy' | 'booths';

/** Reading the tree is open to both route groups; editing it is admin only. */
export const locationsApi = {
  list: () => request<Unit[]>('/api/units'),

  create: (payload: UnitPayload) => request<Unit>('/api/admin/units', { method: 'POST', body: payload }),

  update: (id: string, payload: UnitPayload) =>
    request<Unit>(`/api/admin/units/${id}`, { method: 'PUT', body: payload }),

  remove: (id: string) => request<null>(`/api/admin/units/${id}`, { method: 'DELETE' }),

  /**
   * The master arrives as two sheets, and the order matters: the booth sheet
   * names no district, so its panchayats have to exist before it is loaded.
   * With `preview` the server reports what the file would do and changes nothing.
   */
  importMaster: (sheet: MasterSheet, file: File, preview: boolean) => {
    const formData = new FormData();
    formData.append('file', file);
    return request<LocationImportResult>(`/api/admin/locations/import/${sheet}?preview=${preview}`, {
      method: 'POST',
      formData,
    });
  },
};
