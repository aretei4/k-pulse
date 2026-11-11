import { useEffect, useState } from 'react';
import { Copy, RefreshCw, X } from 'lucide-react';
import { Button, ErrorNote, Field, Select, Tag, useToast } from '@/shared/components';
import { colors, fonts } from '@/shared/theme';
import { formatDate } from '@/shared/utils/format';
import { REFERRAL_HINT, cleanReferral, referralProblem } from '@/shared/utils/referral';
import { usersApi } from '@/features/users/services/usersApi';
import type { FieldAgent, PaymentMode } from '@/shared/types';
import { fieldAgentsApi } from '../services/fieldAgentsApi';
import { AccessPill } from './AccessPill';

const modeOptions = [
  { value: 'UPI', label: 'UPI' },
  { value: 'BANK', label: 'Bank transfer' },
  { value: 'CASH', label: 'Cash' },
];

/** The amount the prototype opens with; the admin overwrites it per payment. */
const DEFAULT_AMOUNT = '1500';

/**
 * FA-19: a word and four digits, easy to say down a phone line. Suggested in
 * the browser so "Suggest another" costs nothing; the server makes its own if
 * the admin clears the box, and holds whatever arrives to the same rule.
 */
const WORDS = ['seva', 'jaga', 'sathi', 'bela', 'tara', 'nila', 'pala', 'mela', 'sita', 'bana'];

function suggestPassword() {
  return `${WORDS[Math.floor(Math.random() * WORDS.length)]}${1000 + Math.floor(Math.random() * 9000)}`;
}

/** The one rule the admin's temporary password has to meet (FA-19). */
function temporaryIsUsable(password: string) {
  return password.trim().length >= 6 && !/\s/.test(password);
}

function today() {
  return new Date().toISOString().slice(0, 10);
}

function Line({ label, value }: { label: string; value: React.ReactNode }) {
  return (
    <div
      style={{
        display: 'flex',
        justifyContent: 'space-between',
        gap: 12,
        padding: '7px 0',
        borderBottom: `1px solid ${colors.line}`,
        fontFamily: fonts.sans,
        fontSize: 12,
      }}
    >
      <span style={{ color: colors.inkSoft }}>{label}</span>
      <span style={{ color: colors.ink, textAlign: 'right' }}>{value ?? '—'}</span>
    </div>
  );
}

function Section({ title, children }: { title: string; children: React.ReactNode }) {
  return (
    <div style={{ marginTop: 16 }}>
      <p
        style={{
          fontFamily: fonts.sans,
          fontSize: 10,
          letterSpacing: '0.09em',
          textTransform: 'uppercase',
          color: colors.muted,
          margin: '0 0 4px',
        }}
      >
        {title}
      </p>
      {children}
    </div>
  );
}

/**
 * Everything held about one agent (FR-A20 §5), and the payment controls
 * (FA-11, FA-12).
 *
 * <p>The row the admin clicked is shown straight away and then replaced by
 * what the server returns, so opening the panel never waits on a round trip
 * for data the list already had.
 */
