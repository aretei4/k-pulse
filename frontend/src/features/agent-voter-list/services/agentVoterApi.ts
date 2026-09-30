import { request } from '@/lib/apiClient';
import type { Page, SentimentEntry, SentimentValue, Voter, VoterChangePayload, VoterChangeRequest, VoterChangeType } from '@/shared/types';
import type { AgentBooth } from '../types';

export interface AgentVoterQuery {
  boothId: string;
  search?: string;
  sentiment?: SentimentValue | 'ALL' | 'NOT_RECORDED';
  page?: number;
  size?: number;
}

export const agentVoterApi = {
  /** Booths unlocked by the agent's live grants — a panchayat grant expands to all its booths. */
  booths: () => request<AgentBooth[]>('/api/agent/booths'),

  voters: (query: AgentVoterQuery) =>
    request<Page<Voter>>('/api/agent/voters', {
      query: {
        boothId: query.boothId,
        search: query.search,
        sentiment: query.sentiment,
        page: query.page ?? 0,
        size: query.size ?? 50,
      },
    }),

  voter: (id: string) => request<{ voter: Voter; entry: SentimentEntry | null }>(`/api/agent/voters/${id}`),

  proposeChange: (payload: { type: VoterChangeType; voterId?: string | null; boothId: string; payload: VoterChangePayload }) =>
    request<VoterChangeRequest>('/api/agent/voter-changes', { method: 'POST', body: payload }),

  myChanges: () => request<VoterChangeRequest[]>('/api/agent/voter-changes'),
};
