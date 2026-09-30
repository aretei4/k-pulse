import { request } from '@/lib/apiClient';
import type {
  ConfidenceLevel,
  HouseEntryRow,
  HouseInsights,
  HouseReport,
  HouseSentimentEntry,
  UnitLevel,
} from '@/shared/types';

export interface HouseReportFilter {
  level: UnitLevel;
  unitId?: string;
  candidateId?: string;
  confidence?: ConfidenceLevel | 'ALL';
  from?: string;
  to?: string;
}

function toQuery(filter: HouseReportFilter) {
  return {
    level: filter.level,
    unitId: filter.unitId,
    candidateId: filter.candidateId,
    confidence: filter.confidence,
    from: filter.from,
    to: filter.to,
  };
}

export interface HouseSentimentPayload {
  boothId: string;
  candidateId: string;
  houseNo: string;
  houseName: string;
  wardNo: number | null;
  headcount: number;
  residentialCount: number;
  positiveCount: number;
  neutralCount: number;
  negativeCount: number;
  confidence: ConfidenceLevel;
}

/** FR-U12. Same booth grant as the named-voter screens; a separate store on the server. */
export const houseSentimentApi = {
  list: (boothId: string, candidateId: string) =>
    request<HouseSentimentEntry[]>('/api/agent/houses', { query: { boothId, candidateId } }),

  /** Totals for the pre-election charts; its own endpoint, not the named-voter one. */
  insights: (params: { boothId?: string; candidateId?: string }) =>
    request<HouseInsights>('/api/agent/houses/insights', {
      query: { boothId: params.boothId, candidateId: params.candidateId },
    }),

  record: (payload: HouseSentimentPayload) =>
    request<HouseSentimentEntry>('/api/agent/houses', { method: 'POST', body: payload }),

  remove: (id: string) => request<null>(`/api/agent/houses/${id}`, { method: 'DELETE' }),

  /** Admin: house tallies rolled up to the chosen level. */
  adminSummary: (filter: HouseReportFilter) =>
    request<HouseReport>('/api/admin/house-sentiment/summary', { query: toQuery(filter) }),

  /** The houses behind one row of the report. */
  adminHouses: (unitId: string, filter: Omit<HouseReportFilter, 'level' | 'unitId'>) =>
    request<HouseEntryRow[]>('/api/admin/house-sentiment/houses', {
      query: {
        unitId,
        candidateId: filter.candidateId,
        confidence: filter.confidence,
        from: filter.from,
        to: filter.to,
      },
    }),

  adminHousesExport: (unitId: string, filter: Omit<HouseReportFilter, 'level' | 'unitId'>, format: 'pdf' | 'excel') =>
    request<Blob>('/api/admin/house-sentiment/houses/export', {
      query: {
        unitId,
        candidateId: filter.candidateId,
        confidence: filter.confidence,
        from: filter.from,
        to: filter.to,
        format,
      },
      blob: true,
    }),

  adminExport: (filter: HouseReportFilter, format: 'pdf' | 'excel') =>
    request<Blob>('/api/admin/house-sentiment/export', { query: { ...toQuery(filter), format }, blob: true }),
};
