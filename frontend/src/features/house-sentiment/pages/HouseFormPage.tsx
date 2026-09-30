import { useEffect, useState } from 'react';
import { useNavigate, useSearchParams } from 'react-router-dom';
import { Save } from 'lucide-react';
import { Button, ErrorNote, Field, SegmentedControl, Select, Spinner, useToast } from '@/shared/components';
import type { Segment } from '@/shared/components';
import { PageHeader } from '@/shared/layouts/PageHeader';
import { useAsync } from '@/shared/hooks/useAsync';
import { agentVoterApi } from '@/features/agent-voter-list/services/agentVoterApi';
import { wardOptions } from '@/features/voter-lists/components/VoterFormModal';
import { colors, fonts } from '@/shared/theme';
import type { ConfidenceLevel } from '@/shared/types';
import { houseSentimentApi } from '../services/houseSentimentApi';

const confidenceSegments: Segment<ConfidenceLevel>[] = [
  { value: 'HIGH', label: 'High' },
  { value: 'MEDIUM', label: 'Medium' },
  { value: 'LOW', label: 'Low' },
];

/** The count boxes carry the sentiment colour, as on the named-voter screen. */
function CountBox({
  label,
  color,
  wash,
  value,
  onChange,
}: {
  label: string;
  color: string;
  wash: string;
  value: string;
  onChange: (value: string) => void;
}) {
  return (
    <div style={{ flex: 1 }}>
      <label style={{ display: 'block', fontFamily: fonts.sans, fontSize: 12, color, marginBottom: 4 }}>{label}</label>
      <input
        inputMode="numeric"
        placeholder="0"
        value={value}
        onChange={(e) => onChange(e.target.value.replace(/[^0-9]/g, ''))}
        style={{
          width: '100%',
          boxSizing: 'border-box',
          fontFamily: fonts.sans,
          fontSize: 14,
          padding: '9px 12px',
          borderRadius: 6,
          border: `1px solid ${color}`,
          background: wash,
          color: colors.ink,
          outline: 'none',
        }}
      />
    </div>
  );
}

/**
 * FR-U12: add a house, or correct the house already recorded under that number.
 * Nothing here names an individual — the one house name stands for the whole
 * household, and no age, gender or EPIC no. is collected.
 */
