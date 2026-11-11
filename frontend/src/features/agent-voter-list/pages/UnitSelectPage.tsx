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

type Option = { value: string; label: string };

/** The distinct names at one level, in the order the grants came back. */
function namesOf(names: Array<string | null>): Option[] {
  const seen = [...new Set(names.filter((n): n is string => Boolean(n)))];
  return seen.map((n) => ({ value: n, label: n }));
}

/** Keeps the chosen value while it is still on offer, else falls back to the first. */
function settle(chosen: string, options: Option[]) {
  return chosen && options.some((o) => o.value === chosen) ? chosen : options[0]?.value ?? '';
}

/**
 * Most agents hold one grant, so district, block and panchayat have a single
 * answer and are shown locked rather than as a choice of one. An agent whose
 * grant covers a whole block or district gets a real dropdown at that level
 * instead, which is why this is decided per level rather than fixed in advance.
 */
function levelProps(base: string, options: Option[]) {
  const fixed = options.length <= 1;
  return { label: fixed ? `${base} (fixed for your account)` : base, disabled: fixed, options };
}

/** Entry point of the Voters tab: choose which approved booth to work in. */
export function UnitSelectPage() {
  const navigate = useNavigate();
  const booths = useAsync(() => agentVoterApi.booths(), []);
  const [candidateId, setCandidateId] = useState('');
  const [district, setDistrict] = useState('');
  const [block, setBlock] = useState('');
  const [panchayat, setPanchayat] = useState('');
  const [boothId, setBoothId] = useState('');

  const rows = useMemo(() => booths.data ?? [], [booths.data]);

  const candidateOptions = useMemo(() => {
    const seen = new Map<string, string>();
    rows.forEach((r) => seen.set(r.candidateId, r.candidateName));
    return [...seen].map(([value, label]) => ({ value, label }));
  }, [rows]);

  const activeCandidate = settle(candidateId, candidateOptions);

  // One level at a time: each narrows the rows the next is drawn from, so a stale
  // choice lower down drops back to the first thing still on offer by itself.
  const forCandidate = rows.filter((r) => r.candidateId === activeCandidate);

  const districtOptions = namesOf(forCandidate.map((r) => r.districtName));
  const activeDistrict = settle(district, districtOptions);

  const forDistrict = forCandidate.filter((r) => r.districtName === activeDistrict);
  const blockOptions = namesOf(forDistrict.map((r) => r.blockName));
  const activeBlock = settle(block, blockOptions);

  const forBlock = forDistrict.filter((r) => r.blockName === activeBlock);
  const panchayatOptions = namesOf(forBlock.map((r) => r.panchayatName));
  const activePanchayat = settle(panchayat, panchayatOptions);

  const boothRows = forBlock.filter((r) => r.panchayatName === activePanchayat);
  const boothOptions = boothRows.map((r) => ({ value: r.boothId, label: r.boothName }));
  const activeBooth = settle(boothId, boothOptions);

  const grant = boothRows.find((r) => r.boothId === activeBooth);

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

          {/* Left out rather than shown empty if a booth sits shallower than the usual four levels. */}
          {districtOptions.length > 0 && (
            <Select {...levelProps('District', districtOptions)} value={activeDistrict} onChange={setDistrict} />
          )}
          {blockOptions.length > 0 && (
            <Select {...levelProps('Block', blockOptions)} value={activeBlock} onChange={setBlock} />
          )}
          {panchayatOptions.length > 0 && (
            <Select {...levelProps('Panchayat', panchayatOptions)} value={activePanchayat} onChange={setPanchayat} />
          )}

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
