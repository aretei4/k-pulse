import { useState } from 'react';
import {
  Card,
  ChartCard,
  ConfidenceDonut,
  EmptyState,
  ErrorNote,
  SentimentBarChart,
  SentimentDonut,
  Select,
  Spinner,
  StatCard,
} from '@/shared/components';
import { PageHeader } from '@/shared/layouts/PageHeader';
import { useAsync } from '@/shared/hooks/useAsync';
import { referenceApi } from '@/shared/services/referenceApi';
import { colors, fonts } from '@/shared/theme';
import type { ConfidenceLevel, SentimentValue, UnitLevel } from '@/shared/types';
import { dashboardApi } from '../services/dashboardApi';

const levelOptions = [
  { value: 'BOOTH', label: 'Level: Booth' },
  { value: 'PANCHAYAT', label: 'Level: Panchayat' },
  { value: 'BLOCK', label: 'Level: Block' },
  { value: 'DISTRICT', label: 'Level: District' },
];

const sentimentOptions = [
  { value: 'ALL', label: 'Sentiment: All' },
  { value: 'POSITIVE', label: 'Sentiment: Positive' },
  { value: 'NEUTRAL', label: 'Sentiment: Neutral' },
  { value: 'NEGATIVE', label: 'Sentiment: Negative' },
];

const confidenceOptions = [
  { value: 'ALL', label: 'Confidence: All' },
  { value: 'HIGH', label: 'Confidence: High' },
  { value: 'MEDIUM', label: 'Confidence: Medium' },
  { value: 'LOW', label: 'Confidence: Low' },
];

const rangeOptions = [
  { value: 'ALL', label: 'Whole campaign' },
  { value: '7', label: 'Last 7 days' },
  { value: '30', label: 'Last 30 days' },
];

function daysAgoIso(days: number) {
  const d = new Date();
  d.setDate(d.getDate() - days);
  return d.toISOString();
}

export function DashboardPage() {
  const [level, setLevel] = useState<UnitLevel>('BOOTH');
  const [sentiment, setSentiment] = useState<SentimentValue | 'ALL'>('ALL');
  const [confidence, setConfidence] = useState<ConfidenceLevel | 'ALL'>('ALL');
  const [candidateId, setCandidateId] = useState('ALL');
  const [range, setRange] = useState('ALL');

  const candidates = useAsync(() => referenceApi.candidates(), []);
  const from = range === 'ALL' ? undefined : daysAgoIso(Number(range));

  const summary = useAsync(
    () => dashboardApi.summary({ level, sentiment, confidence, candidateId, from }),
    [level, sentiment, confidence, candidateId, from],
  );

  const candidateOptions = [
    { value: 'ALL', label: 'Candidate: All' },
    ...(candidates.data ?? []).map((c) => ({ value: c.id, label: `Candidate: ${c.name}` })),
  ];

  const data = summary.data;

  return (
    <div>
      <PageHeader title="Sentiment overview" subtitle="Constituency S18-98 · Bhadrak, Odisha" />

      <div style={{ display: 'flex', gap: 10, flexWrap: 'wrap', marginBottom: 16 }}>
        <Select
          options={levelOptions}
          value={level}
          onChange={(v) => setLevel(v as UnitLevel)}
          style={{ marginBottom: 0, minWidth: 160 }}
        />
        <Select
          options={sentimentOptions}
          value={sentiment}
          onChange={(v) => setSentiment(v as SentimentValue | 'ALL')}
          style={{ marginBottom: 0, minWidth: 170 }}
        />
        <Select
          options={confidenceOptions}
          value={confidence}
          onChange={(v) => setConfidence(v as ConfidenceLevel | 'ALL')}
          style={{ marginBottom: 0, minWidth: 175 }}
        />
        <Select
          options={candidateOptions}
          value={candidateId}
          onChange={setCandidateId}
          style={{ marginBottom: 0, minWidth: 190 }}
        />
        <Select options={rangeOptions} value={range} onChange={setRange} style={{ marginBottom: 0, minWidth: 150 }} />
      </div>

      {summary.error && <ErrorNote message={summary.error} />}

      {summary.loading && !data ? (
        <Spinner label="Loading sentiment…" />
      ) : data ? (
        <>
          <div style={{ display: 'flex', gap: 12, marginBottom: 16, flexWrap: 'wrap' }}>
            <StatCard label="Entries recorded" value={data.entriesRecorded} />
            <StatCard label="Active field agents" value={data.activeAgents} />
            <StatCard label="Pending access requests" value={data.pendingAccessRequests} />
            <StatCard label="Pending voter changes" value={data.pendingChangeRequests} />
          </div>

          {data.byUnit.length === 0 ? (
            <EmptyState title="No sentiment recorded yet for this filter" hint="Try a wider date range or another level." />
          ) : (
            <>
              <div style={{ display: 'flex', gap: 16, flexWrap: 'wrap', marginBottom: 16 }}>
                <ChartCard title={`Sentiment by ${level.toLowerCase()}`} style={{ flex: '2 1 460px' }}>
                  <SentimentBarChart data={data.byUnit} />
                </ChartCard>
                <ChartCard title="Overall split" style={{ flex: '1 1 240px' }}>
                  <SentimentDonut data={data.byUnit} />
                </ChartCard>
                <ChartCard title="Confidence level" style={{ flex: '1 1 240px' }}>
                  <ConfidenceDonut confidence={data.confidence} />
                </ChartCard>
              </div>

              <Card padding={0}>
                <table style={{ width: '100%', borderCollapse: 'collapse', fontFamily: fonts.sans, fontSize: 13 }}>
                  <thead>
                    <tr style={{ background: colors.paper }}>
                      {['Unit', 'Positive', 'Neutral', 'Negative', 'Not recorded', 'Voters'].map((h, i) => (
                        <th
                          key={h}
                          style={{
                            textAlign: i === 0 ? 'left' : 'right',
                            padding: '10px 16px',
                            fontSize: 11,
                            fontWeight: 500,
                            color: colors.inkSoft,
                            borderBottom: `1px solid ${colors.line}`,
                          }}
                        >
                          {h}
                        </th>
                      ))}
                    </tr>
                  </thead>
                  <tbody>
                    {data.byUnit.map((row) => (
                      <tr key={row.unitId}>
                        <td style={{ padding: '10px 16px', borderBottom: `1px solid ${colors.line}`, color: colors.ink }}>
                          {row.unitName}
                        </td>
                        {[row.positive, row.neutral, row.negative, row.notRecorded, row.total].map((value, i) => (
                          <td
                            key={i}
                            style={{
                              padding: '10px 16px',
                              textAlign: 'right',
                              borderBottom: `1px solid ${colors.line}`,
                              fontFamily: fonts.mono,
                              color: colors.inkSoft,
                            }}
                          >
                            {value.toLocaleString('en-IN')}
                          </td>
                        ))}
                      </tr>
                    ))}
                  </tbody>
                </table>
              </Card>
            </>
          )}
        </>
      ) : null}
    </div>
  );
}
