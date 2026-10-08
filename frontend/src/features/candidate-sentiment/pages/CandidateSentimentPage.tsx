import { useEffect, useMemo, useState } from 'react';
import { ChevronRight } from 'lucide-react';
import { Card, EmptyState, ErrorNote, Legend, SegmentedControl, Select, Spinner } from '@/shared/components';
import type { Segment } from '@/shared/components';
import { PageHeader } from '@/shared/layouts/PageHeader';
import { useAsync } from '@/shared/hooks/useAsync';
import { colors, fonts } from '@/shared/theme';
import type { CandidateSentimentRow, SentimentSource, UnitLevel } from '@/shared/types';
import { candidateSentimentApi } from '../services/candidateSentimentApi';
import { BoothSentimentRow } from '../components/BoothSentimentRow';
import { VerdictBadge } from '../components/VerdictBadge';

const sourceSegments: Segment<SentimentSource>[] = [
  { value: 'ALL', label: 'All data' },
  { value: 'VOTER', label: 'Voters' },
  { value: 'HOUSE', label: 'Houses' },
];

const rangeOptions = [
  { value: 'ALL', label: 'Whole campaign' },
  { value: '7', label: 'Last 7 days' },
  { value: '30', label: 'Last 30 days' },
];

const sortOptions = [
  { value: 'UNIT', label: 'Unit name' },
  { value: 'WEAKEST', label: 'Weakest first' },
];

function daysAgoIso(days: number) {
  const d = new Date();
  d.setDate(d.getDate() - days);
  return d.toISOString();
}

/** One step of the drill-down: where we are, and what to call it. */
interface Crumb {
  unitId: string | null;
  label: string;
  level: UnitLevel | undefined;
}

/**
 * FR-A15. One candidate, the verdict on them, and the units driving it.
 *
 * The verdict, every net lead and each booth's status come from the server —
 * recomputing them here would eventually disagree with an export.
 */
