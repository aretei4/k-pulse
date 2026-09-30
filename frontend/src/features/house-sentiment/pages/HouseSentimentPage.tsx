import { useState } from 'react';
import { Link, useNavigate, useSearchParams } from 'react-router-dom';
import { ChevronLeft, Edit3, Home, Trash2, UserPlus } from 'lucide-react';
import { Button, EmptyState, ErrorNote, Modal, Spinner, useToast } from '@/shared/components';
import { PageHeader } from '@/shared/layouts/PageHeader';
import { useAsync } from '@/shared/hooks/useAsync';
import { agentVoterApi } from '@/features/agent-voter-list/services/agentVoterApi';
import { colors, fonts } from '@/shared/theme';
import type { HouseSentimentEntry } from '@/shared/types';
import { houseSentimentApi } from '../services/houseSentimentApi';

/**
 * FR-U12: the houses tallied so far in this booth, for this candidate. Every
 * agent approved on the booth sees the same list, so the same house isn't
 * visited twice.
 */
export function HouseSentimentPage() {
  const navigate = useNavigate();
  const { notify } = useToast();
  const [params] = useSearchParams();
  const boothId = params.get('boothId') ?? '';
  const candidateId = params.get('candidateId') ?? '';

  const booths = useAsync(() => agentVoterApi.booths(), []);
  const [reload, setReload] = useState(0);
  const houses = useAsync(
    () => (boothId && candidateId ? houseSentimentApi.list(boothId, candidateId) : Promise.resolve(null)),
    [boothId, candidateId, reload],
  );

  const [doomed, setDoomed] = useState<HouseSentimentEntry | null>(null);
  const [busy, setBusy] = useState(false);

  const grant = (booths.data ?? []).find((b) => b.boothId === boothId && b.candidateId === candidateId);
  const rows = houses.data ?? [];

  if (!boothId || !candidateId) {
    return (
      <div style={{ padding: '16px 20px 0' }}>
        <EmptyState
          title="Pick a booth first"
          action={
            <Link to="/agent/voters" style={{ textDecoration: 'none' }}>
              <Button small>Select unit</Button>
            </Link>
          }
        />
      </div>
    );
  }

  const formLink = (entry?: HouseSentimentEntry) =>
    `/agent/houses/record?boothId=${boothId}&candidateId=${candidateId}${entry ? `&houseNo=${encodeURIComponent(entry.houseNo)}` : ''}`;

  async function remove() {
    if (!doomed) return;
    setBusy(true);
    try {
      await houseSentimentApi.remove(doomed.id);
      notify(`House ${doomed.houseNo} removed`);
      setDoomed(null);
      setReload((n) => n + 1);
    } catch (err) {
      notify(err instanceof Error ? err.message : 'Could not remove that house');
    } finally {
      setBusy(false);
    }
  }

  return (
    <div style={{ padding: '12px 20px 0' }}>
      <button
        onClick={() => navigate('/agent/voters')}
        style={{
          display: 'flex',
          alignItems: 'center',
          gap: 4,
          background: 'none',
          border: 'none',
          cursor: 'pointer',
          padding: 0,
          marginBottom: 8,
          fontFamily: fonts.sans,
          fontSize: 12,
          color: colors.inkSoft,
        }}
      >
        <ChevronLeft size={14} /> Change unit
      </button>

      <PageHeader
        title="Pre-election sentiment"
        subtitle="By house, not by name — no voter identity is recorded here."
        size={18}
        action={
          <Link to={formLink()} style={{ textDecoration: 'none' }}>
            <Button tone="accent" small icon={UserPlus}>
              Add
            </Button>
          </Link>
        }
      />

      {grant && (
        <p style={{ fontFamily: fonts.sans, fontSize: 11, color: colors.muted, margin: '0 0 12px' }}>
          {grant.boothName} · candidate {grant.candidateName}
        </p>
      )}

      {houses.error && <ErrorNote message={houses.error} />}

      {houses.loading && rows.length === 0 ? (
        <Spinner />
      ) : rows.length === 0 ? (
        <EmptyState
          title="No houses recorded yet"
          hint="Add a house to tally how many people there lean positive, neutral or negative."
          action={
            <Link to={formLink()} style={{ textDecoration: 'none' }}>
              <Button tone="accent" small icon={Home}>
                Add a house
              </Button>
            </Link>
          }
        />
      ) : (
        <>
          <p style={{ fontFamily: fonts.sans, fontSize: 12, fontWeight: 500, color: colors.ink, margin: '4px 0 8px' }}>
            Recorded so far
          </p>
          <div style={{ border: `1px solid ${colors.line}`, borderRadius: 8, overflow: 'hidden', marginBottom: 16 }}>
            {rows.map((house, i) => (
              <div
                key={house.id}
                style={{
                  display: 'flex',
                  alignItems: 'center',
                  justifyContent: 'space-between',
                  gap: 8,
                  padding: '10px 12px',
                  borderBottom: i < rows.length - 1 ? `1px solid ${colors.line}` : 'none',
                  background: colors.card,
                }}
              >
                <div style={{ minWidth: 0 }}>
                  <p style={{ fontFamily: fonts.sans, fontSize: 13, fontWeight: 500, color: colors.ink, margin: 0 }}>
                    House {house.houseNo} · {house.houseName}
                  </p>
                  <p style={{ fontFamily: fonts.sans, fontSize: 11, color: colors.inkSoft, margin: '2px 0 0' }}>
                    {house.headcount} people ({house.residentialCount} resid.) ·{' '}
                    <span style={{ color: colors.positive }}>{house.positiveCount}+</span>{' '}
                    <span style={{ color: colors.marigoldDeep }}>{house.neutralCount}~</span>{' '}
                    <span style={{ color: colors.negative }}>{house.negativeCount}−</span> ·{' '}
                    {house.confidence.toLowerCase()} conf.
                  </p>
                </div>
                <div style={{ display: 'flex', alignItems: 'center', gap: 10 }}>
                  <button
                    aria-label={`Edit house ${house.houseNo}`}
                    onClick={() => navigate(formLink(house))}
                    style={{ background: 'none', border: 'none', cursor: 'pointer', padding: 0 }}
                  >
                    <Edit3 size={13} color={colors.inkSoft} />
                  </button>
                  <button
                    aria-label={`Delete house ${house.houseNo}`}
                    onClick={() => setDoomed(house)}
                    style={{ background: 'none', border: 'none', cursor: 'pointer', padding: 0 }}
                  >
                    <Trash2 size={13} color={colors.negative} />
                  </button>
                </div>
              </div>
            ))}
          </div>
        </>
      )}

      <Modal open={doomed !== null} title="Remove this house?" onClose={() => setDoomed(null)} width={360}>
        <p style={{ fontFamily: fonts.sans, fontSize: 13, color: colors.inkSoft, margin: '0 0 16px' }}>
          House {doomed?.houseNo} · {doomed?.houseName} and its tally are deleted for good.
        </p>
        <div style={{ display: 'flex', gap: 8 }}>
          <Button tone="danger" loading={busy} onClick={() => void remove()}>
            Remove
          </Button>
          <Button tone="ghost" onClick={() => setDoomed(null)}>
            Cancel
          </Button>
        </div>
      </Modal>
    </div>
  );
}
