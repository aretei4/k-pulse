import { useState } from 'react';
import { Link, useNavigate, useSearchParams } from 'react-router-dom';
import { ChevronLeft, Edit3, Search, Trash2, UserPlus } from 'lucide-react';
import {
  Button,
  EmptyState,
  ErrorNote,
  SearchBox,
  Select,
  SentimentPill,
  Spinner,
} from '@/shared/components';
import { PageHeader } from '@/shared/layouts/PageHeader';
import { useAsync } from '@/shared/hooks/useAsync';
import { useDebounced } from '@/shared/hooks/useDebounced';
import { colors, fonts } from '@/shared/theme';
import type { SentimentValue } from '@/shared/types';
import { agentVoterApi } from '../services/agentVoterApi';

const sentimentOptions = [
  { value: 'ALL', label: 'All voters' },
  { value: 'NOT_RECORDED', label: 'Not recorded' },
  { value: 'POSITIVE', label: 'Positive' },
  { value: 'NEUTRAL', label: 'Neutral' },
  { value: 'NEGATIVE', label: 'Negative' },
];

export function VoterListPage() {
  const navigate = useNavigate();
  const [params] = useSearchParams();
  const boothId = params.get('boothId') ?? '';
  const candidateId = params.get('candidateId') ?? '';

  const [search, setSearch] = useState('');
  const [filter, setFilter] = useState<SentimentValue | 'ALL' | 'NOT_RECORDED'>('ALL');
  const debouncedSearch = useDebounced(search);

  const booths = useAsync(() => agentVoterApi.booths(), []);
  const voters = useAsync(
    () =>
      boothId
        ? agentVoterApi.voters({ boothId, search: debouncedSearch || undefined, sentiment: filter, size: 200 })
        : Promise.resolve(null),
    [boothId, debouncedSearch, filter],
  );

  const grant = (booths.data ?? []).find((b) => b.boothId === boothId && b.candidateId === candidateId);
  const rows = voters.data?.content ?? [];

  if (!boothId || !candidateId) {
    return (
      <div style={{ padding: '16px 20px 0' }}>
        <EmptyState
          title="Pick a booth first"
          action={
            <Link to="/agent/voters" style={{ textDecoration: 'none' }}>
              <Button small>Select unit</Button>
            </Link>
          }
        />
      </div>
    );
  }

  const changeLink = (type: 'ADD' | 'EDIT' | 'DELETE', voterId?: string) =>
    `/agent/voters/change?boothId=${boothId}&type=${type}${voterId ? `&voterId=${voterId}` : ''}`;

  return (
    <div style={{ padding: '12px 20px 0' }}>
      <button
        onClick={() => navigate('/agent/voters')}
        style={{
          display: 'flex',
          alignItems: 'center',
          gap: 4,
          background: 'none',
          border: 'none',
          cursor: 'pointer',
          padding: 0,
          marginBottom: 8,
          fontFamily: fonts.sans,
          fontSize: 12,
          color: colors.inkSoft,
        }}
      >
        <ChevronLeft size={14} /> Change unit
      </button>

      <PageHeader
        title={grant?.boothName ?? 'Voter list'}
        subtitle={
          voters.data
            ? `${voters.data.totalElements.toLocaleString('en-IN')} voters · candidate ${grant?.candidateName ?? ''}`
            : grant?.path
        }
        size={18}
        action={
          <Link to={changeLink('ADD')} style={{ textDecoration: 'none' }}>
            <Button tone="accent" small icon={UserPlus}>
              Add
            </Button>
          </Link>
        }
      />

      <Select
        options={sentimentOptions}
        value={filter}
        onChange={(v) => setFilter(v as SentimentValue | 'ALL' | 'NOT_RECORDED')}
      />

      <div style={{ marginBottom: 12 }}>
        <SearchBox value={search} onChange={setSearch} placeholder="Search by name or house no." icon={Search} />
      </div>

      {voters.error && <ErrorNote message={voters.error} />}

      {voters.loading && rows.length === 0 ? (
        <Spinner />
      ) : rows.length === 0 ? (
        <EmptyState title="No voters match" hint="Clear the filter or try another spelling." />
      ) : (
        <div style={{ paddingBottom: 16 }}>
          {rows.map((voter, i) => (
            <div
              key={voter.id}
              style={{
                display: 'flex',
                justifyContent: 'space-between',
                alignItems: 'center',
                gap: 8,
                padding: '10px 0',
                borderBottom: i < rows.length - 1 ? `1px solid ${colors.line}` : 'none',
              }}
            >
              <button
                onClick={() => navigate(`/agent/voters/${voter.id}/sentiment?candidateId=${candidateId}`)}
                style={{ background: 'none', border: 'none', cursor: 'pointer', textAlign: 'left', padding: 0, flex: 1, minWidth: 0 }}
              >
                <p style={{ fontFamily: fonts.sans, fontSize: 13, color: colors.ink, margin: 0 }}>{voter.name}</p>
                <p style={{ fontFamily: fonts.sans, fontSize: 10, color: colors.muted, margin: '2px 0 0' }}>
                  {voter.relation} · {voter.age} · {voter.gender} ·{' '}
                  {voter.houseNo ? `House ${voter.houseNo}` : 'No house no.'}
                </p>
              </button>

              <div style={{ display: 'flex', alignItems: 'center', gap: 8, flexShrink: 0 }}>
                <SentimentPill value={voter.sentiment} />
                <Link to={changeLink('EDIT', voter.id)} aria-label={`Propose an edit for ${voter.name}`}>
                  <Edit3 size={13} color={colors.inkSoft} />
                </Link>
                <Link to={changeLink('DELETE', voter.id)} aria-label={`Propose deleting ${voter.name}`}>
                  <Trash2 size={13} color={colors.negative} />
                </Link>
              </div>
            </div>
          ))}
        </div>
      )}
    </div>
  );
}
