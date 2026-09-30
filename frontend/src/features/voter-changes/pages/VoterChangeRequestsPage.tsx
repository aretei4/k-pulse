import { useState } from 'react';
import { CheckCircle2, XCircle } from 'lucide-react';
import {
  Button,
  EmptyState,
  ErrorNote,
  Row,
  RowList,
  Select,
  Spinner,
  Tag,
  useToast,
} from '@/shared/components';
import { PageHeader } from '@/shared/layouts/PageHeader';
import { useAsync } from '@/shared/hooks/useAsync';
import { colors, fonts } from '@/shared/theme';
import { formatDate, titleCase } from '@/shared/utils/format';
import type { VoterChangeRequest, VoterChangeStatus, VoterChangeType } from '@/shared/types';
import { voterChangesApi } from '../services/voterChangesApi';

const statusOptions = [
  { value: 'PENDING', label: 'Pending' },
  { value: 'APPROVED', label: 'Approved' },
  { value: 'REJECTED', label: 'Rejected' },
  { value: 'ALL', label: 'All' },
];

const typeColor: Record<VoterChangeType, string> = {
  ADD: colors.positive,
  EDIT: colors.marigoldDeep,
  DELETE: colors.negative,
};

function describe(change: VoterChangeRequest) {
  const p = change.payload;
  if (change.type === 'DELETE') return change.voterName ?? 'Voter record';
  const bits = [
    p.name ?? change.voterName,
    p.relation,
    p.age !== undefined ? `${p.age} yrs` : null,
    p.gender,
    p.wardNo !== undefined ? `Ward ${p.wardNo}` : null,
    p.epicNo,
  ].filter(Boolean);
  return bits.join(' · ') || change.voterName || 'Voter record';
}

export function VoterChangeRequestsPage() {
  const { notify } = useToast();
  const [status, setStatus] = useState<VoterChangeStatus | 'ALL'>('PENDING');
  const changes = useAsync(() => voterChangesApi.list(status), [status]);
  const [busyId, setBusyId] = useState<string | null>(null);

  async function decide(change: VoterChangeRequest, approve: boolean) {
    setBusyId(change.id);
    try {
      if (approve) {
        await voterChangesApi.approve(change.id);
        notify(`${titleCase(change.type)} applied to the voter list`);
      } else {
        await voterChangesApi.reject(change.id);
        notify('Proposal rejected — the roll is unchanged');
      }
      changes.reload();
    } catch (err) {
      notify(err instanceof Error ? err.message : 'Action failed', 'error');
    } finally {
      setBusyId(null);
    }
  }

  const rows = changes.data ?? [];

  return (
    <div>
      <PageHeader
        title="Voter change requests"
        subtitle="Field agents propose additions, edits and deletions here — nothing touches the live voter list until you approve it."
        action={
          <Select
            options={statusOptions}
            value={status}
            onChange={(v) => setStatus(v as VoterChangeStatus | 'ALL')}
            style={{ marginBottom: 0, minWidth: 150 }}
          />
        }
      />

      {changes.error && <ErrorNote message={changes.error} />}

      {changes.loading && rows.length === 0 ? (
        <Spinner />
      ) : rows.length === 0 ? (
        <EmptyState title={`No ${titleCase(status)} proposals`} hint="Agent-proposed roll changes land here." />
      ) : (
        <RowList>
          {rows.map((change, i) => (
            <Row key={change.id} last={i === rows.length - 1}>
              <div style={{ minWidth: 0 }}>
                <div style={{ display: 'flex', alignItems: 'center', gap: 8, flexWrap: 'wrap' }}>
                  <Tag color={typeColor[change.type]}>{titleCase(change.type)}</Tag>
                  <p style={{ fontFamily: fonts.sans, fontSize: 13, fontWeight: 500, color: colors.ink, margin: 0 }}>
                    {describe(change)}
                  </p>
                </div>
                <p style={{ fontFamily: fonts.sans, fontSize: 12, color: colors.inkSoft, margin: '4px 0 0' }}>
                  Proposed by {change.agentName} · {change.unitPath} · {formatDate(change.proposedAt)}
                </p>
                {change.payload.reason && (
                  <p style={{ fontFamily: fonts.sans, fontSize: 11, color: colors.muted, margin: '2px 0 0' }}>
                    Reason: {change.payload.reason}
                  </p>
                )}
              </div>

              {change.status === 'PENDING' && (
                <div style={{ display: 'flex', gap: 8, flexShrink: 0 }}>
                  <Button
                    tone="ghost"
                    small
                    icon={XCircle}
                    loading={busyId === change.id}
                    onClick={() => void decide(change, false)}
                  >
                    Reject
                  </Button>
                  <Button
                    tone="accent"
                    small
                    icon={CheckCircle2}
                    loading={busyId === change.id}
                    onClick={() => void decide(change, true)}
                  >
                    Approve &amp; update
                  </Button>
                </div>
              )}
            </Row>
          ))}
        </RowList>
      )}
    </div>
  );
}
