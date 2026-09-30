import { useState } from 'react';
import { CheckCircle2, XCircle } from 'lucide-react';
import {
  Button,
  EmptyState,
  ErrorNote,
  Field,
  Modal,
  Row,
  RowList,
  Select,
  Spinner,
  StatusPill,
  useToast,
} from '@/shared/components';
import { PageHeader } from '@/shared/layouts/PageHeader';
import { useAsync } from '@/shared/hooks/useAsync';
import { colors, fonts } from '@/shared/theme';
import { formatDate, titleCase } from '@/shared/utils/format';
import type { AccountDeletionRequest, DeletionStatus } from '@/shared/types';
import { accountDeletionApi } from '../services/accountDeletionApi';

const statusOptions = [
  { value: 'PENDING', label: 'Pending' },
  { value: 'APPROVED', label: 'Approved' },
  { value: 'REJECTED', label: 'Rejected' },
  { value: 'ALL', label: 'All' },
];

/**
 * The review queue for account deletions. Approving here is destructive and
 * final: the agent's account and every entry they recorded are erased, so the
 * confirmation spells out what goes.
 */
export function AccountDeletionsPage() {
  const { notify } = useToast();
  const [status, setStatus] = useState<DeletionStatus | 'ALL'>('PENDING');
  const requests = useAsync(() => accountDeletionApi.list(status === 'ALL' ? undefined : status), [status]);

  const [approving, setApproving] = useState<AccountDeletionRequest | null>(null);
  const [note, setNote] = useState('');
  const [busy, setBusy] = useState(false);

  async function approve() {
    if (!approving) return;
    setBusy(true);
    try {
      const result = await accountDeletionApi.approve(approving.id, note.trim() || undefined);
      notify(`${approving.agentName}'s account deleted with ${result.deletedEntries ?? 0} recorded entr(ies)`);
      setApproving(null);
      setNote('');
      requests.reload();
    } catch (err) {
      notify(err instanceof Error ? err.message : 'Could not delete the account', 'error');
    } finally {
      setBusy(false);
    }
  }

  async function reject(request: AccountDeletionRequest) {
    try {
      await accountDeletionApi.reject(request.id);
      notify('Request rejected — the account is untouched');
      requests.reload();
    } catch (err) {
      notify(err instanceof Error ? err.message : 'Action failed', 'error');
    }
  }

  const rows = requests.data ?? [];

  return (
    <div>
      <PageHeader
        title="Account deletions"
        subtitle="Agents asking for their account to be removed. Confirm who is asking before you approve — approving erases the account and everything they recorded."
        action={
          <Select
            options={statusOptions}
            value={status}
            onChange={(v) => setStatus(v as DeletionStatus | 'ALL')}
            style={{ marginBottom: 0, minWidth: 150 }}
          />
        }
      />

      {requests.error && <ErrorNote message={requests.error} />}

      {requests.loading && rows.length === 0 ? (
        <Spinner />
      ) : rows.length === 0 ? (
        <EmptyState
          title={`No ${titleCase(status)} requests`}
          hint="Requests from the app or the public delete-account page land here."
        />
      ) : (
        <RowList>
          {rows.map((request, i) => (
            <Row key={request.id} last={i === rows.length - 1}>
              <div style={{ minWidth: 0 }}>
                <div style={{ display: 'flex', alignItems: 'center', gap: 8, flexWrap: 'wrap' }}>
                  <p style={{ fontFamily: fonts.sans, fontSize: 13, fontWeight: 500, color: colors.ink, margin: 0 }}>
                    {request.agentName}
                  </p>
                  <StatusPill status={request.status} />
                </div>
                <p style={{ fontFamily: fonts.sans, fontSize: 12, color: colors.inkSoft, margin: '3px 0 0' }}>
                  {request.agentPhone}
                  {request.agentEmail ? ` · ${request.agentEmail}` : ''}
                </p>
                {request.reason && (
                  <p style={{ fontFamily: fonts.sans, fontSize: 12, color: colors.inkSoft, margin: '2px 0 0' }}>
                    “{request.reason}”
                  </p>
                )}
                <p style={{ fontFamily: fonts.sans, fontSize: 11, color: colors.muted, margin: '2px 0 0' }}>
                  Asked {formatDate(request.requestedAt)}
                  {request.reviewedAt ? ` · reviewed ${formatDate(request.reviewedAt)}` : ''}
                  {request.reviewedByName ? ` by ${request.reviewedByName}` : ''}
                  {request.status === 'APPROVED'
                    ? ` · ${request.deletedEntries ?? 0} entr(ies) deleted`
                    : ''}
                </p>
              </div>

              {request.status === 'PENDING' && (
                <div style={{ display: 'flex', gap: 8, flexShrink: 0 }}>
                  <Button tone="ghost" small icon={XCircle} onClick={() => void reject(request)}>
                    Reject
                  </Button>
                  <Button tone="danger" small icon={CheckCircle2} onClick={() => setApproving(request)}>
                    Approve
                  </Button>
                </div>
              )}
            </Row>
          ))}
        </RowList>
      )}

      <Modal
        open={approving !== null}
        title="Delete this account?"
        subtitle={approving ? `${approving.agentName} · ${approving.agentPhone}` : undefined}
        onClose={() => setApproving(null)}
        width={420}
      >
        <p style={{ fontFamily: fonts.sans, fontSize: 13, color: colors.inkSoft, margin: '0 0 10px' }}>
          This erases the account and every entry recorded under it — sentiment entries, pre-election house tallies,
          access requests and proposed voter changes. Booth reports will drop that agent's work. The roll itself is not
          touched.
        </p>
        <p style={{ fontFamily: fonts.sans, fontSize: 12, color: colors.marigoldDeep, margin: '0 0 14px' }}>
          It cannot be undone. Confirm with the agent on {approving?.agentPhone} first.
        </p>

        <Field label="Note (optional)" placeholder="e.g. confirmed by phone" value={note} onChange={setNote} />

        <div style={{ display: 'flex', gap: 8 }}>
          <Button tone="danger" loading={busy} onClick={() => void approve()}>
            Delete account
          </Button>
          <Button tone="ghost" onClick={() => setApproving(null)}>
            Cancel
          </Button>
        </div>
      </Modal>
    </div>
  );
}