export function CandidateSentimentPage() {
  const candidates = useAsync(() => candidateSentimentApi.candidates(), []);
  const [candidateId, setCandidateId] = useState('');
  const [source, setSource] = useState<SentimentSource>('ALL');
  const [range, setRange] = useState('ALL');
  const [sort, setSort] = useState('UNIT');
  const [trail, setTrail] = useState<Crumb[]>([{ unitId: null, label: 'All units', level: undefined }]);
  // The server opens on the candidate's own panchayat when one is mapped. This
  // widens past it; it is not the default, because a candidate standing in one
  // panchayat rarely wants the whole district first.
  const [widened, setWidened] = useState(false);

  const list = candidates.data ?? [];
  const activeCandidate = candidateId || list[0]?.id || '';
  const here = trail[trail.length - 1];

  // Drilling into one candidate then switching to another would otherwise leave
  // the new candidate showing the old candidate's unit.
  useEffect(() => {
    setTrail([{ unitId: null, label: 'All units', level: undefined }]);
    setWidened(false);
  }, [activeCandidate]);

  const report = useAsync(
    () =>
      activeCandidate
        ? candidateSentimentApi.report(activeCandidate, {
            parentUnitId: here.unitId ?? undefined,
            // Asking for a level explicitly is what overrides the server's
            // candidate-panchayat default.
            level: here.level ?? (widened && trail.length === 1 ? 'DISTRICT' : undefined),
            source,
            from: range === 'ALL' ? undefined : daysAgoIso(Number(range)),
          })
        : Promise.resolve(null),
    [activeCandidate, here.unitId, here.level, source, range, widened],
  );

  const data = report.data;
  const rows = useMemo(() => {
    const all = data?.rows ?? [];
    if (sort !== 'WEAKEST') return all;
    // Units with nothing recorded are not "weak", they are unknown — they sort last.
    return [...all].sort((a, b) => {
      if (a.split.total === 0) return 1;
      if (b.split.total === 0) return -1;
      return a.netLead - b.netLead;
    });
  }, [data, sort]);

  function drill(row: CandidateSentimentRow) {
    setTrail((current) => [...current, { unitId: row.unitId, label: row.unitName, level: undefined }]);
  }

  return (
    <div>
      <PageHeader
        title="Candidate sentiment"
        subtitle="Pick a candidate to see how the ground reads for them, and which units are driving it."
      />

      {candidates.error && <ErrorNote message={candidates.error} />}

      {candidates.loading && list.length === 0 ? (
        <Spinner />
      ) : list.length === 0 ? (
        <EmptyState
          title="No sentiment recorded yet"
          hint="Once agents record sentiment in your area, the candidates they recorded it for appear here."
        />
      ) : (
        <>
          <div style={{ display: 'flex', gap: 10, flexWrap: 'wrap', alignItems: 'flex-end', marginBottom: 16 }}>
            <Select
              label="Candidate"
              options={list.map((c) => ({ value: c.id, label: c.name }))}
              value={activeCandidate}
              onChange={setCandidateId}
              style={{ marginBottom: 0, minWidth: 190 }}
            />
            <Select
              label="Date range"
              options={rangeOptions}
              value={range}
              onChange={setRange}
              style={{ marginBottom: 0, minWidth: 160 }}
            />
            <div style={{ minWidth: 220 }}>
              <p style={{ fontFamily: fonts.sans, fontSize: 12, color: colors.inkSoft, margin: '0 0 4px' }}>Data</p>
              <SegmentedControl segments={sourceSegments} value={source} onChange={setSource} pill />
            </div>
          </div>

          {report.error && <ErrorNote message={report.error} />}

          {report.loading && !data ? (
            <Spinner />
          ) : !data ? null : (
            <>
              <Card padding={16} style={{ marginBottom: 14 }}>
                <div style={{ display: 'flex', justifyContent: 'space-between', gap: 16, flexWrap: 'wrap' }}>
                  <div>
                    <p style={{ fontFamily: fonts.serif, fontSize: 20, fontWeight: 700, color: colors.ink, margin: 0 }}>
                      {data.candidateName}
                    </p>
                    <p style={{ fontFamily: fonts.sans, fontSize: 12, color: colors.inkSoft, margin: '3px 0 0' }}>
                      {data.parentUnitPath ?? 'All units in your area'} ·{' '}
                      {data.totals.total.toLocaleString('en-IN')} entries
                      {data.candidateUnitName && !widened && trail.length === 1 ? ' · contests here' : ''}
                    </p>
                    {data.candidateUnitId && trail.length === 1 && (
                      <button
                        onClick={() => setWidened((w) => !w)}
                        style={{
                          background: 'none',
                          border: 'none',
                          padding: 0,
                          marginTop: 4,
                          cursor: 'pointer',
                          fontFamily: fonts.sans,
                          fontSize: 11,
                          color: colors.marigoldDeep,
                          textDecoration: 'underline',
                        }}
                      >
                        {widened
                          ? `Back to ${data.candidateUnitName}`
                          : 'Show my whole area instead'}
                      </button>
                    )}
                  </div>
                  <VerdictBadge totals={data.totals} />
                </div>
              </Card>

              {/* Where the drill-down is, and the way back up. */}
              {trail.length > 1 && (
                <div
                  style={{
                    display: 'flex',
                    alignItems: 'center',
                    gap: 4,
                    flexWrap: 'wrap',
                    margin: '0 0 10px',
                    fontFamily: fonts.sans,
                    fontSize: 12,
                  }}
                >
                  {trail.map((crumb, i) => (
                    <span key={`${crumb.unitId ?? 'root'}-${i}`} style={{ display: 'flex', alignItems: 'center', gap: 4 }}>
                      {i > 0 && <ChevronRight size={12} color={colors.muted} />}
                      {i === trail.length - 1 ? (
                        <span style={{ color: colors.ink }}>{crumb.label}</span>
                      ) : (
                        <button
                          onClick={() => setTrail((current) => current.slice(0, i + 1))}
                          style={{
                            background: 'none',
                            border: 'none',
                            padding: 0,
                            cursor: 'pointer',
                            fontFamily: fonts.sans,
                            fontSize: 12,
                            color: colors.inkSoft,
                            textDecoration: 'underline',
                          }}
                        >
                          {crumb.label}
                        </button>
                      )}
                    </span>
                  ))}
                </div>
              )}

              <Card padding={14} style={{ marginBottom: 14 }}>
                <div
                  style={{
                    display: 'flex',
                    justifyContent: 'space-between',
                    alignItems: 'center',
                    gap: 10,
                    marginBottom: 8,
                    flexWrap: 'wrap',
                  }}
                >
                  <p style={{ fontFamily: fonts.sans, fontSize: 12, fontWeight: 500, color: colors.ink, margin: 0 }}>
                    Sentiment by {data.level.toLowerCase()} (% of what has been recorded)
                  </p>
                  <Select
                    options={sortOptions}
                    value={sort}
                    onChange={setSort}
                    style={{ marginBottom: 0, minWidth: 150 }}
                  />
                </div>

                {rows.length === 0 ? (
                  <EmptyState title="Nothing under this unit" />
                ) : (
                  rows.map((row) => <BoothSentimentRow key={row.unitId} row={row} onDrill={drill} />)
                )}

                <div style={{ marginTop: 8 }}>
                  <Legend
                    items={[
                      { name: 'Positive', color: colors.positive },
                      { name: 'Neutral', color: colors.marigold },
                      { name: 'Negative', color: colors.negative },
                    ]}
                  />
                </div>
              </Card>

              <Card padding={14}>
                <p style={{ fontFamily: fonts.sans, fontSize: 12, fontWeight: 500, color: colors.ink, margin: '0 0 8px' }}>
                  Overall · {data.parentUnitPath ?? 'your whole area'}
                </p>
                <div style={{ display: 'flex', gap: 18, flexWrap: 'wrap' }}>
                  {[
                    { label: 'Positive', value: data.totals.positivePercent, count: data.totals.positive, color: colors.positive },
                    { label: 'Neutral', value: data.totals.neutralPercent, count: data.totals.neutral, color: colors.marigold },
                    { label: 'Negative', value: data.totals.negativePercent, count: data.totals.negative, color: colors.negative },
                  ].map((part) => (
                    <div key={part.label} style={{ minWidth: 110 }}>
                      <p style={{ fontFamily: fonts.sans, fontSize: 11, color: part.color, margin: 0 }}>{part.label}</p>
                      <p style={{ fontFamily: fonts.mono, fontSize: 18, color: colors.ink, margin: '2px 0 0' }}>
                        {part.value.toFixed(1)}%
                      </p>
                      <p style={{ fontFamily: fonts.sans, fontSize: 11, color: colors.muted, margin: 0 }}>
                        {part.count.toLocaleString('en-IN')} recorded
                      </p>
                    </div>
                  ))}
                </div>
              </Card>

              <p style={{ fontFamily: fonts.sans, fontSize: 11, color: colors.muted, margin: '10px 0 20px' }}>
                Positive when the positive share is above the negative share. Booth tags: Safe 20+ points, Watch 5 to
                20, At risk under 5. House tallies count each person, so a house of six weighs six. Sentiment recorded
                by agents is not a vote count.
              </p>
            </>
          )}
        </>
      )}
    </div>
  );
}
