import { request } from '@/lib/apiClient';

/** The four chips, in the order the file name lists them. */
export const SENTIMENT_FILTERS = ['POSITIVE', 'NEUTRAL', 'NEGATIVE', 'NOT_RECORDED'] as const;

export type SentimentFilter = (typeof SENTIMENT_FILTERS)[number];

export interface ExportCounts {
  positive: number;
  neutral: number;
  negative: number;
  notRecorded: number;
}

export interface ExportPreviewRow {
  name: string;
  houseNo: string | null;
  sentiment: SentimentFilter;
}

/** What the Download page shows before anything is built. */
export interface VoterExportSummary {
  boothId: string;
  boothName: string;
  blockName: string | null;
  boothPath: string;
  candidateId: string;
  candidateName: string;
  totalVoters: number;
  counts: ExportCounts;
  matching: number;
  pages: number;
  preview: ExportPreviewRow[];
  fileName: string;
  /** Past this many matches the server refuses to build the file. */
  maxRows: number;
}

export const voterExportApi = {
  /**
   * Counts, the page estimate and the first three matches. Also the page's
   * access check: the server answers with the reason when a download is not
   * allowed, rather than leaving the phone to guess.
   */
  summary: (boothId: string, sentiment: SentimentFilter[]) =>
    request<VoterExportSummary>('/api/agent/voter-list/export/count', {
      query: { unitId: boothId, sentiment: sentiment.join(',') },
    }),

  pdf: (boothId: string, sentiment: SentimentFilter[]) =>
    request<Blob>('/api/agent/voter-list/export.pdf', {
      query: { unitId: boothId, sentiment: sentiment.join(',') },
      blob: true,
    }),
};
