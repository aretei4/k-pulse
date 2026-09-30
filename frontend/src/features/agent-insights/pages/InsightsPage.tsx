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
import { agentInsightsApi } from '../services/agentInsightsApi';

export function InsightsPage() {
  const booths = useAsync(() => agentVoterApi.booths(), []);
  const [boothId, setBoothId] = useState('');

  const rows = useMemo(() => booths.data ?? [], [booths.data]);
  const activeBoothId = boothId || rows[0]?.boothId || '';
  const grant = rows.find((r) => r.boothId === activeBoothId);

  const insights = useAsync(
    () =>
      activeBoothId
        ? agentInsightsApi.booth({ boothId: activeBoothId, candidateId: grant?.candidateId })
        : Promise.resolve(null),
    [activeBoothId, grant?.candidateId],
  );

  const summary = insights.data?.rows[0];
  const recorded = summary ? summary.positive + summary.neutral + summary.negative : 0;

  return (
    <div style={{ padding: '16px 20px 0' }}>
      <PageHeader
        title={grant ? `${grant.boothName} insights` : 'Insights'}
        subtitle={grant ? `Candidate ${grant.candidateName} · your recorded entries` : 'Your recorded entries so far.'}
        size={18}
      />

      {booths.error && <ErrorNote message={booths.error} />}

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

          {insights.loading && !summary ? (
            <Spinner />
          ) : !summary || recorded === 0 ? (
            <EmptyState title="Nothing recorded in this booth yet" hint="Record a few voters and the charts fill in." />
          ) : (
            <div style={{ display: 'flex', flexDirection: 'column', gap: 12, paddingBottom: 16 }}>
              <div style={{ display: 'flex', gap: 10 }}>
                <StatCard label="Recorded" value={recorded} hint={`${percent(recorded, summary.total)} of the roll`} />
                <StatCard label="Left to do" value={summary.notRecorded} />
              </div>

              <Card padding={12}>
                <p style={{ fontFamily: fonts.sans, fontSize: 11, color: colors.inkSoft, margin: '0 0 6px' }}>
                  Positive vs negative
                </p>
                <BoothSplitBar summary={summary} />
                <Legend
                  items={[
                    { name: 'Positive', color: colors.positive },
                    { name: 'Negative', color: colors.negative },
                  ]}
                />
              </Card>

              <ChartCard title="Split">
                <SentimentDonut data={[summary]} height={150} />
              </ChartCard>

              <ChartCard title="Confidence">
                <ConfidenceDonut confidence={insights.data!.confidence} height={150} />
              </ChartCard>
            </div>
          )}
        </>
      )}
    </div>
  );
}
