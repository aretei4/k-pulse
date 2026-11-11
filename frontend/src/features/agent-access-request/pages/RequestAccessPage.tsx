import { useEffect, useMemo, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { ClipboardList, MessageCircle } from 'lucide-react';
import { Button, ErrorNote, Field, InfoNote, Select, Spinner, useToast } from '@/shared/components';
import { PageHeader } from '@/shared/layouts/PageHeader';
import { useAsync } from '@/shared/hooks/useAsync';
import { useUnitTree } from '@/shared/hooks/useUnitTree';
import { referenceApi } from '@/shared/services/referenceApi';
import { SUPPORT_WHATSAPP_DISPLAY, whatsappLink } from '@/lib/support';
import { colors, fonts } from '@/shared/theme';
import { agentAccessApi } from '../services/agentAccessApi';

/**
 * An agent can only ask for a panchayat the master already holds, and only an
 * admin can add one, so a missing area is a dead end inside the app. This is
 * the way out of it: WhatsApp to the office, with the area already written out.
 */
function AskTheOffice({ missing, within }: { missing: string; within?: string }) {
  const draft = `K-Pulse: I need access to a ${missing}${within ? ` in ${within}` : ''}, but it is not in the app yet.`;
  return (
    <a
      href={whatsappLink(draft)}
      target="_blank"
      rel="noreferrer"
      style={{
        display: 'inline-flex',
        alignItems: 'center',
        gap: 6,
        fontFamily: fonts.sans,
        fontSize: 12,
        color: colors.marigoldDeep,
        textDecoration: 'none',
      }}
    >
      <MessageCircle size={13} />
      {missing.charAt(0).toUpperCase() + missing.slice(1)} not listed? Ask the office on WhatsApp
    </a>
  );
}

/** One typed name is one candidate however it is cased or spaced, as on the server. */
function sameName(a: string, b: string) {
  return a.trim().replace(/\s+/g, ' ').toLowerCase() === b.trim().replace(/\s+/g, ' ').toLowerCase();
}

export function RequestAccessPage() {
  const navigate = useNavigate();
  const { notify } = useToast();
  const tree = useUnitTree();
  const candidates = useAsync(() => referenceApi.candidates(), []);

  const [districtId, setDistrictId] = useState('');
  const [blockId, setBlockId] = useState('');
  const [panchayatId, setPanchayatId] = useState('');
  const [candidateName, setCandidateName] = useState('');
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  const districts = useMemo(() => tree.byLevel('DISTRICT'), [tree]);

  // With one district there is nothing to choose, so choose it for them.
  useEffect(() => {
    if (!districtId && districts.length === 1) setDistrictId(districts[0].id);
  }, [districts, districtId]);

  // Straight down the unit master, each level drawn from the one above it.
  // Nothing chosen above means nothing to offer here -- not the roots, which is
  // what childrenOf(null) returns.
  const blocks = districtId ? tree.childrenOf(districtId) : [];
  const panchayats = blockId ? tree.childrenOf(blockId) : [];

  /**
   * The panchayat is the grant. District and block only narrow the list down to
   * it, so the booths under it are never picked one by one -- approval at the
   * panchayat already opens every one of them.
   */
  const panchayat = tree.find(panchayatId);

  /** Whether this name is already on the list, so the agent can see it land on one candidate. */
  const known = (candidates.data ?? []).find((c) => sameName(c.name, candidateName));

  async function submit() {
    if (!districtId) return setError('Pick the district you want to work in');
    if (!blockId) return setError('Pick the block you want to work in');
    if (!panchayat) return setError('Pick the panchayat you want to work in');
    if (!candidateName.trim()) return setError("Enter the candidate's name");

    setBusy(true);
    setError(null);
    try {
      await agentAccessApi.create({ unitId: panchayat.id, candidateName: candidateName.trim() });
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
        subtitle="Pick the panchayat you'll canvass and name the candidate you'll record sentiment for — one pairing per request."
        size={18}
      />

      {error && <ErrorNote message={error} />}

      <Select
        label="District"
        options={districts.map((d) => ({ value: d.id, label: d.name }))}
        value={districtId}
        onChange={(v) => {
          setDistrictId(v);
          setBlockId('');
          setPanchayatId('');
        }}
        placeholder="Select a district"
      />

      {/* Each level appears only once the one above it has something to offer,
          and when it has nothing the way out is WhatsApp rather than a blank. */}
      {districtId && blocks.length === 0 && (
        <div style={{ marginBottom: 12 }}>
          <InfoNote>
            No blocks have been loaded for {tree.find(districtId)?.name} yet.
            <div style={{ marginTop: 6 }}>
              <AskTheOffice missing="block" within={tree.find(districtId)?.name} />
            </div>
          </InfoNote>
        </div>
      )}

      {blocks.length > 0 && (
        <Select
          label="Block"
          options={blocks.map((b) => ({ value: b.id, label: b.name }))}
          value={blockId}
          onChange={(v) => {
            setBlockId(v);
            setPanchayatId('');
          }}
          placeholder="Select a block"
        />
      )}

      {blockId && panchayats.length === 0 && (
        <div style={{ marginBottom: 12 }}>
          <InfoNote>
            No panchayats have been loaded for {tree.find(blockId)?.name} yet.
            <div style={{ marginTop: 6 }}>
              <AskTheOffice missing="panchayat" within={tree.find(blockId)?.name} />
            </div>
          </InfoNote>
        </div>
      )}

      {panchayats.length > 0 && (
        <Select
          label="Panchayat"
          options={panchayats.map((p) => ({ value: p.id, label: p.name }))}
          value={panchayatId}
          onChange={setPanchayatId}
          placeholder="Select a panchayat"
        />
      )}

      {panchayat && (
        <p style={{ fontFamily: fonts.sans, fontSize: 11, color: colors.muted, margin: '0 0 12px' }}>
          Asking for the whole panchayat: {panchayat.path}
        </p>
      )}

      {/* Theirs may be missing even where the list is not empty. */}
      {panchayats.length > 0 && (
        <div style={{ marginBottom: 12 }}>
          <AskTheOffice missing="panchayat" within={tree.find(blockId)?.name} />
        </div>
      )}

      <Field
        label="Candidate"
        placeholder="Type the candidate's name"
        value={candidateName}
        onChange={setCandidateName}
        maxLength={120}
        hint={
          candidateName.trim()
            ? known
              ? `Matches ${known.name} — the same candidate`
              : 'A new name — this candidate will be added when you submit'
            : undefined
        }
      />

      <Button icon={ClipboardList} loading={busy} full onClick={() => void submit()}>
        Submit request
      </Button>

      <div style={{ marginTop: 12, paddingBottom: 16 }}>
        <InfoNote>
          Access lasts 6 months by default, or until the admin ends it. One approved panchayat opens every booth beneath
          it. The office is on WhatsApp at {SUPPORT_WHATSAPP_DISPLAY}.
        </InfoNote>
      </div>
    </div>
  );
}
