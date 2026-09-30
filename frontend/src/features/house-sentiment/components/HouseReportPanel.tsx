import { useMemo, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { ChevronRight } from 'lucide-react';
import { Download } from 'lucide-react';
import {
  Button,
  Card,
  ChartCard,
  ConfidenceDonut,
  EmptyState,
  ErrorNote,
  Legend,
  SentimentBarChart,
  SentimentDonut,
  Select,
  Spinner,
  StatCard,
  useToast,
} from '@/shared/components';
import { useAsync } from '@/shared/hooks/useAsync';
import { useUnitTree } from '@/shared/hooks/useUnitTree';
import { downloadBlob } from '@/lib/apiClient';
import { referenceApi } from '@/shared/services/referenceApi';
import { colors, fonts } from '@/shared/theme';
import { percent } from '@/shared/utils/format';
import type { ConfidenceLevel, HouseUnitSummary, SentimentSummary, UnitLevel } from '@/shared/types';
import { houseSentimentApi } from '../services/houseSentimentApi';
import type { HouseReportFilter } from '../services/houseSentimentApi';

const levelOptions = [
  { value: 'BOOTH', label: 'Booth' },
  { value: 'PANCHAYAT', label: 'Panchayat' },
  { value: 'BLOCK', label: 'Block' },
  { value: 'DISTRICT', label: 'District' },
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

/**
 * The charts take a roll summary, and a house tally maps onto it once the
 * residents stand in for the total. "Not recorded" is always zero: nothing says
 * how many houses a booth holds.
 */
function asSummary(row: HouseUnitSummary): SentimentSummary {
  return {
    unitId: row.unitId,
    unitName: row.unitName,
    unitLevel: row.unitLevel,
    positive: row.positive,
    neutral: row.neutral,
    negative: row.negative,
    notRecorded: 0,
    total: row.residents,
  };
}

const numericCell = {
  padding: '10px 16px',
  textAlign: 'right',
  borderBottom: `1px solid ${colors.line}`,
  fontFamily: fonts.mono,
  fontSize: 12,
} as const;

/**
 * Pre-election (house-level) figures with the same filters, charts, table and
 * exports as the named-voter report. Shared by the Pre-election dashboard and
 * the Reports tab so the two can never disagree.
 */
export function HouseReportPanel() {
  const navigate = useNavigate();
  const { notify } = useToast();
  const tree = useUnitTree();
  const candidates = useAsync(() => referenceApi.candidates(), []);

  const [level, setLevel] = useState<UnitLevel>('PANCHAYAT');
  const [unitId, setUnitId] = useState('');
  const [candidateId, setCandidateId] = useState('ALL');
  const [confidence, setConfidence] = useState<ConfidenceLevel | 'ALL'>('ALL');
  const [range, setRange] = useState('ALL');
  const [exporting, setExporting] = useState<'pdf' | 'excel' | null>(null);

  const filter: HouseReportFilter = useMemo(
    () => ({
      level,
      unitId: unitId || undefined,
      candidateId: candidateId === 'ALL' ? undefined : candidateId,
      confidence,
      from: range === 'ALL' ? undefined : daysAgoIso(Number(range)),
    }),
    [level, unitId, candidateId, confidence, range],
  );

  const report = useAsync(() => houseSentimentApi.adminSummary(filter), [filter]);

  /** The drill-down repeats the report's filters, so its figures match the row. */
  function openUnit(unitId: string) {
    const query = new URLSearchParams();
    if (filter.candidateId) query.set('candidateId', filter.candidateId);
    if (filter.confidence && filter.confidence !== 'ALL') query.set('confidence', filter.confidence);
    if (filter.from) query.set('from', filter.from);
    const suffix = query.toString();
    navigate(`/admin/pre-election/unit/${unitId}${suffix ? `?${suffix}` : ''}`);
  }

  async function exportReport(format: 'pdf' | 'excel') {
    setExporting(format);
    try {
      const blob = await houseSentimentApi.adminExport(filter, format);
      const extension = format === 'pdf' ? 'pdf' : 'xlsx';
      const stamp = new Date().toISOString().slice(0, 10);
      downloadBlob(blob, `k-pulse-pre-election-${level.toLowerCase()}-${stamp}.${extension}`);
      notify(`${format === 'pdf' ? 'PDF' : 'Excel'} report downloaded`);
    } catch (err) {
      notify(err instanceof Error ? err.message : 'Export failed', 'error');
    } finally {
      setExporting(null);
    }
  }

  const rows = report.data?.rows ?? [];
  const totals = report.data?.totals;
  const unitChoices = tree.byLevel(level);
  const chartRows = useMemo(() => rows.filter((r) => r.houses > 0).map(asSummary), [rows]);
  const totalSummary: SentimentSummary | null = totals
    ? {
        unitId: 'all',
        unitName: 'All units',
        unitLevel: level,
        positive: totals.positive,
        neutral: totals.neutral,
        negative: totals.negative,
        notRecorded: 0,
        total: totals.residents,
      }
    : null;

  return (
    <div>
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
          options={[
            { value: 'ALL', label: 'All candidates' },
            ...(candidates.data ?? []).map((c) => ({ value: c.id, label: c.name })),
          ]}
          value={candidateId}
          onChange={setCandidateId}
          style={{ marginBottom: 0, minWidth: 170 }}
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

      {report.loading && !totals ? (
        <Spinner />
      ) : !totals || totals.houses === 0 ? (
        <EmptyState
          title="No houses recorded for this filter"
          hint="Agents record houses from the Pre-election sentiment screen in the field app."
        />
      ) : (
        <>
          <div style={{ display: 'flex', gap: 12, flexWrap: 'wrap', marginBottom: 16 }}>
            <StatCard label="Houses recorded" value={totals.houses} />
            <StatCard label="People counted" value={totals.people} />
            <StatCard
              label="Residents"
              value={totals.residents}
              hint={`${percent(totals.residents, totals.people)} of people counted`}
            />
            <StatCard
              label="Positive"
              value={percent(totals.positive, totals.residents)}
              hint={`${totals.positive.toLocaleString('en-IN')} of ${totals.residents.toLocaleString('en-IN')} residents`}
            />
          </div>

          <div style={{ display: 'flex', gap: 16, flexWrap: 'wrap', marginBottom: 16 }}>
            <ChartCard title={`Sentiment by ${level.toLowerCase()}`} style={{ flex: '2 1 460px' }}>
              <SentimentBarChart data={chartRows} />
            </ChartCard>
            <ChartCard title="Overall split" style={{ flex: '1 1 240px' }}>
              <SentimentDonut data={totalSummary ? [totalSummary] : []} />
            </ChartCard>
            <ChartCard title="Confidence" style={{ flex: '1 1 240px' }}>
              <ConfidenceDonut confidence={totals.confidence} />
            </ChartCard>
          </div>

          <Card padding={0}>
            <div style={{ overflowX: 'auto' }}>
              <table style={{ width: '100%', borderCollapse: 'collapse', fontFamily: fonts.sans, fontSize: 13 }}>
                <thead>
                  <tr style={{ background: colors.paper }}>
                    {['Unit', 'Houses', 'People', 'Residents', 'Positive', 'Neutral', 'Negative', 'Positive %', ''].map(
                      (h, i) => (
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
                      ),
                    )}
                  </tr>
                </thead>
                <tbody>
                  {rows.map((row) => (
                    <tr
                      key={row.unitId}
                      onClick={() => openUnit(row.unitId)}
                      title={`See every house recorded in ${row.unitName}`}
                      style={{ cursor: 'pointer' }}
                    >
                      <td style={{ padding: '10px 16px', borderBottom: `1px solid ${colors.line}` }}>{row.unitName}</td>
                      {[row.houses, row.people, row.residents, row.positive, row.neutral, row.negative].map(
                        (value, i) => (
                          <td key={i} style={numericCell}>
                            {value.toLocaleString('en-IN')}
                          </td>
                        ),
                      )}
                      <td style={numericCell}>{row.residents > 0 ? percent(row.positive, row.residents) : '—'}</td>
                      <td style={{ ...numericCell, padding: '10px 12px' }}>
                        <ChevronRight size={14} color={colors.inkSoft} />
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          </Card>

          <div style={{ margin: '10px 0 20px' }}>
            <Legend
              items={[
                { name: 'Positive', color: colors.positive },
                { name: 'Neutral', color: colors.marigold },
                { name: 'Negative', color: colors.negative },
              ]}
            />
            <p style={{ fontFamily: fonts.sans, fontSize: 11, color: colors.muted, margin: '6px 0 0' }}>
              Select a unit to see every house recorded in it. Households, not named voters. Sentiment covers residents only; people counted at a house but living
              elsewhere are not part of the breakdown. Units with no houses recorded are listed with zeros.
            </p>
          </div>
        </>
      )}
    </div>
  );
}
