import { useEffect, useMemo, useState } from 'react';
import { BarChart3, CheckCircle2, Download } from 'lucide-react';
import {
  Button,
  Card,
  ChartCard,
  EmptyState,
  ErrorNote,
  Select,
  Spinner,
  StatCard,
  useToast,
} from '@/shared/components';
import { PageHeader } from '@/shared/layouts/PageHeader';
import { useAsync } from '@/shared/hooks/useAsync';
import { useAuth } from '@/shared/hooks/useAuth';
import { canOpenAfterSaving, openSavedFile, saveFile } from '@/lib/download';
import { colors, fonts } from '@/shared/theme';
import { formatDate, percent } from '@/shared/utils/format';
import type { AgentReport, AgentReportScope, ConfidenceLevel } from '@/shared/types';
import { agentReportsApi } from '../services/agentReportsApi';
import { BoothStack, SliceLegend, TotalsPie, sliceLabel, sliceValue } from '../components/ReportCharts';
import type { Slice } from '../components/ReportCharts';

const ALL_SLICES: Slice[] = ['POSITIVE', 'NEUTRAL', 'NEGATIVE', 'NOT_RECORDED'];
const ALL_CONFIDENCE: ConfidenceLevel[] = ['HIGH', 'MEDIUM', 'LOW'];

/**
 * The caste groups, matching the Caste enum plus the voters nobody has recorded
 * one for. Sensitive data, so the page says so where it is asked for.
 */
const CASTE_CHOICES = [
  { value: 'GENERAL', label: 'General' },
  { value: 'SEBC', label: 'SEBC / OBC' },
  { value: 'SC', label: 'SC' },
  { value: 'ST', label: 'ST' },
  { value: 'MINORITY', label: 'Minority' },
  { value: 'OTHER', label: 'Other' },
  { value: 'NONE', label: 'Not recorded' },
];

const ALL_CASTES = CASTE_CHOICES.map((choice) => choice.value);

const confidenceLabel: Record<ConfidenceLevel, string> = { HIGH: 'High', MEDIUM: 'Medium', LOW: 'Low' };

/** AG-7: every section hides, and a hidden one says what it is holding. */
function Section({
  step,
  title,
  summary,
  hidden,
  onToggle,
  children,
}: {
  step?: number;
  title: string;
  summary: string;
  hidden: boolean;
  onToggle: () => void;
  children: React.ReactNode;
}) {
  return (
    <Card padding={14} style={{ marginBottom: 12 }}>
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', gap: 8 }}>
        <p style={{ fontFamily: fonts.serif, fontSize: 15, color: colors.ink, margin: 0 }}>
          {step !== undefined && (
            <span
              style={{
                display: 'inline-flex',
                alignItems: 'center',
                justifyContent: 'center',
                width: 20,
                height: 20,
                marginRight: 8,
                borderRadius: 10,
                background: colors.marigoldDeep,
                color: colors.paper,
                fontFamily: fonts.sans,
                fontSize: 11,
              }}
            >
              {step}
            </span>
          )}
          {title}
        </p>
        <button
          type="button"
          onClick={onToggle}
          style={{
            background: 'none',
            border: 'none',
            padding: 0,
            cursor: 'pointer',
            fontFamily: fonts.sans,
            fontSize: 12,
            color: colors.marigoldDeep,
            textDecoration: 'underline',
          }}
        >
          {hidden ? 'Show' : 'Hide'}
        </button>
      </div>
      {hidden ? (
        <p style={{ fontFamily: fonts.sans, fontSize: 12, color: colors.muted, margin: '6px 0 0' }}>{summary}</p>
      ) : (
        <div style={{ marginTop: 10 }}>{children}</div>
      )}
    </Card>
  );
}

function CheckRow<T extends string | number>({
  options,
  labelOf,
  chosen,
  onChange,
}: {
  options: T[];
  labelOf: (value: T) => string;
  chosen: T[];
  onChange: (next: T[]) => void;
}) {
  return (
    <div style={{ display: 'flex', flexWrap: 'wrap', gap: '6px 14px' }}>
      {options.map((option) => (
        <label
          key={String(option)}
          style={{
            display: 'inline-flex',
            alignItems: 'center',
            gap: 6,
            fontFamily: fonts.sans,
            fontSize: 12,
            color: colors.ink,
            minWidth: 92,
          }}
        >
          <input
            type="checkbox"
            checked={chosen.includes(option)}
            onChange={(e) =>
              onChange(e.target.checked ? [...chosen, option] : chosen.filter((value) => value !== option))
            }
          />
          {labelOf(option)}
        </label>
      ))}
    </div>
  );
}

