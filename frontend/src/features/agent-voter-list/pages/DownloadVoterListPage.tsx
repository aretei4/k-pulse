import { useCallback, useEffect, useMemo, useState } from 'react';
import { useNavigate, useSearchParams } from 'react-router-dom';
import { CheckCircle2, Download, ShieldAlert } from 'lucide-react';
import { Button, Card, ErrorNote, Spinner, useToast } from '@/shared/components';
import { PageHeader } from '@/shared/layouts/PageHeader';
import { ApiError } from '@/lib/apiClient';
import { canOpenAfterSaving, openSavedFile, saveFile } from '@/lib/download';
import { colors, fonts } from '@/shared/theme';
import {
  SENTIMENT_FILTERS,
  voterExportApi,
  type SentimentFilter,
  type VoterExportSummary,
} from '../services/voterExportApi';

const CHIPS: Array<{ id: SentimentFilter; label: string; fg: string; bg: string }> = [
  { id: 'POSITIVE', label: 'Positive', fg: colors.positive, bg: colors.positiveWash },
  { id: 'NEUTRAL', label: 'Neutral', fg: colors.neutral, bg: colors.neutralWash },
  { id: 'NEGATIVE', label: 'Negative', fg: colors.negative, bg: colors.negativeWash },
  { id: 'NOT_RECORDED', label: 'Not recorded', fg: colors.muted, bg: colors.paper },
];

function countOf(summary: VoterExportSummary, id: SentimentFilter) {
  const { positive, neutral, negative, notRecorded } = summary.counts;
  return { POSITIVE: positive, NEUTRAL: neutral, NEGATIVE: negative, NOT_RECORDED: notRecorded }[id];
}

/** The sentiment as it reads beside a previewed name. */
function PreviewPill({ value }: { value: SentimentFilter }) {
  const chip = CHIPS.find((c) => c.id === value)!;
  const plain = value === 'NOT_RECORDED';
  return (
    <span
      style={{
        fontFamily: fonts.sans,
        fontSize: 11,
        fontWeight: plain ? 400 : 500,
        borderRadius: 20,
        padding: '2px 8px',
        whiteSpace: 'nowrap',
        color: chip.fg,
        background: plain ? 'transparent' : chip.bg,
        border: plain ? `1px solid ${colors.line}` : 'none',
      }}
    >
      {chip.label}
    </span>
  );
}

/**
 * FR-U13: a booth's voter list as a PDF to walk with.
 *
 * <p>Sentiment is the only filter, by design — the file is a copy of named
 * voters' data leaving K-Pulse, so what it may contain is kept narrow and
 * predictable rather than configurable.
 */
