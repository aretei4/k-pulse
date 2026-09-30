import { useState } from 'react';
import { Link } from 'react-router-dom';
import { ClipboardList, Trash2 } from 'lucide-react';
import { Button, Card, EmptyState, ErrorNote, Field, Modal, Spinner, StatusPill, Tag, useToast } from '@/shared/components';
import { PageHeader } from '@/shared/layouts/PageHeader';
import { useAsync } from '@/shared/hooks/useAsync';
import { colors, fonts } from '@/shared/theme';
import { formatDate, titleCase } from '@/shared/utils/format';
import { agentAccessApi } from '@/features/agent-access-request/services/agentAccessApi';
import { agentVoterApi } from '@/features/agent-voter-list/services/agentVoterApi';
import { accountDeletionApi } from '@/features/account-deletion/services/accountDeletionApi';

const typeColor: Record<string, string> = {
  ADD: colors.positive,
  EDIT: colors.marigoldDeep,
  DELETE: colors.negative,
};

export function StatusPage() {
  const { notify } = useToast();
  const requests = useAsync(() => agentAccessApi.mine(), []);
  const changes = useAsync(() => agentVoterApi.myChanges(), []);

  // Google Play expects the account to be closable from inside the app, not
  // only from the website. Both routes file the same request for an admin.
  const [closing, setClosing] = useState(false);
  const [reason, setReason] = useState('');
  const [busy, setBusy] = useState(false);

  async function requestDeletion() {
    setBusy(true);
    try {
      await accountDeletionApi.requestMine(reason.trim() || undefined);
      notify('Request sent — an administrator will confirm before your account is deleted');
      setClosing(false);
      setReason('');
    } catch (err) {
      notify(err instanceof Error ? err.message : 'Could not send the request', 'error');
    } finally {
      setBusy(false);
    }
  }

  const rows = requests.data ?? [];
  const changeRows = changes.data ?? [];

  return (
    <div style={{ padding: '16px 20px 0' }}>
      <PageHeader
        title="Your access"
        subtitle="Status of every request you've submitted."
        size={18}
        action={
          <Link to="/agent/request" style={{ textDecoration: 'none' }}>
            <Button tone="accent" small icon={ClipboardList}>
              Request access
            </Button>
          </Link>
        }
      />

      {requests.error && <ErrorNote message={requests.error} />}

      {requests.loading && rows.length === 0 ? (
        <Spinner />
      ) : rows.length === 0 ? (
        <EmptyState
          title="No requests yet"
          hint="Ask for a booth, panchayat, block or district and the admin will review it."
          action={
            <Link to="/agent/request" style={{ textDecoration: 'none' }}>
              <Button tone="accent" small icon={ClipboardList}>
                Request access
              </Button>
            </Link>
          }
        />
      ) : (
        <div style={{ display: 'flex', flexDirection: 'column', gap: 10 }}>
          {rows.map((request) => (
            <Card key={request.id} padding={12}>
              <div style={{ display: 'flex', justifyContent: 'space-between', gap: 8 }}>
                <p style={{ fontFamily: fonts.sans, fontSize: 13, fontWeight: 500, color: colors.ink, margin: 0 }}>
                  {request.unitName}
                </p>
                <StatusPill status={request.status} />
              </div>
              <p style={{ fontFamily: fonts.sans, fontSize: 11, color: colors.inkSoft, margin: '4px 0 0' }}>
                {titleCase(request.unitLevel)} · {request.unitPath}
              </p>
              <p style={{ fontFamily: fonts.sans, fontSize: 11, color: colors.muted, margin: '2px 0 0' }}>
                Candidate: {request.candidateName} ·{' '}
                {request.status === 'APPROVED' && request.expiresAt
                  ? `expires ${formatDate(request.expiresAt)}`
                  : `submitted ${formatDate(request.requestedAt)}`}
              </p>
            </Card>
          ))}
        </div>
      )}

      {changeRows.length > 0 && (
        <>
          <p style={{ fontFamily: fonts.sans, fontSize: 12, color: colors.inkSoft, margin: '22px 0 8px' }}>
            Your proposed voter changes
          </p>
          <div style={{ display: 'flex', flexDirection: 'column', gap: 8, paddingBottom: 16 }}>
            {changeRows.map((change) => (
              <Card key={change.id} padding={12}>
                <div style={{ display: 'flex', justifyContent: 'space-between', gap: 8, alignItems: 'center' }}>
                  <div style={{ display: 'flex', alignItems: 'center', gap: 8, minWidth: 0 }}>
                    <Tag color={typeColor[change.type] ?? colors.inkSoft}>{titleCase(change.type)}</Tag>
                    <p style={{ fontFamily: fonts.sans, fontSize: 12, color: colors.ink, margin: 0 }}>
                      {change.voterName ?? change.payload.name ?? 'Voter record'}
                    </p>
                  </div>
                  <StatusPill status={change.status} />
                </div>
                <p style={{ fontFamily: fonts.sans, fontSize: 11, color: colors.muted, margin: '4px 0 0' }}>
                  {change.unitPath} · {formatDate(change.proposedAt)}
                </p>
              </Card>
            ))}
          </div>
        </>
      )}

      <div style={{ borderTop: `1px solid ${colors.line}`, margin: '20px 0 0', padding: '14px 0 20px' }}>
        <p style={{ fontFamily: fonts.sans, fontSize: 12, fontWeight: 500, color: colors.ink, margin: '0 0 4px' }}>
          Delete your account
        </p>
        <p style={{ fontFamily: fonts.sans, fontSize: 11, color: colors.muted, margin: '0 0 8px' }}>
          Removes your account and every entry you recorded. An administrator confirms first.
        </p>
        <Button tone="ghost" small icon={Trash2} onClick={() => setClosing(true)}>
          Request account deletion
        </Button>
      </div>

      <Modal open={closing} title="Request account deletion?" onClose={() => setClosing(false)} width={360}>
        <p style={{ fontFamily: fonts.sans, fontSize: 13, color: colors.inkSoft, margin: '0 0 10px' }}>
          An administrator reviews this and confirms with you. Once approved, your account and every sentiment entry and
          house tally you recorded are deleted for good.
        </p>
        <Field label="Reason (optional)" placeholder="e.g. I have left the campaign" value={reason} onChange={setReason} />
        <div style={{ display: 'flex', gap: 8 }}>
          <Button tone="danger" loading={busy} onClick={() => void requestDeletion()}>
            Send request
          </Button>
          <Button tone="ghost" onClick={() => setClosing(false)}>
            Cancel
          </Button>
        </div>
      </Modal>
    </div>
  );
}