export function HouseFormPage() {
  const navigate = useNavigate();
  const { notify } = useToast();
  const [params] = useSearchParams();
  const boothId = params.get('boothId') ?? '';
  const candidateId = params.get('candidateId') ?? '';
  const editingHouseNo = params.get('houseNo');

  const booths = useAsync(() => agentVoterApi.booths(), []);
  const existing = useAsync(
    () => (boothId && candidateId && editingHouseNo ? houseSentimentApi.list(boothId, candidateId) : Promise.resolve(null)),
    [boothId, candidateId, editingHouseNo],
  );

  const [houseName, setHouseName] = useState('');
  const [houseNo, setHouseNo] = useState(editingHouseNo ?? '');
  const [headcount, setHeadcount] = useState('');
  const [residentialCount, setResidentialCount] = useState('');
  const [wardNo, setWardNo] = useState('1');
  const [positive, setPositive] = useState('');
  const [neutral, setNeutral] = useState('');
  const [negative, setNegative] = useState('');
  const [confidence, setConfidence] = useState<ConfidenceLevel | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  // Editing loads the booth's list and picks out the house being corrected.
  useEffect(() => {
    if (!editingHouseNo) return;
    const house = (existing.data ?? []).find((h) => h.houseNo.toLowerCase() === editingHouseNo.toLowerCase());
    if (!house) return;
    setHouseName(house.houseName);
    setHouseNo(house.houseNo);
    setHeadcount(String(house.headcount));
    setResidentialCount(String(house.residentialCount));
    setWardNo(house.wardNo ? String(house.wardNo) : '1');
    setPositive(String(house.positiveCount));
    setNeutral(String(house.neutralCount));
    setNegative(String(house.negativeCount));
    setConfidence(house.confidence);
  }, [existing.data, editingHouseNo]);

  const grant = (booths.data ?? []).find((b) => b.boothId === boothId && b.candidateId === candidateId);
  const candidateName = grant?.candidateName ?? 'the candidate';
  const num = (value: string) => (value.trim() === '' ? 0 : Number(value));
  const split = num(positive) + num(neutral) + num(negative);
  const listLink = `/agent/houses?boothId=${boothId}&candidateId=${candidateId}`;

  async function save() {
    if (!houseName.trim()) return setError('Enter the house name');
    if (!houseNo.trim()) return setError('Enter the house no.');
    if (num(headcount) < 1) return setError('Enter how many people are at the house');
    if (num(residentialCount) < 1) return setError('Enter how many of them are residential');
    if (num(residentialCount) > num(headcount)) return setError('A house cannot have more residents than people');
    if (!confidence) return setError('Pick how confident you are');
    // The split covers the residents only — non-residents are not read for sentiment.
    if (split !== num(residentialCount)) {
      return setError(
        `Positive, neutral and negative add up to ${split}, but the house has ${num(residentialCount)} resident(s)`,
      );
    }

    setBusy(true);
    setError(null);
    try {
      await houseSentimentApi.record({
        boothId,
        candidateId,
        houseNo: houseNo.trim(),
        houseName: houseName.trim(),
        wardNo: Number(wardNo),
        headcount: num(headcount),
        residentialCount: num(residentialCount),
        positiveCount: num(positive),
        neutralCount: num(neutral),
        negativeCount: num(negative),
        confidence,
      });
      notify(editingHouseNo ? 'House updated' : 'House saved');
      navigate(listLink);
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Could not save this house');
    } finally {
      setBusy(false);
    }
    return undefined;
  }

  if (existing.loading) return <Spinner />;

  return (
    <div style={{ padding: '16px 20px 0' }}>
      <PageHeader
        title={editingHouseNo ? 'Edit house' : 'Add a house'}
        subtitle="House name only — no individual voter names, ages, or genders are collected."
        size={18}
      />

      {error && <ErrorNote message={error} />}

      <Field label="House name" placeholder="e.g. Bijay Kumar Behera" value={houseName} onChange={setHouseName} />

      <div style={{ display: 'flex', gap: 8 }}>
        <Field
          label="House no."
          placeholder="e.g. 42"
          value={houseNo}
          onChange={setHouseNo}
          disabled={Boolean(editingHouseNo)}
          hint={editingHouseNo ? 'Recorded house' : undefined}
          style={{ flex: 1 }}
        />
        <Field label="No. of people" placeholder="e.g. 4" value={headcount} onChange={(v) => setHeadcount(v.replace(/[^0-9]/g, ''))} style={{ flex: 1 }} />
        <Field
          label="No. of resid."
          placeholder="e.g. 4"
          value={residentialCount}
          onChange={(v) => setResidentialCount(v.replace(/[^0-9]/g, ''))}
          style={{ flex: 1 }}
        />
      </div>

      <Select label="Ward no." options={wardOptions} value={wardNo} onChange={setWardNo} />

      <p style={{ fontFamily: fonts.sans, fontSize: 12, color: colors.inkSoft, margin: '10px 0 6px' }}>
        Sentiment breakdown toward {candidateName} — should add up to the no. of resid. above
      </p>
      <div style={{ display: 'flex', gap: 8, marginBottom: 4 }}>
        <CountBox label="Positive" color={colors.positive} wash="#E7F0EA" value={positive} onChange={setPositive} />
        <CountBox label="Neutral" color={colors.marigoldDeep} wash="#F5E9D5" value={neutral} onChange={setNeutral} />
        <CountBox label="Negative" color={colors.negative} wash="#F6E7E2" value={negative} onChange={setNegative} />
      </div>
      <p
        style={{
          fontFamily: fonts.sans,
          fontSize: 11,
          color: split === num(residentialCount) && split > 0 ? colors.muted : colors.marigoldDeep,
          margin: '0 0 10px',
        }}
      >
        {split} of {num(residentialCount) || '—'} residents accounted for
      </p>

      <p style={{ fontFamily: fonts.sans, fontSize: 12, color: colors.inkSoft, margin: '0 0 6px' }}>Confidence</p>
      <div style={{ marginBottom: 16 }}>
        <SegmentedControl segments={confidenceSegments} value={confidence} onChange={setConfidence} pill />
      </div>

      <div style={{ display: 'flex', gap: 8, paddingBottom: 16 }}>
        <Button tone="accent" icon={Save} loading={busy} onClick={() => void save()}>
          Save entry
        </Button>
        <Button tone="ghost" onClick={() => navigate(listLink)}>
          Cancel
        </Button>
      </div>
    </div>
  );
}
