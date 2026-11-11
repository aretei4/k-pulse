import { request } from '@/lib/apiClient';
import type {
  AgentAccessState,
  AreaCount,
  FieldAgent,
  FieldAgentPage,
  PaymentMode,
  UnitLevel,
} from '@/shared/types';

/** What the admin has narrowed the list to. Sent to the server on every call (FA-N2). */
export interface FieldAgentQuery {
  districtId?: string;
  blockId?: string;
  panchayatId?: string;
  access?: AgentAccessState;
  paid?: boolean;
  /** FA-25: who has the Reports page. */
  reports?: boolean;
  q?: string;
  sort?: 'NAME' | 'VOTERS';
  desc?: boolean;
  page?: number;
  size?: number;
}

export interface PaymentPayload {
  paid: boolean;
  amount?: number;
  paidOn?: string;
  mode?: PaymentMode;
  reference?: string;
}

export interface BulkPaymentPayload extends PaymentPayload {
  selection: 'IDS' | 'ALL_MATCHING';
  ids?: string[];
  /** Repeated for ALL_MATCHING: the server resolves the selection itself (FA-14). */
  districtId?: string;
  blockId?: string;
  panchayatId?: string;
  access?: AgentAccessState;
  paidFilter?: boolean;
  search?: string;
}

export interface BulkReportsPayload {
  selection: 'IDS' | 'ALL_MATCHING';
  ids?: string[];
  districtId?: string;
  blockId?: string;
  panchayatId?: string;
  access?: AgentAccessState;
  paidFilter?: boolean;
  reportsFilter?: boolean;
  search?: string;
  enabled: boolean;
}

export interface BulkPaymentResult {
  agents: number;
  paid: boolean;
  total: number | null;
}

/** `undefined` is dropped by the client, so an absent filter is simply absent. */
function query(filters: FieldAgentQuery) {
  return {
    districtId: filters.districtId,
    blockId: filters.blockId,
    panchayatId: filters.panchayatId,
    access: filters.access,
    paid: filters.paid,
    reports: filters.reports,
    q: filters.q,
    sort: filters.sort,
    desc: filters.desc ? true : undefined,
    page: filters.page,
    size: filters.size,
  };
}

export const fieldAgentsApi = {
  list: (filters: FieldAgentQuery) => request<FieldAgentPage>('/api/admin/field-agents', { query: query(filters) }),

  /** FA-3: one level of the geography at a time, each option with its agent count. */
  areas: (level: UnitLevel, parentId?: string) =>
    request<AreaCount[]>('/api/admin/field-agents/areas', { query: { level, parentId } }),

  one: (id: string) => request<FieldAgent>(`/api/admin/field-agents/${id}`),

  markPayment: (id: string, payload: PaymentPayload) =>
    request<FieldAgent>(`/api/admin/field-agents/${id}/payment`, { method: 'PUT', body: payload }),

  markMany: (payload: BulkPaymentPayload) =>
    request<BulkPaymentResult>('/api/admin/field-agents/payment/bulk', { method: 'POST', body: payload }),

  /** FA-19/FA-25: the password comes back once. Pass none to take the suggestion. */
  resetPassword: (id: string, password?: string) =>
    request<{ temporaryPassword: string; validDays: number }>(`/api/admin/field-agents/${id}/password-reset`, {
      method: 'POST',
      body: { password },
    }),

  /** FR-A22: gives or removes one agent's Reports page. */
  setReportsAccess: (id: string, enabled: boolean) =>
    request<FieldAgent>(`/api/admin/field-agents/${id}/reports-access`, { method: 'PUT', body: { enabled } }),

  setReportsAccessMany: (payload: BulkReportsPayload) =>
    request<{ agents: number; enabled: boolean }>('/api/admin/field-agents/reports-access/bulk', {
      method: 'POST',
      body: payload,
    }),

  /** Sets or corrects the referral; an empty string clears it. */
  updateReferral: (id: string, referralNo: string) =>
    request<FieldAgent>(`/api/admin/field-agents/${id}/referral`, { method: 'PUT', body: { referralNo } }),

  dismissPasswordRequest: (id: string) =>
    request<FieldAgent>(`/api/admin/field-agents/${id}/password-request`, { method: 'DELETE' }),

  exportCsv: (filters: FieldAgentQuery) =>
    request<Blob>('/api/admin/field-agents/export.csv', { query: query(filters), blob: true }),
};
