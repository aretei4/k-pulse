import { StatusPill, Tag } from '@/shared/components';
import { colors } from '@/shared/theme';
import type { AgentAccessState } from '@/shared/types';

/**
 * The Access column (FA-1). Approved, Pending and Expired are the statuses the
 * rest of the app already draws; "no access" is not a status of anything — the
 * agent simply holds no grant — so it is written out rather than borrowed from
 * a pill that would call them inactive.
 */
export function AccessPill({ access }: { access: AgentAccessState }) {
  if (access === 'NONE') {
    return <Tag color={colors.muted}>No access</Tag>;
  }
  return <StatusPill status={access} />;
}
