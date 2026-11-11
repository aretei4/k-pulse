import { useMemo, useState } from 'react';
import { Link } from 'react-router-dom';
import { FileDown } from 'lucide-react';
import {
  BoothSplitBar,
  Card,
  ChartCard,
  ConfidenceDonut,
  EmptyState,
  ErrorNote,
  Legend,
  SegmentedControl,
  SentimentDonut,
  Select,
  Spinner,
  StatCard,
  type Segment,
} from '@/shared/components';
import { PageHeader } from '@/shared/layouts/PageHeader';
import { useAsync } from '@/shared/hooks/useAsync';
import { useAuth } from '@/shared/hooks/useAuth';
import { colors, fonts } from '@/shared/theme';
import { percent } from '@/shared/utils/format';
import { agentVoterApi } from '@/features/agent-voter-list/services/agentVoterApi';
import { agentInsightsApi } from '../services/agentInsightsApi';

/**
 * Whether a voter the agent found living elsewhere still counts. The entry
 * screen records that as "No residence"; here it is a choice, because a booth
 * with many such voters reads very differently with them in or out.
 */
type Counting = 'EVERYONE' | 'RESIDENTS';

const countingSegments: Segment<Counting>[] = [
  { value: 'EVERYONE', label: 'Everyone recorded' },
  { value: 'RESIDENTS', label: 'Residence only' },
];

export function InsightsPage() {
  const { user } = useAuth();
  const booths = useAsync(() => agentVoterApi.booths(), []);
  const [boothId, setBoothId] = useState('');
  const [counting, setCounting] = useState<Counting>('EVERYONE');
  const residentsOnly = counting === 'RESIDENTS';

  const rows = useMemo(() => booths.data ?? [], [booths.data]);
  const activeBoothId = boothId || rows[0]?.boothId || '';
  const grant = rows.find((r) => r.boothId === activeBoothId);

  const insights = useAsync(
    () =>
      activeBoothId
        ? agentInsightsApi.booth({ boothId: activeBoothId, candidateId: grant?.candidateId, residentsOnly })
        : Promise.resolve(null),
    [activeBoothId, grant?.candidateId, residentsOnly],
  );

  const summary = insights.data?.rows[0];
  const recorded = summary ? summary.positive + summary.neutral + summary.negative : 0;
  // Reported whichever way the choice is set, so the line below the control can
  // say what it covers before the agent switches to it.
  const noResidence = summary?.nonResident ?? 0;

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

          <div style={{ marginBottom: 12 }}>
            <p style={{ fontFamily: fonts.sans, fontSize: 12, color: colors.inkSoft, margin: '0 0 4px' }}>
              Residence status
            </p>
            <SegmentedControl segments={countingSegments} value={counting} onChange={setCounting} />
            <p style={{ fontFamily: fonts.sans, fontSize: 11, color: colors.muted, margin: '6px 0 0' }}>
              {noResidence === 0
                ? 'No one here is recorded as living elsewhere yet.'
                : residentsOnly
                  ? `${noResidence} recorded as living elsewhere, left out of the charts.`
                  : `Includes ${noResidence} recorded as living elsewhere.`}
            </p>
          </div>

          {/* DL-1/DL-2: directly under the selector, and only for an agent whose
              admin has switched downloads on. The server checks again anyway. */}
          {user?.downloadEnabled && activeBoothId && (
            <Link
              to={`/agent/voters/download?boothId=${activeBoothId}`}
              style={{
                display: 'inline-flex',
                alignItems: 'center',
                gap: 6,
                marginBottom: 12,
                fontFamily: fonts.sans,
                fontSize: 12,
                color: colors.marigoldDeep,
                textDecoration: 'none',
              }}
            >
              <FileDown size={13} /> Download list as PDF
            </Link>
          )}

          {insights.loading && !summary ? (
            <Spinner />
          ) : !summary || recorded === 0 ? (
            <EmptyState title="Nothing recorded in this booth yet" hint="Record a few voters and the charts fill in." />
          ) : (
            <div style={{ display: 'flex', flexDirection: 'column', gap: 12, paddingBottom: 16 }}>
              <div style={{ display: 'flex', gap: 10 }}>
                <StatCard
                  label="Recorded"
                  value={recorded}
                  hint={
                    residentsOnly && noResidence > 0
                      ? `${percent(recorded, summary.total)} of the roll, less those living elsewhere`
                      : `${percent(recorded, summary.total)} of the roll`
                  }
                />
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