export function AgentDetailPanel({
  agent: initial,
  onClose,
  onChanged,
}: {
  agent: FieldAgent;
  onClose: () => void;
  onChanged: () => void;
}) {
  const { notify } = useToast();
  const [agent, setAgent] = useState(initial);
  const [amount, setAmount] = useState(DEFAULT_AMOUNT);
  const [mode, setMode] = useState<PaymentMode>('UPI');
  const [paidOn, setPaidOn] = useState(today());
  const [reference, setReference] = useState('');
  const [confirmUnpay, setConfirmUnpay] = useState(false);
  const [editingReferral, setEditingReferral] = useState(false);
  const [referral, setReferral] = useState('');
  const [resetting, setResetting] = useState(false);
  const [temporary, setTemporary] = useState('');
  const [issued, setIssued] = useState<string | null>(null);
  const [copied, setCopied] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  useEffect(() => {
    setAgent(initial);
    setConfirmUnpay(false);
    setEditingReferral(false);
    setResetting(false);
    setIssued(null);
    setError(null);
  }, [initial]);

  async function mark(paid: boolean) {
    const value = Number(amount);
    if (paid && !(value > 0)) return setError('Enter the amount paid');

    setBusy(true);
    setError(null);
    try {
      const updated = await fieldAgentsApi.markPayment(agent.id, {
        paid,
        amount: paid ? value : undefined,
        paidOn: paid ? paidOn : undefined,
        mode: paid ? mode : undefined,
        reference: paid && reference.trim() ? reference.trim() : undefined,
      });
      setAgent(updated);
      setConfirmUnpay(false);
      notify(paid ? 'Marked as paid' : 'Marked as not paid');
      onChanged();
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Could not change the payment status');
    } finally {
      setBusy(false);
    }
    return undefined;
  }

  /** An admin recording a referral the agent never gave, or fixing a wrong one. */
  async function saveReferral() {
    const problem = referralProblem(referral, agent.phone ?? undefined);
    if (problem) return setError(problem);

    setBusy(true);
    setError(null);
    try {
      const updated = await fieldAgentsApi.updateReferral(agent.id, cleanReferral(referral));
      setAgent(updated);
      setEditingReferral(false);
      notify(updated.referralNo ? 'Referral saved' : 'Referral cleared');
      onChanged();
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Could not save the referral');
    } finally {
      setBusy(false);
    }
    return undefined;
  }

  /** FA-19: the password comes back once, so it is held on screen until Done. */
  async function resetPassword() {
    if (!temporaryIsUsable(temporary)) return setError('At least 6 characters and no spaces');

    setBusy(true);
    setError(null);
    try {
      const result = await fieldAgentsApi.resetPassword(agent.id, temporary.trim());
      setIssued(result.temporaryPassword);
      setAgent({ ...agent, passwordSet: true, mustChangePassword: true, passwordResetRequestedAt: null });
      onChanged();
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Could not reset the password');
    } finally {
      setBusy(false);
    }
    return undefined;
  }

  async function dismissRequest() {
    try {
      const updated = await fieldAgentsApi.dismissPasswordRequest(agent.id);
      setAgent(updated);
      notify('Request dismissed');
      onChanged();
    } catch (err) {
      notify(err instanceof Error ? err.message : 'Could not dismiss the request', 'error');
    }
  }

  /** AD-1: gives or removes the agent's Reports page. Nothing else moves. */
  async function toggleReports() {
    setBusy(true);
    try {
      const updated = await fieldAgentsApi.setReportsAccess(agent.id, !agent.reportsEnabled);
      setAgent(updated);
      notify(updated.reportsEnabled ? 'Reports access given' : 'Reports access removed');
      onChanged();
    } catch (err) {
      notify(err instanceof Error ? err.message : 'Could not change reports access', 'error');
    } finally {
      setBusy(false);
    }
  }

  async function toggleDownload() {
    try {
      await usersApi.setDownloadEnabled(agent.id, !agent.downloadEnabled);
      setAgent({ ...agent, downloadEnabled: !agent.downloadEnabled });
      notify(agent.downloadEnabled ? 'PDF download turned off' : 'PDF download turned on');
      onChanged();
    } catch (err) {
      notify(err instanceof Error ? err.message : 'Could not change the download switch', 'error');
    }
  }

  return (
    <aside
      style={{
        width: 340,
        flexShrink: 0,
        alignSelf: 'flex-start',
        position: 'sticky',
        top: 16,
        maxHeight: 'calc(100vh - 32px)',
        overflowY: 'auto',
        background: colors.card,
        border: `1px solid ${colors.line}`,
        borderRadius: 10,
        padding: 16,
      }}
    >
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'start', gap: 8 }}>
        <div>
          <p style={{ fontFamily: fonts.serif, fontSize: 17, color: colors.ink, margin: 0 }}>{agent.name}</p>
          <p style={{ fontFamily: fonts.sans, fontSize: 11, color: colors.muted, margin: '2px 0 0' }}>
            {agent.agentCode ?? 'No agent ID'}
          </p>
        </div>
        <Button tone="ghost" small icon={X} onClick={onClose}>
          Close
        </Button>
      </div>

      <div style={{ display: 'flex', gap: 6, marginTop: 8 }}>
        <AccessPill access={agent.access} />
        <Tag color={agent.paid ? colors.positive : colors.marigoldDeep}>{agent.paid ? 'Paid' : 'Not paid'}</Tag>
        {!agent.active && <Tag color={colors.negative}>Deactivated</Tag>}
      </div>

      <Section title="Contact">
        <Line label="Mobile" value={agent.phone} />
        <Line label="Email" value={agent.email} />
        <Line label="Address" value={agent.address} />
        {/* Given at sign-up, and correctable here: a number typed wrong on a
            phone is otherwise stuck on the record for good. */}
        <Line
          label="Referral"
          value={
            <>
              {agent.referralNo ?? 'None given'}{' '}
              <button
                type="button"
                onClick={() => {
                  setReferral(agent.referralNo ?? '');
                  setEditingReferral(true);
                }}
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
                {agent.referralNo ? 'Change' : 'Add'}
              </button>
            </>
          }
        />
        {editingReferral && (
          <div style={{ marginTop: 8 }}>
            <Field
              label="Referral number"
              placeholder="98XXXXXX21"
              value={referral}
              onChange={(value) => setReferral(cleanReferral(value))}
              error={referralProblem(referral, agent.phone ?? undefined) ?? undefined}
              hint={REFERRAL_HINT}
            />
            <div style={{ display: 'flex', gap: 8 }}>
              <Button small loading={busy} onClick={() => void saveReferral()}>
                Save
              </Button>
              <Button tone="ghost" small onClick={() => setEditingReferral(false)}>
                Cancel
              </Button>
            </div>
          </div>
        )}
      </Section>

      <Section title="Assignment">
        <Line label="District" value={agent.districtName} />
        <Line label="Block" value={agent.blockName} />
        <Line label="Panchayat" value={agent.panchayatName} />
        <Line label="Candidate" value={agent.candidateName} />
        <Line
          label="Access"
          value={
            agent.access === 'NONE'
              ? 'No access'
              : `${agent.access === 'APPROVED' ? 'Approved' : agent.access === 'PENDING' ? 'Pending' : 'Expired'}${
                  agent.accessExpiresAt
                    ? ` · ${agent.access === 'EXPIRED' ? 'expired' : 'until'} ${formatDate(agent.accessExpiresAt)}`
                    : ''
                }`
          }
        />
      </Section>

      <Section title="Activity">
        <Line label="Voters recorded" value={agent.votersRecorded.toLocaleString('en-IN')} />
        <Line label="Last active" value={agent.lastActive ? formatDate(agent.lastActive) : 'Never'} />
        <Line label="Joined" value={formatDate(agent.joined)} />
        <Line
          label="PDF download"
          value={
            <>
              {agent.downloadEnabled ? 'On' : 'Off'}{' '}
              <button
                type="button"
                onClick={() => void toggleDownload()}
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
                {agent.downloadEnabled ? 'Turn off' : 'Turn on'}
              </button>
            </>
          }
        />
      </Section>

      {/* FR-A22 / AD-1. The page it gives is still limited to the booths the
          agent's own approvals already cover. */}
      <Section title="Page access">
        <Line label="Reports page" value={agent.reportsEnabled ? 'On' : 'Off'} />
        <p style={{ fontFamily: fonts.sans, fontSize: 11, color: colors.muted, margin: '6px 0 0' }}>
          The agent sees a Reports page in the app, limited to the booths approved for them.
        </p>
        <div style={{ marginTop: 10 }}>
          <Button
            small
            tone={agent.reportsEnabled ? 'ghost' : 'primary'}
            loading={busy}
            onClick={() => void toggleReports()}
          >
            {agent.reportsEnabled ? 'Remove reports access' : 'Give reports access'}
          </Button>
        </div>
      </Section>

      {/* FA-18/FA-19/FA-24. OTP on and off is not here yet; what is shown is
          what the server can actually answer for. */}
      <Section title="Login">
        <Line
          label="Password"
          value={
            !agent.passwordSet
              ? 'Not set'
              : agent.mustChangePassword
                ? 'Set · must change at next sign-in'
                : 'Set'
          }
        />
        <Line
          label="Reset asked for"
          value={agent.passwordResetRequestedAt ? formatDate(agent.passwordResetRequestedAt) : 'No'}
        />
        <p style={{ fontFamily: fonts.sans, fontSize: 11, color: colors.muted, margin: '6px 0 0' }}>
          {agent.passwordResetRequestedAt
            ? 'This agent has asked for a new password. Set one and give it to them in person or by phone — K-Pulse does not send it.'
            : 'The agent signs in with a one-time code sent to their mobile number, or with their email and password.'}
        </p>

        {issued ? (
          <div
            style={{
              marginTop: 10,
              padding: 10,
              borderRadius: 8,
              background: colors.marigoldWash,
              border: `1px solid ${colors.line}`,
            }}
          >
            <p style={{ fontFamily: fonts.sans, fontSize: 11, color: colors.inkSoft, margin: 0 }}>
              Give this to {agent.name} now. It is not shown again.
            </p>
            <p style={{ fontFamily: fonts.mono, fontSize: 18, color: colors.ink, margin: '6px 0' }}>{issued}</p>
            <div style={{ display: 'flex', gap: 8 }}>
              <Button
                tone="ghost"
                small
                icon={Copy}
                onClick={() => {
                  void navigator.clipboard?.writeText(issued).then(() => setCopied(true));
                }}
              >
                {copied ? 'Copied' : 'Copy'}
              </Button>
              <Button
                small
                onClick={() => {
                  setIssued(null);
                  setResetting(false);
                  setCopied(false);
                }}
              >
                Done
              </Button>
            </div>
          </div>
        ) : resetting ? (
          <div style={{ marginTop: 10 }}>
            <Field
              label="Temporary password"
              value={temporary}
              onChange={setTemporary}
              hint="At least 6 characters, no spaces. The agent must change it at their next sign-in."
            />
            <div style={{ display: 'flex', gap: 8 }}>
              <Button
                tone="accent"
                small
                loading={busy}
                disabled={!temporaryIsUsable(temporary)}
                onClick={() => void resetPassword()}
              >
                Reset
              </Button>
              <Button tone="ghost" small icon={RefreshCw} onClick={() => setTemporary(suggestPassword())}>
                Suggest another
              </Button>
              <Button tone="ghost" small onClick={() => setResetting(false)}>
                Cancel
              </Button>
            </div>
          </div>
        ) : (
          <div style={{ display: 'flex', gap: 8, marginTop: 10 }}>
            <Button
              small
              onClick={() => {
                setTemporary(suggestPassword());
                setResetting(true);
              }}
            >
              Reset password
            </Button>
            {agent.passwordResetRequestedAt && (
              <Button tone="ghost" small onClick={() => void dismissRequest()}>
                Dismiss request
              </Button>
            )}
          </div>
        )}
      </Section>

      <Section title="Payment">
        <Line label="Status" value={<Tag color={agent.paid ? colors.positive : colors.marigoldDeep}>{agent.paid ? 'Paid' : 'Not paid'}</Tag>} />
        {agent.paid && (
          <>
            <Line
              label="Amount"
              value={agent.paidAmount == null ? '—' : `₹${Number(agent.paidAmount).toLocaleString('en-IN')}`}
            />
            <Line label="Paid on" value={agent.paidOn ? formatDate(agent.paidOn) : '—'} />
            <Line label="Mode" value={modeOptions.find((m) => m.value === agent.paidMode)?.label ?? '—'} />
            <Line label="Reference" value={agent.paidRef} />
            <Line
              label="Recorded by"
              value={agent.paidUpdatedByName ? `${agent.paidUpdatedByName} · ${formatDate(agent.paidUpdatedAt)}` : '—'}
            />
          </>
        )}
      </Section>

      {error && <ErrorNote message={error} />}

      {agent.paid ? (
        <div style={{ marginTop: 12 }}>
          {confirmUnpay ? (
            <>
              <p style={{ fontFamily: fonts.sans, fontSize: 12, color: colors.inkSoft, margin: '0 0 8px' }}>
                This clears the amount, date, mode and reference. Only the record of who changed it is kept.
              </p>
              <div style={{ display: 'flex', gap: 8 }}>
                <Button small loading={busy} onClick={() => void mark(false)}>
                  Yes, mark as not paid
                </Button>
                <Button tone="ghost" small onClick={() => setConfirmUnpay(false)}>
                  Cancel
                </Button>
              </div>
            </>
          ) : (
            <Button tone="ghost" small onClick={() => setConfirmUnpay(true)}>
              Mark as not paid
            </Button>
          )}
        </div>
      ) : (
        <div style={{ marginTop: 12 }}>
          <div style={{ display: 'flex', gap: 8 }}>
            <Field label="Amount (₹)" value={amount} onChange={setAmount} style={{ flex: 1 }} />
            <Select
              label="Mode"
              options={modeOptions}
              value={mode}
              onChange={(value) => setMode(value as PaymentMode)}
              style={{ flex: 1 }}
            />
          </div>
          <div style={{ display: 'flex', gap: 8 }}>
            <Field label="Paid on" type="date" value={paidOn} onChange={setPaidOn} style={{ flex: 1 }} />
            <Field label="Reference" value={reference} onChange={setReference} style={{ flex: 1 }} />
          </div>
          <Button tone="accent" small full loading={busy} onClick={() => void mark(true)}>
            Mark as paid
          </Button>
        </div>
      )}
    </aside>
  );
}
