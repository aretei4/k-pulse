import { useState } from 'react';
import { CheckCircle2, ShieldOff, XCircle } from 'lucide-react';
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
import type { AccessRequest, AccessRequestStatus } from '@/shared/types';
import { accessRequestsApi } from '../services/accessRequestsApi';

const statusOptions = [
  { value: 'PENDING', label: 'Pending' },
  { value: 'APPROVED', label: 'Approved' },
  { value: 'REJECTED', label: 'Rejected' },
  { value: 'REVOKED', label: 'Revoked' },
  { value: 'ALL', label: 'All' },
];

export function AccessRequestsPage() {
  const { notify } = useToast();
  const [status, setStatus] = useState<AccessRequestStatus | 'ALL'>('PENDING');
  const requests = useAsync(() => accessRequestsApi.list(status), [status]);

  const [approving, setApproving] = useState<AccessRequest | null>(null);
  const [months, setMonths] = useState('6');
  const [note, setNote] = useState('');
  const [busy, setBusy] = useState(false);

  async function approve() {
    if (!approving) return;
    const parsed = Number(months);
    if (!Number.isFinite(parsed) || parsed < 1 || parsed > 60) {
      notify('Access duration must be between 1 and 60 months', 'error');
      return;
    }
    setBusy(true);
    try {
      await accessRequestsApi.approve(approving.id, parsed, note.trim() || undefined);
      notify(`Approved — ${approving.agentName} now sees ${approving.unitName}`);
      setApproving(null);
      setNote('');
      setMonths('6');
      requests.reload();
    } catch (err) {
      notify(err instanceof Error ? err.message : 'Could not approve', 'error');
    } finally {
      setBusy(false);
    }
  }

  async function act(request: AccessRequest, action: 'reject' | 'revoke') {
    try {
      if (action === 'reject') await accessRequestsApi.reject(request.id);
      else await accessRequestsApi.revoke(request.id);
      notify(action === 'reject' ? 'Request rejected' : 'Access revoked');
      requests.reload();
    } catch (err) {
      notify(err instanceof Error ? err.message : 'Action failed', 'error');
    }
  }

  const rows = requests.data ?? [];

  return (
    <div>
      <PageHeader
        title="Access requests"
        subtitle="Approving a request grants the agent that unit's voter list — and every booth under it — tied to the named candidate."
        action={
          <Select
            options={statusOptions}
            value={status}
            onChange={(v) => setStatus(v as AccessRequestStatus | 'ALL')}
            style={{ marginBottom: 0, minWidth: 150 }}
          />
        }
      />

      {requests.error && <ErrorNote message={requests.error} />}

      {requests.loading && rows.length === 0 ? (
        <Spinner />
      ) : rows.length === 0 ? (
        <EmptyState title={`No ${titleCase(status)} requests`} hint="New requests from field agents land here." />
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
                  {titleCase(request.unitLevel)} · {request.unitPath} · Candidate: {request.candidateName}
                </p>
                <p style={{ fontFamily: fonts.sans, fontSize: 11, color: colors.muted, margin: '2px 0 0' }}>
                  Requested {formatDate(request.requestedAt)}
                  {request.expiresAt && request.status === 'APPROVED' ? ` · expires ${formatDate(request.expiresAt)}` : ''}
                </p>
              </div>

              <div style={{ display: 'flex', gap: 8, flexShrink: 0 }}>
                {request.status === 'PENDING' && (
                  <>
                    <Button tone="ghost" small icon={XCircle} onClick={() => void act(request, 'reject')}>
                      Reject
                    </Button>
                    <Button tone="accent" small icon={CheckCircle2} onClick={() => setApproving(request)}>
                      Approve
                    </Button>
                  </>
                )}
                {request.status === 'APPROVED' && (
                  <Button tone="ghost" small icon={ShieldOff} onClick={() => void act(request, 'revoke')}>
                    Revoke
                  </Button>
                )}
              </div>
            </Row>
          ))}
        </RowList>
      )}

      <Modal
        open={approving !== null}
        title="Approve access"
        subtitle={
          approving
            ? `${approving.agentName} → ${approving.unitPath} · ${approving.candidateName}`
            : undefined
        }
        onClose={() => setApproving(null)}
      >
        <Field
          label="Access duration (months)"
          type="number"
          value={months}
          onChange={setMonths}
          hint="6 months is the default. You can revoke early at any time."
        />
        <Field label="Note (optional)" placeholder="Visible to you in the audit trail" value={note} onChange={setNote} />
        <div style={{ display: 'flex', gap: 8, marginTop: 4 }}>
          <Button tone="accent" icon={CheckCircle2} loading={busy} onClick={() => void approve()}>
            Approve access
          </Button>
          <Button tone="ghost" onClick={() => setApproving(null)}>
            Cancel
          </Button>
        </div>
      </Modal>
    </div>
  );
}
