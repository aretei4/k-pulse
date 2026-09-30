import { request } from '@/lib/apiClient';
import type { ConfidenceLevel, SentimentEntry, SentimentValue } from '@/shared/types';

export interface RecordSentimentPayload {
  candidateId: string;
  sentiment: SentimentValue;
  confidence: ConfidenceLevel;
  resident: boolean;
  wardNo?: number;
}

export const sentimentApi = {
  /** Create or overwrite — FR-U10 puts no time window on editing an entry. */
  record: (voterId: string, payload: RecordSentimentPayload) =>
    request<SentimentEntry>(`/api/agent/voters/${voterId}/sentiment`, { method: 'POST', body: payload }),
};
