import { useEffect, useState } from 'react';
import { Button, Field, Modal, Select } from '@/shared/components';
import type { Gender, Voter, VoterChangePayload } from '@/shared/types';

const genderOptions = [
  { value: 'M', label: 'M' },
  { value: 'F', label: 'F' },
  { value: 'OTHER', label: 'Other' },
];

export const wardOptions = Array.from({ length: 20 }, (_, i) => ({ value: String(i + 1), label: `Ward ${i + 1}` }));

export interface VoterFormValues extends VoterChangePayload {
  epicNo: string;
  name: string;
  relation: string;
}

/** Shared add/edit voter form — the admin writes directly, the agent proposes. */
export function VoterFormModal({
  open,
  title,
  subtitle,
  voter,
  submitLabel,
  requireReason,
  busy,
  onClose,
  onSubmit,
}: {
  open: boolean;
  title: string;
  subtitle?: string;
  voter?: Voter | null;
  submitLabel: string;
  requireReason?: boolean;
  busy?: boolean;
  onClose: () => void;
  onSubmit: (values: VoterFormValues) => void;
}) {
  const [epicNo, setEpicNo] = useState('');
  const [name, setName] = useState('');
  const [relation, setRelation] = useState('');
  const [houseNo, setHouseNo] = useState('');
  const [age, setAge] = useState('');
  const [gender, setGender] = useState<Gender>('M');
  const [wardNo, setWardNo] = useState('1');
  const [reason, setReason] = useState('');
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    if (!open) return;
    setEpicNo(voter?.epicNo ?? '');
    setName(voter?.name ?? '');
    setRelation(voter?.relation ?? '');
    setHouseNo(voter?.houseNo ?? '');
    setAge(voter ? String(voter.age) : '');
    setGender(voter?.gender ?? 'M');
    setWardNo(voter?.wardNo ? String(voter.wardNo) : '1');
    setReason('');
    setError(null);
  }, [open, voter]);

  function submit() {
    if (!name.trim()) return setError('Enter the voter name');
    if (!relation.trim()) return setError('Enter the relation, e.g. W/O Ranjit Nayak');
    const parsedAge = Number(age);
    if (!Number.isFinite(parsedAge) || parsedAge < 18 || parsedAge > 120) return setError('Age must be between 18 and 120');
    if (requireReason && !reason.trim()) return setError('Say why this change is needed');

    onSubmit({
      epicNo: epicNo.trim(),
      name: name.trim(),
      relation: relation.trim(),
      houseNo: houseNo.trim() || undefined,
      age: parsedAge,
      gender,
      wardNo: Number(wardNo),
      reason: reason.trim() || undefined,
    });
    return undefined;
  }

  return (
    <Modal open={open} title={title} subtitle={subtitle} onClose={onClose}>
      {error && (
        <p style={{ fontSize: 12, color: '#B5482F', margin: '0 0 10px', fontFamily: "'IBM Plex Sans', sans-serif" }}>{error}</p>
      )}
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
      {requireReason && (
        <Field label="Reason for the change" placeholder="Age corrected against the EPIC card" value={reason} onChange={setReason} />
      )}
      <div style={{ display: 'flex', gap: 8, marginTop: 4 }}>
        <Button tone="accent" loading={busy} onClick={submit}>
          {submitLabel}
        </Button>
        <Button tone="ghost" onClick={onClose}>
          Cancel
        </Button>
      </div>
    </Modal>
  );
}
