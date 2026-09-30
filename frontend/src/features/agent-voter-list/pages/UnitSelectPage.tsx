import { useMemo, useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { ClipboardList, Home, Users } from 'lucide-react';
import { Button, EmptyState, ErrorNote, Select, Spinner } from '@/shared/components';
import { PageHeader } from '@/shared/layouts/PageHeader';
import { useAsync } from '@/shared/hooks/useAsync';
import { colors, fonts } from '@/shared/theme';
import { formatDate } from '@/shared/utils/format';
import { PRE_ELECTION_ONLY } from '@/lib/features';
import { agentVoterApi } from '../services/agentVoterApi';

/** Entry point of the Voters tab: choose which approved booth to work in. */
export function UnitSelectPage() {
  const navigate = useNavigate();
  const booths = useAsync(() => agentVoterApi.booths(), []);
  const [candidateId, setCandidateId] = useState('');
  const [boothId, setBoothId] = useState('');

  const rows = useMemo(() => booths.data ?? [], [booths.data]);

  const candidateOptions = useMemo(() => {
    const seen = new Map<string, string>();
    rows.forEach((r) => seen.set(r.candidateId, r.candidateName));
    return [...seen].map(([value, label]) => ({ value, label }));
  }, [rows]);

  const activeCandidate = candidateId || candidateOptions[0]?.value || '';
  const boothOptions = rows
    .filter((r) => r.candidateId === activeCandidate)
    .map((r) => ({ value: r.boothId, label: r.path }));

  const activeBooth = boothId && boothOptions.some((b) => b.value === boothId) ? boothId : boothOptions[0]?.value ?? '';
  const grant = rows.find((r) => r.boothId === activeBooth && r.candidateId === activeCandidate);

  return (
    <div style={{ padding: '16px 20px 0' }}>
      <PageHeader
        title="Select unit"
        subtitle={
          PRE_ELECTION_ONLY
            ? 'Choose the candidate and booth you want to record houses in.'
            : 'Choose the candidate and booth you want to work in.'
        }
        size={18}
      />

      {booths.error && <ErrorNote message={booths.error} />}

      {booths.loading && rows.length === 0 ? (
        <Spinner />
      ) : rows.length === 0 ? (
        <EmptyState
          title="No approved units yet"
          hint="Once an admin approves a request, its booths show up here."
          action={
            <Link to="/agent/request" style={{ textDecoration: 'none' }}>
              <Button tone="accent" small icon={ClipboardList}>
                Request access
              </Button>
            </Link>
          }
        />
      ) : (
        <>
          <Select label="Candidate" options={candidateOptions} value={activeCandidate} onChange={setCandidateId} />
          <Select label="Booth" options={boothOptions} value={activeBooth} onChange={setBoothId} />

          {grant?.expiresAt && (
            <p style={{ fontFamily: fonts.sans, fontSize: 11, color: colors.muted, margin: '0 0 12px' }}>
              Access to this booth expires {formatDate(grant.expiresAt)}.
            </p>
          )}

          {/* FR-U12: the house-level tally, on the same grant as the voter list.
              A VITE_PRE_ELECTION_ONLY build offers only this one. */}
          <div style={{ display: 'flex', gap: 8 }}>
            {!PRE_ELECTION_ONLY && (
              <Button
                icon={Users}
                disabled={!activeBooth}
                onClick={() => navigate(`/agent/voters/list?boothId=${activeBooth}&candidateId=${activeCandidate}`)}
              >
                View voter list
              </Button>
            )}
            <Button
              tone={PRE_ELECTION_ONLY ? 'primary' : 'ghost'}
              icon={Home}
              full={PRE_ELECTION_ONLY}
              disabled={!activeBooth}
              onClick={() => navigate(`/agent/houses?boothId=${activeBooth}&candidateId=${activeCandidate}`)}
            >
              Pre-election sentiment
            </Button>
          </div>
        </>
      )}
    </div>
  );
}