/**
 * One checkbox group's heading (AG-7): its Select all / Clear all, and its own
 * Hide link. Hiding a group never changes what is ticked in it, so the summary
 * it leaves behind is what the report will still use.
 */
function Group({
  title,
  note,
  hidden,
  onToggle,
  onAll,
  onNone,
  summary,
  children,
}: {
  title: string;
  note?: string;
  hidden: boolean;
  onToggle: () => void;
  onAll: () => void;
  onNone: () => void;
  summary: string;
  children: React.ReactNode;
}) {
  return (
    <div style={{ marginTop: 12 }}>
      <div style={{ display: 'flex', alignItems: 'baseline', gap: 8, marginBottom: 6, flexWrap: 'wrap' }}>
        <span style={{ fontFamily: fonts.sans, fontSize: 12, fontWeight: 600, color: colors.ink }}>{title}</span>
        {!hidden && (
          <>
            <button type="button" onClick={onAll} style={linkStyle}>
              Select all
            </button>
            <button type="button" onClick={onNone} style={linkStyle}>
              Clear all
            </button>
          </>
        )}
        {note && <span style={{ fontFamily: fonts.sans, fontSize: 11, color: colors.muted }}>{note}</span>}
        <button type="button" onClick={onToggle} style={{ ...linkStyle, marginLeft: 'auto' }}>
          {hidden ? 'Show' : 'Hide'}
        </button>
      </div>
      {hidden ? (
        <p style={{ fontFamily: fonts.sans, fontSize: 11, color: colors.muted, margin: 0 }}>{summary}</p>
      ) : (
        children
      )}
    </div>
  );
}

const linkStyle: React.CSSProperties = {
  background: 'none',
  border: 'none',
  padding: 0,
  cursor: 'pointer',
  fontFamily: fonts.sans,
  fontSize: 11,
  color: colors.marigoldDeep,
  textDecoration: 'underline',
};

/**
 * FR-U14. One panchayat, and only the booths the agent has been approved for.
 *
 * <p>The page never asks for a wider area: it picks from the grants the server
 * hands back, and the server checks the choice again on every call. What the
 * agent ticks decides what is counted and drawn, and changing a tick after
 * drawing fades the report until it is drawn again (AG-6).
 */
