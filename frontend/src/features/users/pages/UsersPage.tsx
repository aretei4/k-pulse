import { useState } from 'react';
import { UserPlus } from 'lucide-react';
import {
  Button,
  EmptyState,
  ErrorNote,
  Field,
  Modal,
  Row,
  RowList,
  Spinner,
  StatusPill,
  useToast,
} from '@/shared/components';
import { PageHeader } from '@/shared/layouts/PageHeader';
import { useAsync } from '@/shared/hooks/useAsync';
import { colors, fonts } from '@/shared/theme';
import { usersApi } from '../services/usersApi';
import { REFERRAL_HINT, cleanReferral, referralProblem } from '@/shared/utils/referral';

export function UsersPage() {
  const { notify } = useToast();
  const agents = useAsync(() => usersApi.list('FIELD_AGENT'), []);

  const [adding, setAdding] = useState(false);
  const [name, setName] = useState('');
  const [email, setEmail] = useState('');
  const [phone, setPhone] = useState('');
  const [referralNo, setReferralNo] = useState('');
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  async function create() {
    if (!name.trim()) return setError('Enter the agent name');
    if (!/^\S+@\S+\.\S+$/.test(email.trim())) return setError('Enter a valid email address');
    if (!/^\d{10}$/.test(phone.trim())) return setError('Enter a 10-digit mobile number');
    const referralError = referralProblem(referralNo, phone);
    if (referralError) return setError(referralError);

    setBusy(true);
    setError(null);
    try {
      await usersApi.create({
        name: name.trim(),
        email: email.trim(),
        phone: phone.trim(),
        ...(referralNo ? { referralNo } : {}),
      });
      notify('Field agent created');
      setAdding(false);
      setName('');
      setEmail('');
      setPhone('');
      setReferralNo('');
      agents.reload();
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Could not create the agent');
    } finally {
      setBusy(false);
    }
    return undefined;
  }

  async function toggle(id: string, active: boolean) {
    try {
      await usersApi.setActive(id, active);
      notify(active ? 'Agent reactivated' : 'Agent deactivated');
      agents.reload();
    } catch (err) {
      notify(err instanceof Error ? err.message : 'Could not update the agent', 'error');
    }
  }

  /**
   * FR-U13 / DL-13: a downloaded list is named voters' data leaving K-Pulse, so
   * it is off for a new agent and switched on one agent at a time.
   */
  async function toggleDownload(id: string, enabled: boolean) {
    try {
      await usersApi.setDownloadEnabled(id, enabled);
      notify(enabled ? 'PDF download turned on' : 'PDF download turned off');
      agents.reload();
    } catch (err) {
      notify(err instanceof Error ? err.message : 'Could not update the agent', 'error');
    }
  }

  const rows = agents.data ?? [];

  return (
    <div>
      <PageHeader
        title="Field agents"
        subtitle="Create agents, or deactivate one to cut off their access immediately. PDF download is off until you switch it on."
        action={
          <Button tone="accent" icon={UserPlus} onClick={() => setAdding(true)}>
            Add field agent
          </Button>
        }
      />

      {agents.error && <ErrorNote message={agents.error} />}

      {agents.loading && rows.length === 0 ? (
        <Spinner />
      ) : rows.length === 0 ? (
        <EmptyState title="No field agents yet" />
      ) : (
        <RowList>
          {rows.map((agent, i) => (
            <Row key={agent.id} last={i === rows.length - 1}>
              <div>
                <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
                  <p style={{ fontFamily: fonts.sans, fontSize: 13, fontWeight: 500, color: colors.ink, margin: 0 }}>
                    {agent.name}
                  </p>
                  <StatusPill status={agent.active ? 'ACTIVE' : 'INACTIVE'} />
                </div>
                <p style={{ fontFamily: fonts.sans, fontSize: 12, color: colors.inkSoft, margin: '3px 0 0' }}>
                  {agent.phone} · {agent.email}
                </p>
                <label
                  style={{
                    display: 'inline-flex',
                    alignItems: 'center',
                    gap: 6,
                    marginTop: 6,
                    cursor: 'pointer',
                    fontFamily: fonts.sans,
                    fontSize: 12,
                    color: colors.inkSoft,
                  }}
                >
                  <input
                    type="checkbox"
                    checked={Boolean(agent.downloadEnabled)}
                    onChange={(e) => void toggleDownload(agent.id, e.target.checked)}
                  />
                  Can download voter lists as PDF
                </label>
              </div>
              <Button tone="ghost" small onClick={() => void toggle(agent.id, !agent.active)}>
                {agent.active ? 'Deactivate' : 'Reactivate'}
              </Button>
            </Row>
          ))}
        </RowList>
      )}

      <Modal open={adding} title="Add field agent" subtitle="They sign in with an OTP on this number." onClose={() => setAdding(false)}>
        {error && <p style={{ fontFamily: fonts.sans, fontSize: 12, color: colors.negative, margin: '0 0 10px' }}>{error}</p>}
        <Field label="Full name" placeholder="Prakash Sahoo" value={name} onChange={setName} />
        <Field label="Email" placeholder="agent@example.com" type="email" value={email} onChange={setEmail} />
        <Field
          label="Phone number"
          placeholder="98XXXXXX21"
          type="tel"
          value={phone}
          onChange={(v) => setPhone(v.replace(/\D/g, '').slice(0, 10))}
        />
        {/* Optional, and held to the same rule as the agent's own sign-up form. */}
        <Field
          label="Referral number — if any"
          placeholder="98XXXXXX21"
          type="tel"
          value={referralNo}
          onChange={(v) => setReferralNo(cleanReferral(v))}
          error={referralProblem(referralNo, phone) ?? undefined}
          hint={REFERRAL_HINT}
        />
        <div style={{ display: 'flex', gap: 8, marginTop: 4 }}>
          <Button tone="accent" loading={busy} onClick={() => void create()}>
            Create agent
          </Button>
          <Button tone="ghost" onClick={() => setAdding(false)}>
            Cancel
          </Button>
        </div>
      </Modal>
    </div>
  );
}
