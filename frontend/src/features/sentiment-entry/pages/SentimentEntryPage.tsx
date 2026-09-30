import { useEffect, useState } from 'react';
import { useNavigate, useParams, useSearchParams } from 'react-router-dom';
import { ChevronLeft, Minus, Save, ThumbsDown, ThumbsUp } from 'lucide-react';
import { Button, Card, ErrorNote, SegmentedControl, Select, Spinner, useToast } from '@/shared/components';
import type { Segment } from '@/shared/components';
import { PageHeader } from '@/shared/layouts/PageHeader';
import { useAsync } from '@/shared/hooks/useAsync';
import { wardOptions } from '@/features/voter-lists/components/VoterFormModal';
import { colors, fonts } from '@/shared/theme';
import { formatDate } from '@/shared/utils/format';
import type { ConfidenceLevel, SentimentValue } from '@/shared/types';
import { sentimentApi } from '../services/sentimentApi';
import { agentVoterApi } from '@/features/agent-voter-list/services/agentVoterApi';

const sentimentSegments: Segment<SentimentValue>[] = [
  { value: 'POSITIVE', label: 'Positive', icon: ThumbsUp, activeColor: colors.positive, activeWash: colors.positiveWash },
  { value: 'NEUTRAL', label: 'Neutral', icon: Minus, activeColor: colors.neutral, activeWash: colors.neutralWash },
  { value: 'NEGATIVE', label: 'Negative', icon: ThumbsDown, activeColor: colors.negative, activeWash: colors.negativeWash },
];

const confidenceSegments: Segment<ConfidenceLevel>[] = [
  { value: 'HIGH', label: 'High' },
  { value: 'MEDIUM', label: 'Medium' },
  { value: 'LOW', label: 'Low' },
];

const residenceSegments: Segment<'YES' | 'NO'>[] = [
  { value: 'YES', label: 'Residence', activeColor: colors.positive, activeWash: colors.positiveWash },
  { value: 'NO', label: 'No residence' },
];

export function SentimentEntryPage() {
  const navigate = useNavigate();
  const { notify } = useToast();
  const { voterId = '' } = useParams();
  const [params] = useSearchParams();
  const candidateId = params.get('candidateId') ?? '';

  const record = useAsync(() => agentVoterApi.voter(voterId), [voterId]);
  const booths = useAsync(() => agentVoterApi.booths(), []);

  const [sentiment, setSentiment] = useState<SentimentValue | null>(null);
  const [confidence, setConfidence] = useState<ConfidenceLevel | null>(null);
  const [residence, setResidence] = useState<'YES' | 'NO'>('YES');
  const [wardNo, setWardNo] = useState('1');
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  useEffect(() => {
    const data = record.data;
    if (!data) return;
    setWardNo(data.voter.wardNo ? String(data.voter.wardNo) : '1');
    if (data.entry) {
      setSentiment(data.entry.sentiment);
      setConfidence(data.entry.confidence);
      setResidence(data.entry.resident ? 'YES' : 'NO');
    }
  }, [record.data]);

  const voter = record.data?.voter;
  const entry = record.data?.entry;
  const grant = (booths.data ?? []).find((b) => b.boothId === voter?.boothId);
  // The candidate comes from the grant this booth was opened under (FR-U7) —
  // an agent never picks it per entry.
  const resolvedCandidateId = candidateId || grant?.candidateId || '';
  const candidateName = grant?.candidateName ?? 'the candidate';

  async function save() {
    if (!sentiment) return setError('Pick a sentiment');
    if (!confidence) return setError('Pick how confident you are');

    setBusy(true);
    setError(null);
    try {
      await sentimentApi.record(voterId, {
        candidateId: resolvedCandidateId,
        sentiment,
        confidence,
        resident: residence === 'YES',
        wardNo: Number(wardNo),
      });
      notify('Entry saved');
      navigate(-1);
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Could not save the entry');
    } finally {
      setBusy(false);
    }
    return undefined;
  }

  if (record.loading) return <Spinner />;
  if (record.error) {
    return (
      <div style={{ padding: '16px 20px' }}>
        <ErrorNote message={record.error} />
      </div>
    );
  }

  return (
    <div style={{ padding: '12px 20px 0' }}>
      <button
        onClick={() => navigate(-1)}
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
        <ChevronLeft size={14} /> Back to voter list
      </button>

      <PageHeader title="Your assessment" size={18} />

      {error && <ErrorNote message={error} />}

      <Card padding={14} style={{ marginBottom: 14 }}>
        <p style={{ fontFamily: fonts.sans, fontSize: 14, fontWeight: 500, color: colors.ink, margin: 0 }}>{voter?.name}</p>
        <p style={{ fontFamily: fonts.sans, fontSize: 11, color: colors.muted, margin: '3px 0 0' }}>
          {voter?.relation} · {voter?.age} · {voter?.gender} ·{' '}
          {voter?.houseNo ? `House ${voter.houseNo}` : 'No house no.'}
        </p>
        {entry && (
          <p style={{ fontFamily: fonts.sans, fontSize: 11, color: colors.marigoldDeep, margin: '6px 0 0' }}>
            Already recorded {formatDate(entry.recordedAt)} — saving overwrites it.
          </p>
        )}
      </Card>

      <Select label="Ward no." options={wardOptions} value={wardNo} onChange={setWardNo} />

      <p style={{ fontFamily: fonts.sans, fontSize: 12, color: colors.inkSoft, margin: '6px 0 6px' }}>Residence status</p>
      <div style={{ marginBottom: 14 }}>
        <SegmentedControl segments={residenceSegments} value={residence} onChange={setResidence} />
      </div>

      {/* Names what this field is: the agent's judgement, not a statement the voter made. */}
      <p style={{ fontFamily: fonts.sans, fontSize: 12, color: colors.marigoldDeep, margin: '0 0 8px', lineHeight: 1.5 }}>
        Your read of this voter's leaning toward {candidateName} — your own assessment, not something recorded from the
        voter directly
      </p>
      <div style={{ marginBottom: 14 }}>
        <SegmentedControl segments={sentimentSegments} value={sentiment} onChange={setSentiment} stacked />
      </div>

      <p style={{ fontFamily: fonts.sans, fontSize: 12, color: colors.inkSoft, margin: '0 0 6px' }}>Confidence</p>
      <div style={{ marginBottom: 16 }}>
        <SegmentedControl segments={confidenceSegments} value={confidence} onChange={setConfidence} pill />
      </div>

      <Button tone="accent" icon={Save} loading={busy} full onClick={() => void save()}>
        Save entry
      </Button>
      <p style={{ fontFamily: fonts.sans, fontSize: 11, color: colors.muted, margin: '8px 0 16px' }}>
        You can edit this entry any time from the voter list.
      </p>
    </div>
  );
}