export function AgentReportsPage() {
  const { notify } = useToast();
  const { user } = useAuth();
  const scopes = useAsync(() => agentReportsApi.scope(), []);
  const [downloading, setDownloading] = useState(false);
  // The file just saved, so the phone has something to open it with. In the
  // shell a download lands in the phone's Downloads folder and nothing in the
  // app points at it, so the agent is left with a file they cannot reach.
  const [saved, setSaved] = useState<{ fileName: string; uri: string | null } | null>(null);

  const [scopeKey, setScopeKey] = useState('');
  const [slices, setSlices] = useState<Slice[]>(ALL_SLICES);
  const [confidence, setConfidence] = useState<ConfidenceLevel[]>(ALL_CONFIDENCE);
  const [wards, setWards] = useState<number[]>([]);
  const [booths, setBooths] = useState<string[]>([]);
  const [castes, setCastes] = useState<string[]>(ALL_CASTES);
  const [drawn, setDrawn] = useState<{ report: AgentReport; slices: Slice[]; key: string; at: string } | null>(null);
  const [hidden, setHidden] = useState<Record<string, boolean>>({});

  const options = scopes.data ?? [];
  const scope: AgentReportScope | undefined =
    options.find((option) => `${option.panchayatId}:${option.candidateId}` === scopeKey) ?? options[0];

  // The agent's booths and wards are only known once their scope is: start
  // with all of them, which is the report they would have drawn before.
  useEffect(() => {
    if (!scope) return;
    setWards(scope.wards);
    setBooths(scope.booths.map((booth) => booth.id));
  }, [scope?.panchayatId, scope?.candidateId]);

  const report = useAsync(
    () =>
      scope
        ? agentReportsApi.report({
            panchayatId: scope.panchayatId,
            candidateId: scope.candidateId,
            booth: booths,
            confidence,
            ward: wards,
            caste: castes,
          })
        : Promise.resolve(null),
    [scope?.panchayatId, scope?.candidateId, booths, confidence, wards, castes],
  );

  /** What the drawn report was drawn from. Any change to it makes it stale (AG-6). */
  const choiceKey = useMemo(
    () =>
      JSON.stringify({
        panchayat: scope?.panchayatId,
        candidate: scope?.candidateId,
        booths: [...booths].sort(),
        slices: [...slices].sort(),
        confidence: [...confidence].sort(),
        wards: [...wards].sort((a, b) => a - b),
        castes: [...castes].sort(),
      }),
    [scope?.panchayatId, scope?.candidateId, booths, slices, confidence, wards, castes],
  );
  const stale = drawn !== null && drawn.key !== choiceKey;

  const missing: string[] = [];
  if (booths.length === 0) missing.push('one booth');
  if (slices.length === 0) missing.push('one sentiment');
  if (confidence.length === 0) missing.push('one confidence level');
  if (scope && scope.wards.length > 0 && wards.length === 0) missing.push('one ward');
  if (castes.length === 0) missing.push('one caste');

  /**
   * AG-9. The same choices as the drawn report, so what the agent carries out
   * of the building is the people the charts counted — not a fresh query that
   * might have moved on.
   */
  async function downloadVoters(snapshot: { report: AgentReport; slices: Slice[] }) {
    if (!scope) return;
    setDownloading(true);
    setSaved(null);
    try {
      const blob = await agentReportsApi.voters({
        panchayatId: scope.panchayatId,
        candidateId: scope.candidateId,
        booth: booths,
        sentiment: snapshot.slices.map((slice) => (slice === 'NOT_RECORDED' ? 'NOT_RECORDED' : slice)),
        confidence,
        ward: wards,
        caste: castes,
      });
      // The phone shell writes it to Downloads and hands back something to
      // open it with; a browser downloads it as usual and hands back nothing.
      const fileName = `${scope.panchayatName}_voter-details.pdf`;
      const uri = await saveFile(blob, fileName);
      setSaved({ fileName, uri });
      notify(uri ? 'Saved to Downloads' : 'Voter details downloaded');
    } catch (err) {
      notify(err instanceof Error ? err.message : 'Could not build the file', 'error');
    } finally {
      setDownloading(false);
    }
  }

  const live = report.data;
  // What the file would hold: the ticked tiles of the drawn report.
  const voterCount = drawn
    ? drawn.slices.reduce((sum, slice) => sum + sliceValue(drawn.report.totals, slice), 0)
    : 0;
  const toggle = (key: string) => setHidden((current) => ({ ...current, [key]: !current[key] }));
  const HIDEABLE = ['area', 'choices', 'report', 'g:booth', 'g:sentiment', 'g:confidence', 'g:ward', 'g:caste'];
  const allHidden = HIDEABLE.every((key) => hidden[key]);

  if (scopes.loading) return <Spinner />;
  if (scopes.error) {
    return (
      <div style={{ padding: '16px 20px' }}>
        <ErrorNote message={scopes.error} />
      </div>
    );
  }

  if (options.length === 0) {
    return (
      <div style={{ padding: '16px 20px' }}>
        <PageHeader title="Reports" size={18} />
        <EmptyState
          title="No approved booths"
          hint="A report covers the booths approved for you. Ask your admin for access to a panchayat first."
        />
      </div>
    );
  }

  return (
    <div style={{ padding: '16px 20px 0' }}>
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'start', gap: 8 }}>
        <PageHeader title="Reports" subtitle="One panchayat, for the booths approved for you" size={18} />
        <button
          type="button"
          onClick={() =>
            setHidden(allHidden ? {} : Object.fromEntries(HIDEABLE.map((key) => [key, true])))
          }
          style={{ ...linkStyle, fontSize: 12, marginTop: 4 }}
        >
          {allHidden ? 'Show all sections' : 'Hide all sections'}
        </button>
      </div>

      <Section
        step={1}
        title="Your area"
        summary={scope ? `${scope.panchayatName} · ${scope.candidateName}` : ''}
        hidden={Boolean(hidden.area)}
        onToggle={() => toggle('area')}
      >
        {/* Always shown, even with one grant: it is the only place the report
            names the area and candidate it covers. */}
        <Select
          label="Panchayat and candidate"
          options={options.map((option) => ({
            value: `${option.panchayatId}:${option.candidateId}`,
            label: `${option.panchayatName} · ${option.candidateName}`,
          }))}
          value={scope ? `${scope.panchayatId}:${scope.candidateId}` : ''}
          disabled={options.length === 1}
          onChange={(value) => {
            setScopeKey(value);
            setDrawn(null);
          }}
        />

      </Section>

      <Section
        step={2}
        title="Choose what to include"
        summary={`${booths.length} booth(s), ${slices.length} sentiment, ${confidence.length} confidence, ${wards.length} ward(s), ${castes.length} caste group(s)`}
        hidden={Boolean(hidden.choices)}
        onToggle={() => toggle('choices')}
      >
        {scope && (
          <Group
            title="Booth"
            note={`${booths.length} of ${scope.booths.length} approved for you`}
            hidden={Boolean(hidden['g:booth'])}
            onToggle={() => toggle('g:booth')}
            onAll={() => setBooths(scope.booths.map((booth) => booth.id))}
            onNone={() => setBooths([])}
            summary={`${booths.length} booth(s) ticked`}
          >
            <CheckRow
              options={scope.booths.map((booth) => booth.id)}
              labelOf={(id) => scope.booths.find((booth) => booth.id === id)?.name ?? ''}
              chosen={booths}
              onChange={setBooths}
            />
          </Group>
        )}

        <Group
          title="Sentiment"
          hidden={Boolean(hidden['g:sentiment'])}
          onToggle={() => toggle('g:sentiment')}
          onAll={() => setSlices(ALL_SLICES)}
          onNone={() => setSlices([])}
          summary={slices.map((slice) => sliceLabel[slice]).join(', ') || 'none ticked'}
        >
          <CheckRow
            options={ALL_SLICES}
            labelOf={(slice) =>
              live
                ? `${sliceLabel[slice]} (${sliceValue(live.totals, slice).toLocaleString('en-IN')})`
                : sliceLabel[slice]
            }
            chosen={slices}
            onChange={setSlices}
          />
        </Group>

        <Group
          title="Confidence"
          note="Applies to recorded voters"
          hidden={Boolean(hidden['g:confidence'])}
          onToggle={() => toggle('g:confidence')}
          onAll={() => setConfidence(ALL_CONFIDENCE)}
          onNone={() => setConfidence([])}
          summary={confidence.map((level) => confidenceLabel[level]).join(', ') || 'none ticked'}
        >
          <CheckRow
            options={ALL_CONFIDENCE}
            labelOf={(level) => confidenceLabel[level]}
            chosen={confidence}
            onChange={setConfidence}
          />
        </Group>

        {scope && scope.wards.length > 0 && (
          <Group
            title="Ward no."
            hidden={Boolean(hidden['g:ward'])}
            onToggle={() => toggle('g:ward')}
            onAll={() => setWards(scope.wards)}
            onNone={() => setWards([])}
            summary={`${wards.length} of ${scope.wards.length} ward(s) ticked`}
          >
            <CheckRow options={scope.wards} labelOf={(ward) => String(ward)} chosen={wards} onChange={setWards} />
          </Group>
        )}

        <Group
          title="Caste"
          note="Sensitive data"
          hidden={Boolean(hidden['g:caste'])}
          onToggle={() => toggle('g:caste')}
          onAll={() => setCastes(ALL_CASTES)}
          onNone={() => setCastes([])}
          summary={
            castes
              .map((value) => CASTE_CHOICES.find((choice) => choice.value === value)?.label ?? value)
              .join(', ') || 'none ticked'
          }
        >
          <CheckRow
            options={ALL_CASTES}
            labelOf={(value) => CASTE_CHOICES.find((choice) => choice.value === value)?.label ?? value}
            chosen={castes}
            onChange={setCastes}
          />
        </Group>
      </Section>

      <div style={{ display: 'flex', alignItems: 'center', gap: 10, flexWrap: 'wrap', marginBottom: 12 }}>
        <Button
          tone="accent"
          icon={BarChart3}
          disabled={missing.length > 0 || !live}
          loading={report.loading && !live}
          onClick={() =>
            live && setDrawn({ report: live, slices, key: choiceKey, at: new Date().toISOString() })
          }
        >
          Draw report
        </Button>
        {missing.length > 0 && (
          <span style={{ fontFamily: fonts.sans, fontSize: 12, color: colors.marigoldDeep }}>
            Tick at least {missing.join(' and ')} to continue.
          </span>
        )}
      </div>

      {report.error && <ErrorNote message={report.error} />}

      {drawn && (
        <Section
          title="Report"
          summary={`Drawn ${formatDate(drawn.at)}`}
          hidden={Boolean(hidden.report)}
          onToggle={() => toggle('report')}
        >
          <div style={{ opacity: stale ? 0.45 : 1 }}>
            {stale && (
              <p style={{ fontFamily: fonts.sans, fontSize: 12, color: colors.marigoldDeep, margin: '0 0 10px' }}>
                Your choices changed. Draw the report again.
              </p>
            )}

            <div style={{ display: 'flex', gap: 8, flexWrap: 'wrap' }}>
              {drawn.slices.map((slice) => (
                <StatCard
                  key={slice}
                  label={sliceLabel[slice]}
                  value={sliceValue(drawn.report.totals, slice)}
                  hint={percent(
                    sliceValue(drawn.report.totals, slice),
                    drawn.slices.reduce((sum, each) => sum + sliceValue(drawn.report.totals, each), 0),
                  )}
                />
              ))}
            </div>

            {drawn.slices.includes('POSITIVE') && drawn.slices.includes('NEGATIVE') && (
              <Verdict report={drawn.report} />
            )}

            <ChartCard title="Booth by booth" style={{ marginTop: 12 }}>
              <BoothStack report={drawn.report} slices={drawn.slices} />
              <SliceLegend slices={drawn.slices} />
            </ChartCard>

            <ChartCard title="Your booths together" style={{ marginTop: 12 }}>
              <TotalsPie report={drawn.report} slices={drawn.slices} />
              <SliceLegend slices={drawn.slices} />
            </ChartCard>

            {/* AG-9/AG-10: the raw rows behind the figures, and only for an
                agent whose admin has switched downloads on. The server checks
                both switches again. */}
            <div style={{ marginTop: 14, paddingTop: 12, borderTop: `1px solid ${colors.line}` }}>
              <p style={{ fontFamily: fonts.sans, fontSize: 12, fontWeight: 600, color: colors.ink, margin: 0 }}>
                Download
              </p>
              {user?.downloadEnabled ? (
                <>
                  <Button
                    small
                    icon={Download}
                    loading={downloading}
                    disabled={stale || voterCount === 0}
                    onClick={() => void downloadVoters(drawn)}
                    style={{ marginTop: 8 }}
                  >
                    Voter details ({voterCount.toLocaleString('en-IN')})
                  </Button>
                  <p style={{ fontFamily: fonts.sans, fontSize: 11, color: colors.muted, margin: '6px 0 0' }}>
                    Name, relation, age, gender, house no. and the sentiment mark, for the voters counted above.
                    Your name and the time are printed on every page. Do not share it.
                  </p>
                  {saved && (
                    <div
                      style={{
                        display: 'flex',
                        alignItems: 'center',
                        gap: 8,
                        flexWrap: 'wrap',
                        marginTop: 10,
                      }}
                    >
                      <span
                        style={{
                          display: 'inline-flex',
                          alignItems: 'center',
                          gap: 6,
                          fontFamily: fonts.sans,
                          fontSize: 12,
                          color: colors.positive,
                        }}
                      >
                        <CheckCircle2 size={14} /> {saved.uri ? 'Saved to Downloads' : 'Downloaded'}
                      </span>
                      <span style={{ fontFamily: fonts.mono, fontSize: 11, color: colors.inkSoft }}>
                        {saved.fileName}
                      </span>
                      {canOpenAfterSaving() && saved.uri && (
                        <Button tone="accent" small onClick={() => openSavedFile(saved.uri)}>
                          Open
                        </Button>
                      )}
                    </div>
                  )}
                </>
              ) : (
                <p style={{ fontFamily: fonts.sans, fontSize: 12, color: colors.muted, margin: '8px 0 0' }}>
                  Voter-details download is turned off for you.
                </p>
              )}
            </div>
          </div>
        </Section>
      )}

      <div style={{ height: 16 }} />
    </div>
  );
}

/** AG-5, the same rule as the admin's verdict (FR-A15): positive share minus negative share. */
function Verdict({ report }: { report: AgentReport }) {
  const recorded = report.totals.positive + report.totals.neutral + report.totals.negative;
  if (recorded === 0) {
    return (
      <p style={{ fontFamily: fonts.sans, fontSize: 12, color: colors.muted, margin: '10px 0 0' }}>
        Nothing recorded yet in these booths.
      </p>
    );
  }
  const lead = ((report.totals.positive - report.totals.negative) / recorded) * 100;
  const positive = lead >= 0;
  return (
    <p
      style={{
        fontFamily: fonts.sans,
        fontSize: 13,
        fontWeight: 500,
        color: positive ? colors.positive : colors.negative,
        margin: '12px 0 0',
      }}
    >
      {positive ? '👍 Positive' : '👎 Negative'} · {Math.abs(lead).toFixed(1)} point lead across your booths
    </p>
  );
}
