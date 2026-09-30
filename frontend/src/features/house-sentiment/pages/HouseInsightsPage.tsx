import { useMemo, useState } from 'react';
import {
  BoothSplitBar,
  Card,
  ChartCard,
  ConfidenceDonut,
  EmptyState,
  ErrorNote,
  Legend,
  SentimentDonut,
  Select,
  Spinner,
  StatCard,
} from '@/shared/components';
import { PageHeader } from '@/shared/layouts/PageHeader';
import { useAsync } from '@/shared/hooks/useAsync';
import { colors, fonts } from '@/shared/theme';
import { percent } from '@/shared/utils/format';
import { agentVoterApi } from '@/features/agent-voter-list/services/agentVoterApi';
import type { SentimentSummary } from '@/shared/types';
import { houseSentimentApi } from '../services/houseSentimentApi';

/**
 * Insights for a pre-election-only build: the same charts, drawn from house
 * tallies instead of the named-voter roll, which such a build never fills in.
 */
export function HouseInsightsPage() {
  const booths = useAsync(() => agentVoterApi.booths(), []);
  const [boothId, setBoothId] = useState('');

  const rows = useMemo(() => booths.data ?? [], [booths.data]);
  const activeBoothId = boothId || rows[0]?.boothId || '';
  const grant = rows.find((r) => r.boothId === activeBoothId);

  const insights = useAsync(
    () =>
      activeBoothId
        ? houseSentimentApi.insights({ boothId: activeBoothId, candidateId: grant?.candidateId })
        : Promise.resolve(null),
    [activeBoothId, grant?.candidateId],
  );

  const data = insights.data;

  // The charts take a roll summary; a house tally maps onto it cleanly once the
  // residents stand in for the total. There is no "not recorded" — nothing says
  // how many houses a booth holds.
  const summary: SentimentSummary | null = data
    ? {
        unitId: activeBoothId,
        unitName: grant?.boothName ?? 'Booth',
        unitLevel: 'BOOTH',
        positive: data.positive,
        neutral: data.neutral,
        negative: data.negative,
        notRecorded: 0,
        total: data.residents,
      }
    : null;

  return (
    <div style={{ padding: '16px 20px 0' }}>
      <PageHeader
        title={grant ? `${grant.boothName} insights` : 'Insights'}
        subtitle={
          grant ? `Candidate ${grant.candidateName} · houses you have recorded` : 'Houses recorded so far.'
        }
        size={18}
      />

      {booths.error && <ErrorNote message={booths.error} />}
      {insights.error && <ErrorNote message={insights.error} />}

      {booths.loading && rows.length === 0 ? (
        <Spinner />
      ) : rows.length === 0 ? (
        <EmptyState title="No approved booths yet" hint="Charts appear once an admin approves an access request." />
      ) : (
        <>
          <Select
            label="Booth"
            options={rows.map((r) => ({ value: r.boothId, label: `${r.boothName} · ${r.candidateName}` }))}
            value={activeBoothId}
            onChange={setBoothId}
          />

          {insights.loading && !data ? (
            <Spinner />
          ) : !data || data.houses === 0 ? (
            <EmptyState
              title="No houses recorded in this booth yet"
              hint="Record a few houses and the charts fill in."
            />
          ) : (
            <div style={{ display: 'flex', flexDirection: 'column', gap: 12, paddingBottom: 16 }}>
              <div style={{ display: 'flex', gap: 10 }}>
                <StatCard label="Houses" value={data.houses} hint={`${data.people.toLocaleString('en-IN')} people`} />
                <StatCard
                  label="Residents"
                  value={data.residents}
                  hint={`${percent(data.residents, data.people)} of the people counted`}
                />
              </div>

              <Card padding={12}>
                <p style={{ fontFamily: fonts.sans, fontSize: 11, color: colors.inkSoft, margin: '0 0 6px' }}>
                  Positive vs negative
                </p>
                <BoothSplitBar summary={summary!} />
                <Legend
                  items={[
                    { name: 'Positive', color: colors.positive },
                    { name: 'Negative', color: colors.negative },
                  ]}
                />
              </Card>

              <ChartCard title="Split">
                <SentimentDonut data={[summary!]} height={150} />
              </ChartCard>

              <ChartCard title="Confidence">
                <ConfidenceDonut confidence={data.confidence} height={150} />
              </ChartCard>
            </div>
          )}
        </>
      )}
    </div>
  );
}
