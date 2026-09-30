import { useEffect, useMemo, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { ClipboardList } from 'lucide-react';
import { Button, ErrorNote, InfoNote, Select, Spinner, useToast } from '@/shared/components';
import { PageHeader } from '@/shared/layouts/PageHeader';
import { useAsync } from '@/shared/hooks/useAsync';
import { useUnitTree } from '@/shared/hooks/useUnitTree';
import { referenceApi } from '@/shared/services/referenceApi';
import { colors, fonts } from '@/shared/theme';
import type { Unit, UnitLevel } from '@/shared/types';
import { agentAccessApi } from '../services/agentAccessApi';

/** What an agent can ask for once a district is chosen, widest first. */
const levelOptions = [
  { value: 'DISTRICT', label: 'The whole district' },
  { value: 'BLOCK', label: 'Block' },
  { value: 'PANCHAYAT', label: 'Panchayat' },
  { value: 'BOOTH', label: 'Booth' },
];

export function RequestAccessPage() {
  const navigate = useNavigate();
  const { notify } = useToast();
  const tree = useUnitTree();
  const candidates = useAsync(() => referenceApi.candidates(), []);

  const [districtId, setDistrictId] = useState('');
  const [level, setLevel] = useState<UnitLevel>('BOOTH');
  const [unitId, setUnitId] = useState('');
  const [candidateId, setCandidateId] = useState('');
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  const districts = useMemo(() => tree.byLevel('DISTRICT'), [tree]);

  // With one district there is nothing to choose, so choose it for them.
  useEffect(() => {
    if (!districtId && districts.length === 1) setDistrictId(districts[0].id);
  }, [districts, districtId]);

  /** Walks up the parents, so only units inside the chosen district are offered. */
  const unitChoices = useMemo(() => {
    if (!districtId || level === 'DISTRICT') return [];
    const isUnderDistrict = (unit: Unit) => {
      let current: Unit | undefined = unit;
      while (current) {
        if (current.id === districtId) return true;
        current = tree.find(current.parentId);
      }
      return false;
    };
    return tree.byLevel(level).filter(isUnderDistrict);
  }, [tree, districtId, level]);

  // Asking for the whole district means the district itself is the unit.
  const selectedUnitId = level === 'DISTRICT' ? districtId : unitId;

  async function submit() {
    if (!districtId) return setError('Pick the district you want to work in');
    if (!selectedUnitId) return setError('Pick the unit you want to work in');
    if (!candidateId) return setError('Pick the candidate you will record sentiment for');

    setBusy(true);
    setError(null);
    try {
      await agentAccessApi.create({ unitId: selectedUnitId, candidateId });
      notify('Request submitted — an admin will review it');
      navigate('/agent/status', { replace: true });
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Could not submit the request');
    } finally {
      setBusy(false);
    }
    return undefined;
  }

  if (tree.loading) return <Spinner />;

  return (
    <div style={{ padding: '16px 20px 0' }}>
      <PageHeader
        title="Request access"
        subtitle="Pick your district, then how much of it you need and the candidate you'll record sentiment for — one pairing per request."
        size={18}
      />

      {error && <ErrorNote message={error} />}

      <Select
        label="District"
        options={districts.map((d) => ({ value: d.id, label: d.name }))}
        value={districtId}
        onChange={(v) => {
          setDistrictId(v);
          setUnitId('');
        }}
        placeholder="Select a district"
      />

      <Select
        label="Unit level"
        options={levelOptions}
        value={level}
        onChange={(v) => {
          setLevel(v as UnitLevel);
          setUnitId('');
        }}
        disabled={!districtId}
      />

      {level !== 'DISTRICT' && (
        <>
          <Select
            label={levelOptions.find((o) => o.value === level)?.label ?? 'Unit'}
            options={unitChoices.map((u) => ({ value: u.id, label: u.path }))}
            value={unitId}
            onChange={setUnitId}
            placeholder={districtId ? 'Select a unit' : 'Pick a district first'}
            disabled={!districtId}
            style={unitChoices.length === 0 && districtId ? { marginBottom: 4 } : undefined}
          />
          {districtId && unitChoices.length === 0 && (
            <p style={{ fontFamily: fonts.sans, fontSize: 11, color: colors.marigoldDeep, margin: '0 0 12px' }}>
              This district has nothing at that level yet — try a wider one.
            </p>
          )}
        </>
      )}

      <Select
        label="Candidate"
        options={(candidates.data ?? []).map((c) => ({ value: c.id, label: c.name }))}
        value={candidateId}
        onChange={setCandidateId}
        placeholder="Select a candidate"
      />

      <Button icon={ClipboardList} loading={busy} full onClick={() => void submit()}>
        Submit request
      </Button>

      <div style={{ marginTop: 12, paddingBottom: 16 }}>
        <InfoNote>
          Access lasts 6 months by default, or until the admin ends it. Approval at a higher level — a panchayat, say —
          unlocks every booth beneath it.
        </InfoNote>
      </div>
    </div>
  );
}
