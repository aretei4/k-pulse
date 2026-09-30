import { useEffect, useState } from 'react';
import { useNavigate, useSearchParams } from 'react-router-dom';
import { Send } from 'lucide-react';
import { Button, ErrorNote, Field, InfoNote, Select, Spinner, useToast } from '@/shared/components';
import { PageHeader } from '@/shared/layouts/PageHeader';
import { useAsync } from '@/shared/hooks/useAsync';
import { wardOptions } from '@/features/voter-lists/components/VoterFormModal';
import { colors, fonts } from '@/shared/theme';
import type { Gender, VoterChangeType } from '@/shared/types';
import { agentVoterApi } from '../services/agentVoterApi';

const typeOptions = [
  { value: 'ADD', label: 'Add voter' },
  { value: 'EDIT', label: 'Edit voter' },
  { value: 'DELETE', label: 'Delete voter' },
];

const genderOptions = [
  { value: 'F', label: 'F' },
  { value: 'M', label: 'M' },
  { value: 'OTHER', label: 'Other' },
];

/**
 * FR-U11: agents propose roll changes; nothing lands on the live list until an
 * admin approves it on the Change requests screen.
 */
export function VoterChangePage() {
  const navigate = useNavigate();
  const { notify } = useToast();
  const [params] = useSearchParams();
  const boothId = params.get('boothId') ?? '';
  const voterId = params.get('voterId');
  const [type, setType] = useState<VoterChangeType>((params.get('type') as VoterChangeType) ?? 'ADD');

  const existing = useAsync(() => (voterId ? agentVoterApi.voter(voterId) : Promise.resolve(null)), [voterId]);

  const [epicNo, setEpicNo] = useState('');
  const [name, setName] = useState('');
  const [relation, setRelation] = useState('');
  const [houseNo, setHouseNo] = useState('');
  const [age, setAge] = useState('');
  const [gender, setGender] = useState<Gender>('F');
  const [wardNo, setWardNo] = useState('1');
  const [reason, setReason] = useState('');
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  useEffect(() => {
    const voter = existing.data?.voter;
    if (!voter) return;
    setEpicNo(voter.epicNo);
    setName(voter.name);
    setRelation(voter.relation);
    setHouseNo(voter.houseNo ?? '');
    setAge(String(voter.age));
    setGender(voter.gender);
    setWardNo(voter.wardNo ? String(voter.wardNo) : '1');
  }, [existing.data]);

  async function submit() {
    if (!boothId) return setError('Open this from a booth voter list');
    if (!reason.trim()) return setError('Tell the admin why this change is needed');

    if (type !== 'DELETE') {
      if (!name.trim()) return setError('Enter the voter name');
      if (!relation.trim()) return setError('Enter the relation, e.g. W/O Ranjit Nayak');
      const parsedAge = Number(age);
      if (!Number.isFinite(parsedAge) || parsedAge < 18 || parsedAge > 120) {
        return setError('Age must be between 18 and 120');
      }
    }

    setBusy(true);
    setError(null);
    try {
      await agentVoterApi.proposeChange({
        type,
        voterId: type === 'ADD' ? null : voterId,
        boothId,
        payload:
          type === 'DELETE'
            ? { reason: reason.trim() }
            : {
                epicNo: epicNo.trim() || undefined,
                name: name.trim(),
                relation: relation.trim(),
                houseNo: houseNo.trim() || undefined,
                age: Number(age),
                gender,
                wardNo: Number(wardNo),
                reason: reason.trim(),
              },
      });
      notify('Sent to the admin for approval');
      navigate(-1);
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Could not submit the proposal');
    } finally {
      setBusy(false);
    }
    return undefined;
  }

  if (existing.loading) return <Spinner />;

  const target = existing.data?.voter;

  return (
    <div style={{ padding: '16px 20px 0' }}>
      <PageHeader
        title="Propose voter change"
        subtitle="Sent to the admin for approval — the voter list only updates once approved."
        size={18}
      />

      {error && <ErrorNote message={error} />}

      <Select label="Change type" options={typeOptions} value={type} onChange={(v) => setType(v as VoterChangeType)} />

      {type === 'DELETE' ? (
        <div
          style={{
            border: `1px solid ${colors.line}`,
            borderRadius: 8,
            background: colors.card,
            padding: 12,
            marginBottom: 12,
          }}
        >
          <p style={{ fontFamily: fonts.sans, fontSize: 13, fontWeight: 500, color: colors.ink, margin: 0 }}>
            {target?.name ?? 'Voter'}
          </p>
          <p style={{ fontFamily: fonts.sans, fontSize: 11, color: colors.muted, margin: '3px 0 0' }}>
            {target ? `${target.relation} · ${target.age} · ${target.gender} · ${target.epicNo}` : ''}
          </p>
        </div>
      ) : (
        <>
          <Field label="EPIC no." placeholder="ODA1234567" value={epicNo} onChange={setEpicNo} />
          <Field label="Full name" placeholder="Sabitri Nayak" value={name} onChange={setName} />
          <Field label="Relation (S/O · W/O · D/O)" placeholder="W/O Ranjit Nayak" value={relation} onChange={setRelation} />
          <div style={{ display: 'flex', gap: 8 }}>
            <Field label="House no." placeholder="87" value={houseNo} onChange={setHouseNo} style={{ flex: 1 }} />
            <Field label="Age" placeholder="39" type="number" value={age} onChange={setAge} style={{ flex: 1 }} />
          </div>
          <div style={{ display: 'flex', gap: 8 }}>
            <Select
              label="Gender"
              options={genderOptions}
              value={gender}
              onChange={(v) => setGender(v as Gender)}
              style={{ flex: 1 }}
            />
            <Select label="Ward no." options={wardOptions} value={wardNo} onChange={setWardNo} style={{ flex: 1 }} />
          </div>
        </>
      )}

      <Field
        label="Reason"
        placeholder="Age corrected against the EPIC card"
        value={reason}
        onChange={setReason}
      />

      <div style={{ display: 'flex', gap: 8, marginTop: 4 }}>
        <Button tone="accent" icon={Send} loading={busy} onClick={() => void submit()}>
          Submit for approval
        </Button>
        <Button tone="ghost" onClick={() => navigate(-1)}>
          Cancel
        </Button>
      </div>

      <div style={{ marginTop: 14, paddingBottom: 16 }}>
        <InfoNote>
          Status: <strong style={{ color: colors.marigoldDeep }}>Pending admin approval</strong> once submitted. You can
          track it on the Status tab.
        </InfoNote>
      </div>
    </div>
  );
}
