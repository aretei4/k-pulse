import { useMemo, useState } from 'react';
import { useSearchParams } from 'react-router-dom';
import { Download } from 'lucide-react';
import type { Segment } from '@/shared/components';
import {
  Button,
  Card,
  ChartCard,
  ConfidenceDonut,
  EmptyState,
  ErrorNote,
  SegmentedControl,
  SentimentBarChart,
  Select,
  Spinner,
  useToast,
} from '@/shared/components';
import { PageHeader } from '@/shared/layouts/PageHeader';
import { HouseReportPanel } from '@/features/house-sentiment/components/HouseReportPanel';
import { useAsync } from '@/shared/hooks/useAsync';
import { useUnitTree } from '@/shared/hooks/useUnitTree';
import { downloadBlob } from '@/lib/apiClient';
import { referenceApi } from '@/shared/services/referenceApi';
import { colors, fonts } from '@/shared/theme';
import { percent } from '@/shared/utils/format';
import type { ConfidenceLevel, ReportFilter, SentimentValue, UnitLevel } from '@/shared/types';
import { reportsApi } from '../services/reportsApi';

type ReportTab = 'VOTER' | 'HOUSE';

/** Two datasets, deliberately never mixed: named-voter entries and house tallies. */
const tabs: Segment<ReportTab>[] = [
  { value: 'VOTER', label: 'Voter sentiment' },
  { value: 'HOUSE', label: 'Pre-election' },
];

const levelOptions = [
  { value: 'BOOTH', label: 'Booth' },
  { value: 'PANCHAYAT', label: 'Panchayat' },
  { value: 'BLOCK', label: 'Block' },
  { value: 'DISTRICT', label: 'District' },
];

const sentimentOptions = [
  { value: 'ALL', label: 'All' },
  { value: 'POSITIVE', label: 'Positive' },
  { value: 'NEUTRAL', label: 'Neutral' },
  { value: 'NEGATIVE', label: 'Negative' },
];