export function DownloadVoterListPage() {
  const [params] = useSearchParams();
  const navigate = useNavigate();
  const { notify } = useToast();
  const boothId = params.get('boothId') ?? '';

  const [chosen, setChosen] = useState<SentimentFilter[]>([...SENTIMENT_FILTERS]);
  const [summary, setSummary] = useState<VoterExportSummary | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);
  const [saved, setSaved] = useState<{ fileName: string; uri: string | null } | null>(null);

  // Declaration order, not tap order: the file name lists them this way too.
  const selection = useMemo(() => SENTIMENT_FILTERS.filter((id) => chosen.includes(id)), [chosen]);

  const load = useCallback(async () => {
    if (!boothId) {
      setError('Pick a booth on the Insights screen first');
      setLoading(false);
      return;
    }
    setLoading(true);
    try {
      setSummary(await voterExportApi.summary(boothId, selection));
      setError(null);
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Could not read this booth');
    } finally {
      setLoading(false);
    }
  }, [boothId, selection]);

  useEffect(() => {
    void load();
  }, [load]);

  function toggle(id: SentimentFilter) {
    setSaved(null);
    setChosen((current) => (current.includes(id) ? current.filter((c) => c !== id) : [...current, id]));
  }

  const everything = selection.length === SENTIMENT_FILTERS.length;
  const tooMany = summary ? summary.matching > summary.maxRows : false;
  const nothing = summary ? summary.matching === 0 : true;

  async function download() {
    if (!summary) return;
    setBusy(true);
    setError(null);
    try {
      const blob = await voterExportApi.pdf(boothId, selection);
      const uri = await saveFile(blob, summary.fileName);
      setSaved({ fileName: summary.fileName, uri });
      notify('Saved to Downloads');
    } catch (err) {
      // 422 is the server refusing a file too big to be useful (DL-10); its
      // message already says to narrow the selection.
      setError(err instanceof ApiError || err instanceof Error ? err.message : 'The file could not be built');
    } finally {
      setBusy(false);
    }
  }

  // An agent who cannot download at all gets the reason and a way back, not an
  // empty screen: the server is the only thing that decides this.
  if (error && !summary) {
    return (
      <div style={{ padding: '16px 20px 0' }}>
        <PageHeader title="Download voter list" size={18} />
        <ErrorNote message={error} />
        <Button tone="ghost" small onClick={() => navigate('/agent/insights')}>
          Back to insights
        </Button>
      </div>
    );
  }

  if (loading && !summary) return <Spinner />;
  if (!summary) return null;

  return (
    <div style={{ padding: '16px 20px 0' }}>
      <PageHeader
        title="Download voter list"
        subtitle={`${summary.boothName}${summary.blockName ? `, ${summary.blockName}` : ''} · candidate ${
          summary.candidateName
        } · ${summary.totalVoters} voters`}
        size={18}
      />

      {error && <ErrorNote message={error} />}

      <p style={{ fontFamily: fonts.sans, fontSize: 12, color: colors.inkSoft, margin: '0 0 6px' }}>Sentiment</p>

      <div style={{ display: 'flex', flexWrap: 'wrap', gap: 6, marginBottom: 8 }}>
        {CHIPS.map((chip) => {
          const on = chosen.includes(chip.id);
          return (
            <button
              key={chip.id}
              type="button"
              onClick={() => toggle(chip.id)}
              aria-pressed={on}
              style={{
                fontFamily: fonts.sans,
                fontSize: 12,
                fontWeight: 500,
                cursor: 'pointer',
                borderRadius: 20,
                padding: '6px 12px',
                color: on ? colors.card : chip.fg,
                background: on ? chip.fg : 'transparent',
                border: `1px solid ${on ? chip.fg : colors.line}`,
              }}
            >
              {chip.label} {countOf(summary, chip.id)}
            </button>
          );
        })}
      </div>

      {/* DL-4 asks for both; which one is offered is whichever would change something. */}
      <button
        type="button"
        onClick={() => {
          setSaved(null);
          setChosen(everything ? [] : [...SENTIMENT_FILTERS]);
        }}
        style={{
          background: 'none',
          border: 'none',
          padding: 0,
          marginBottom: 14,
          cursor: 'pointer',
          fontFamily: fonts.sans,
          fontSize: 12,
          color: colors.inkSoft,
          textDecoration: 'underline',
        }}
      >
        {everything ? 'Clear all' : 'Select all'}
      </button>

      <Card padding={14} style={{ marginBottom: 12 }}>
        <p style={{ fontFamily: fonts.sans, fontSize: 13, color: colors.ink, margin: 0 }}>
          <strong>{summary.matching}</strong> of {summary.totalVoters} voters match
          {summary.pages > 0 && ` · about ${summary.pages} page${summary.pages === 1 ? '' : 's'}`}
        </p>

        {summary.preview.length > 0 && (
          <div style={{ marginTop: 10 }}>
            {summary.preview.map((row) => (
              <div
                key={`${row.name}-${row.houseNo}`}
                style={{
                  display: 'flex',
                  alignItems: 'center',
                  justifyContent: 'space-between',
                  gap: 8,
                  padding: '5px 0',
                  borderTop: `1px solid ${colors.line}`,
                }}
              >
                <span style={{ fontFamily: fonts.sans, fontSize: 12, color: colors.ink }}>
                  {row.name}
                  {row.houseNo && <span style={{ color: colors.muted }}> · H{row.houseNo}</span>}
                </span>
                <PreviewPill value={row.sentiment} />
              </div>
            ))}
            {summary.matching > summary.preview.length && (
              <p style={{ fontFamily: fonts.sans, fontSize: 11, color: colors.muted, margin: '6px 0 0' }}>
                and {summary.matching - summary.preview.length} more in the file
              </p>
            )}
          </div>
        )}
      </Card>

      {/* DL-6: said plainly, because the agent is the one it is about. */}
      <div
        style={{
          display: 'flex',
          gap: 8,
          alignItems: 'flex-start',
          border: `1px solid ${colors.line}`,
          background: colors.marigoldWash,
          borderRadius: 8,
          padding: 10,
          marginBottom: 12,
        }}
      >
        <ShieldAlert size={14} color={colors.marigoldDeep} style={{ flexShrink: 0, marginTop: 1 }} />
        <p style={{ fontFamily: fonts.sans, fontSize: 11.5, color: colors.inkSoft, margin: 0, lineHeight: 1.5 }}>
          Your name and the time are printed on every page. This list is for your own fieldwork. Do not share.
        </p>
      </div>

      {saved ? (
        <Card padding={12} style={{ marginBottom: 16 }}>
          <span
            style={{
              display: 'inline-flex',
              alignItems: 'center',
              gap: 6,
              fontFamily: fonts.sans,
              fontSize: 13,
              color: colors.positive,
            }}
          >
            <CheckCircle2 size={14} /> Saved to Downloads
          </span>
          <p style={{ fontFamily: fonts.mono, fontSize: 11, color: colors.inkSoft, margin: '6px 0 10px' }}>
            {saved.fileName}
          </p>
          <div style={{ display: 'flex', gap: 8 }}>
            {canOpenAfterSaving() && (
              <Button tone="accent" small icon={Download} onClick={() => openSavedFile(saved.uri)}>
                Open
              </Button>
            )}
            <Button tone="ghost" small onClick={() => setSaved(null)}>
              Change filter
            </Button>
          </div>
        </Card>
      ) : (
        <div style={{ paddingBottom: 16 }}>
          <Button
            icon={Download}
            full
            loading={busy}
            disabled={selection.length === 0 || nothing || tooMany}
            onClick={() => void download()}
          >
            {busy ? 'Preparing PDF…' : 'Download PDF'}
          </Button>
          {selection.length === 0 && (
            <p style={{ fontFamily: fonts.sans, fontSize: 11, color: colors.muted, margin: '6px 0 0' }}>
              Choose at least one sentiment.
            </p>
          )}
          {tooMany && (
            <p style={{ fontFamily: fonts.sans, fontSize: 11, color: colors.negative, margin: '6px 0 0' }}>
              {summary.matching} voters is more than one file holds — narrow the sentiment selection.
            </p>
          )}
        </div>
      )}
    </div>
  );
}