const confidenceOptions = [
  { value: 'ALL', label: 'All' },
  { value: 'HIGH', label: 'High' },
  { value: 'MEDIUM', label: 'Medium' },
  { value: 'LOW', label: 'Low' },
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

export function ReportsPage() {
  const { notify } = useToast();
  const tree = useUnitTree();
  const candidates = useAsync(() => referenceApi.candidates(), []);

  const [level, setLevel] = useState<UnitLevel>('BOOTH');
  const [unitId, setUnitId] = useState('');
  const [candidateId, setCandidateId] = useState('ALL');
  const [sentiment, setSentiment] = useState<SentimentValue | 'ALL'>('ALL');
  const [confidence, setConfidence] = useState<ConfidenceLevel | 'ALL'>('ALL');
  const [range, setRange] = useState('ALL');
  const [exporting, setExporting] = useState<'pdf' | 'excel' | null>(null);
  // The tab lives in the URL so returning from a unit's houses comes back to it.
  const [params, setParams] = useSearchParams();
  const tab: ReportTab = params.get('tab') === 'house' ? 'HOUSE' : 'VOTER';
  const setTab = (next: ReportTab) =>
    setParams(next === 'HOUSE' ? { tab: 'house' } : {}, { replace: true });

  const filter: ReportFilter = useMemo(
    () => ({
      level,
      unitId: unitId || undefined,
      candidateId: candidateId === 'ALL' ? undefined : candidateId,
      sentiment,
      confidence,
      from: range === 'ALL' ? undefined : daysAgoIso(Number(range)),
    }),
    [level, unitId, candidateId, sentiment, confidence, range],
  );

  const report = useAsync(() => reportsApi.summary(filter), [filter]);

  async function exportReport(format: 'pdf' | 'excel') {
    setExporting(format);
    try {
      const blob = await reportsApi.export(filter, format);
      const extension = format === 'pdf' ? 'pdf' : 'xlsx';
      const stamp = new Date().toISOString().slice(0, 10);
      downloadBlob(blob, `k-pulse-sentiment-${level.toLowerCase()}-${stamp}.${extension}`);
      notify(`${format === 'pdf' ? 'PDF' : 'Excel'} report downloaded`);
    } catch (err) {
      notify(err instanceof Error ? err.message : 'Export failed', 'error');
    } finally {
      setExporting(null);
    }
  }

  const rows = report.data?.rows ?? [];
  const unitChoices = tree.byLevel(level);

  return (
    <div>
      <PageHeader
        title="Reports"
        subtitle={
          tab === 'VOTER'
            ? 'Filter by unit, candidate, sentiment and date range, then export.'
            : 'House-level tallies from the field. Households, not named voters — a separate dataset from the voter roll.'
        }
      />

      <div style={{ maxWidth: 320, marginBottom: 16 }}>
        <SegmentedControl segments={tabs} value={tab} onChange={setTab} pill />
      </div>

      {tab === 'HOUSE' ? (
        <HouseReportPanel />
      ) : (
      <>
      <div style={{ display: 'flex', gap: 10, flexWrap: 'wrap', marginBottom: 14 }}>
        <Select
          label="Unit level"
          options={levelOptions}
          value={level}
          onChange={(v) => {
            setLevel(v as UnitLevel);
            setUnitId('');
          }}
          style={{ marginBottom: 0, minWidth: 150 }}
        />
        <Select
          label="Unit"
          options={[{ value: '', label: 'All units' }, ...unitChoices.map((u) => ({ value: u.id, label: u.name }))]}
          value={unitId}
          onChange={setUnitId}
          style={{ marginBottom: 0, minWidth: 170 }}
        />
        <Select
          label="Candidate"
          options={[{ value: 'ALL', label: 'All candidates' }, ...(candidates.data ?? []).map((c) => ({ value: c.id, label: c.name }))]}
          value={candidateId}
          onChange={setCandidateId}
          style={{ marginBottom: 0, minWidth: 170 }}
        />
        <Select
          label="Sentiment"
          options={sentimentOptions}
          value={sentiment}
          onChange={(v) => setSentiment(v as SentimentValue | 'ALL')}
          style={{ marginBottom: 0, minWidth: 140 }}
        />
        <Select
          label="Confidence"
          options={confidenceOptions}
          value={confidence}
          onChange={(v) => setConfidence(v as ConfidenceLevel | 'ALL')}
          style={{ marginBottom: 0, minWidth: 140 }}
        />
        <Select
          label="Date range"
          options={rangeOptions}
          value={range}
          onChange={setRange}
          style={{ marginBottom: 0, minWidth: 160 }}
        />
      </div>

      <div style={{ display: 'flex', gap: 8, marginBottom: 18 }}>
        <Button icon={Download} tone="accent" loading={exporting === 'pdf'} onClick={() => void exportReport('pdf')}>
          Download PDF
        </Button>
        <Button icon={Download} tone="ghost" loading={exporting === 'excel'} onClick={() => void exportReport('excel')}>
          Download Excel
        </Button>
      </div>

      {report.error && <ErrorNote message={report.error} />}

      {report.loading && rows.length === 0 ? (
        <Spinner />
      ) : rows.length === 0 ? (
        <EmptyState title="Nothing to report for this filter" hint="Widen the date range or pick another level." />
      ) : (
        <>
          <div style={{ display: 'flex', gap: 16, flexWrap: 'wrap', marginBottom: 16 }}>
            <ChartCard title={`Sentiment by ${level.toLowerCase()}`} style={{ flex: '2 1 460px' }}>
              <SentimentBarChart data={rows} />
            </ChartCard>
            <ChartCard title="Confidence level" style={{ flex: '1 1 240px' }}>
              <ConfidenceDonut confidence={report.data!.confidence} />
            </ChartCard>
          </div>

          <Card padding={0}>
            <table style={{ width: '100%', borderCollapse: 'collapse', fontFamily: fonts.sans, fontSize: 13 }}>
              <thead>
                <tr style={{ background: colors.paper }}>
                  {['Unit', 'Positive', 'Neutral', 'Negative', 'Recorded', 'Coverage'].map((h, i) => (
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
                {rows.map((row) => {
                  const recorded = row.positive + row.neutral + row.negative;
                  return (
                    <tr key={row.unitId}>
                      <td style={{ padding: '10px 16px', borderBottom: `1px solid ${colors.line}` }}>{row.unitName}</td>
                      {[row.positive, row.neutral, row.negative, recorded].map((value, i) => (
                        <td key={i} style={numericCell}>
                          {value.toLocaleString('en-IN')}
                        </td>
                      ))}
                      <td style={numericCell}>{percent(recorded, row.total)}</td>
                    </tr>
                  );
                })}
              </tbody>
            </table>
          </Card>
        </>
      )}
      </>
      )}
    </div>
  );
}

const numericCell = {
  padding: '10px 16px',
  textAlign: 'right',
  borderBottom: `1px solid ${colors.line}`,
  fontFamily: fonts.mono,
  color: colors.inkSoft,
} as const;
