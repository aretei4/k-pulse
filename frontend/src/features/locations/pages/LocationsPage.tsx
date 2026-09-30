import { useMemo, useState } from 'react';
import { ChevronDown, ChevronRight, ChevronsDownUp, ChevronsUpDown, Pencil, Plus, Trash2 } from 'lucide-react';
import {
  Button,
  Card,
  EmptyState,
  ErrorNote,
  Field,
  Modal,
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
import type { Candidate, Unit, UnitLevel } from '@/shared/types';
import { locationsApi } from '../services/locationsApi';
import { candidatesApi } from '../services/candidatesApi';

const LEVELS: UnitLevel[] = ['DISTRICT', 'BLOCK', 'PANCHAYAT', 'BOOTH'];

const LEVEL_TONE: Record<UnitLevel, string> = {
  DISTRICT: colors.marigoldDeep,
  BLOCK: colors.ink,
  PANCHAYAT: colors.positive,
  BOOTH: colors.inkSoft,
};

const title = (level: UnitLevel) => level.charAt(0) + level.slice(1).toLowerCase();

/** The level a unit of this level sits under; districts sit at the top. */
const parentLevel = (level: UnitLevel): UnitLevel | null =>
  level === 'DISTRICT' ? null : LEVELS[LEVELS.indexOf(level) - 1];

export function LocationsPage() {
  const { notify } = useToast();
  const locations = useAsync(() => locationsApi.list(), []);
  const units = useMemo(() => locations.data ?? [], [locations.data]);

  const [editing, setEditing] = useState<Unit | null>(null);
  const [level, setLevel] = useState<UnitLevel>('DISTRICT');
  const [name, setName] = useState('');
  const [parentId, setParentId] = useState('');
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);
  const [removing, setRemoving] = useState<Unit | null>(null);

  const candidates = useAsync(() => candidatesApi.list(), []);
  const candidateRows = useMemo(() => candidates.data ?? [], [candidates.data]);
  const [editingCandidate, setEditingCandidate] = useState<Candidate | null>(null);
  const [candidateName, setCandidateName] = useState('');
  const [candidateParty, setCandidateParty] = useState('');
  const [candidateUnitId, setCandidateUnitId] = useState('');
  const [candidateError, setCandidateError] = useState<string | null>(null);
  const [candidateBusy, setCandidateBusy] = useState(false);
  const [removingCandidate, setRemovingCandidate] = useState<Candidate | null>(null);

  const above = parentLevel(level);
  const parentChoices = useMemo(
    () => (above ? units.filter((u) => u.level === above) : []),
    [units, above],
  );
  const nameOf = (id: string | null) => units.find((u) => u.id === id)?.name ?? '—';

  const [collapsed, setCollapsed] = useState<Set<string>>(new Set());

  /** Children by parent id, so the tree can be walked without rescanning the list. */
  const childrenOf = useMemo(() => {
    const ids = new Set(units.map((u) => u.id));
    const map = new Map<string | null, Unit[]>();
    units.forEach((unit) => {
      // A unit whose parent is missing would otherwise vanish; show it at the top.
      const key = unit.parentId && ids.has(unit.parentId) ? unit.parentId : null;
      map.set(key, [...(map.get(key) ?? []), unit]);
    });
    map.forEach((list) => list.sort((a, b) => a.name.localeCompare(b.name)));
    return map;
  }, [units]);

  /** The tree flattened to the rows currently on screen, skipping collapsed branches. */
  const visibleRows = useMemo(() => {
    const rows: { unit: Unit; depth: number; childCount: number }[] = [];
    const walk = (parentId: string | null, depth: number) => {
      for (const unit of childrenOf.get(parentId) ?? []) {
        const children = childrenOf.get(unit.id) ?? [];
        rows.push({ unit, depth, childCount: children.length });
        if (children.length > 0 && !collapsed.has(unit.id)) {
          walk(unit.id, depth + 1);
        }
      }
    };
    walk(null, 0);
    return rows;
  }, [childrenOf, collapsed]);

  const parentIds = useMemo(
    () => units.filter((u) => (childrenOf.get(u.id) ?? []).length > 0).map((u) => u.id),
    [units, childrenOf],
  );
  const allCollapsed = parentIds.length > 0 && parentIds.every((id) => collapsed.has(id));

  function toggle(id: string) {
    setCollapsed((prev) => {
      const next = new Set(prev);
      if (next.has(id)) {
        next.delete(id);
      } else {
        next.add(id);
      }
      return next;
    });
  }

  function reset() {
    setEditing(null);
    setName('');
    setParentId('');
    setError(null);
  }

  function edit(unit: Unit) {
    setEditing(unit);
    setLevel(unit.level);
    setName(unit.name);
    setParentId(unit.parentId ?? '');
    setError(null);
  }

  async function submit() {
    if (!name.trim()) {
      setError('Enter a name');
      return;
    }
    if (above && !parentId) {
      setError(`Pick the ${above.toLowerCase()} this ${level.toLowerCase()} belongs to`);
      return;
    }

    setBusy(true);
    setError(null);
    const payload = { level, name: name.trim(), parentId: above ? parentId : null };
    try {
      if (editing) {
        await locationsApi.update(editing.id, payload);
        notify('Location updated');
      } else {
        await locationsApi.create(payload);
        notify('Location added');
      }
      reset();
      locations.reload();
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Could not save the location');
    } finally {
      setBusy(false);
    }
  }

  async function remove(unit: Unit) {
    setBusy(true);
    try {
      await locationsApi.remove(unit.id);
      notify(`${unit.name} deleted`);
      setRemoving(null);
      if (editing?.id === unit.id) reset();
      locations.reload();
    } catch (err) {
      notify(err instanceof Error ? err.message : 'Could not delete the location', 'error');
      setRemoving(null);
    } finally {
      setBusy(false);
    }
  }

  const panchayats = useMemo(() => units.filter((u) => u.level === 'PANCHAYAT'), [units]);

  function resetCandidate() {
    setEditingCandidate(null);
    setCandidateName('');
    setCandidateParty('');
    setCandidateUnitId('');
    setCandidateError(null);
  }

  async function submitCandidate() {
    if (!candidateName.trim()) {
      setCandidateError('Enter the candidate name');
      return;
    }
    setCandidateBusy(true);
    setCandidateError(null);
    const payload = {
      name: candidateName.trim(),
      party: candidateParty.trim() || null,
      unitId: candidateUnitId || null,
    };
    try {
      if (editingCandidate) {
        await candidatesApi.update(editingCandidate.id, payload);
        notify('Candidate updated');
      } else {
        await candidatesApi.create(payload);
        notify('Candidate added');
      }
      resetCandidate();
      candidates.reload();
    } catch (err) {
      setCandidateError(err instanceof Error ? err.message : 'Could not save the candidate');
    } finally {
      setCandidateBusy(false);
    }
  }

  async function removeCandidate(candidate: Candidate) {
    setCandidateBusy(true);
    try {
      await candidatesApi.remove(candidate.id);
      notify(`${candidate.name} deleted`);
      setRemovingCandidate(null);
      if (editingCandidate?.id === candidate.id) resetCandidate();
      candidates.reload();
    } catch (err) {
      notify(err instanceof Error ? err.message : 'Could not delete the candidate', 'error');
      setRemovingCandidate(null);
    } finally {
      setCandidateBusy(false);
    }
  }

  return (
    <div>
      <PageHeader
        title="Locations"
        subtitle="Manage the District → Block → Panchayat → Booth hierarchy used across the app."
        action={
          units.length > 0 && (
            <Button
              tone="ghost"
              icon={allCollapsed ? ChevronsDownUp : ChevronsUpDown}
              onClick={() => setCollapsed(allCollapsed ? new Set() : new Set(parentIds))}
            >
              {allCollapsed ? 'Expand all' : 'Collapse all'}
            </Button>
          )
        }
      />

      <Card padding={16}>
        {error && (
          <p style={{ fontFamily: fonts.sans, fontSize: 12, color: colors.negative, margin: '0 0 10px' }}>{error}</p>
        )}
        <div style={{ display: 'flex', gap: 12, flexWrap: 'wrap', alignItems: 'flex-start' }}>
          <div style={{ width: 150 }}>
            <Select
              label="Level"
              value={level}
              // The level fixes which rows can be its parent, so a change resets that choice.
              onChange={(v) => {
                setLevel(v as UnitLevel);
                setParentId('');
              }}
              disabled={editing !== null}
              options={LEVELS.map((l) => ({ value: l, label: title(l) }))}
            />
          </div>
          <div style={{ flex: 1, minWidth: 200 }}>
            <Field label="Name" placeholder="e.g. Booth 63" value={name} onChange={setName} />
          </div>
          <div style={{ width: 190 }}>
            <Select
              label={above ? `Parent ${above.toLowerCase()}` : 'Parent'}
              value={parentId}
              onChange={setParentId}
              disabled={above === null}
              placeholder={above === null ? 'None — top level' : `Choose a ${above.toLowerCase()}`}
              options={parentChoices.map((u) => ({ value: u.id, label: u.name }))}
            />
          </div>
          <div style={{ display: 'flex', gap: 8, paddingTop: 22 }}>
            <Button tone="accent" icon={editing ? Pencil : Plus} loading={busy} onClick={() => void submit()}>
              {editing ? 'Save changes' : 'Add'}
            </Button>
            {editing && (
              <Button tone="ghost" onClick={reset}>
                Cancel
              </Button>
            )}
          </div>
        </div>
        {editing && (
          <p style={{ fontFamily: fonts.sans, fontSize: 11, color: colors.muted, margin: '10px 0 0' }}>
            Editing {editing.path}. A location's level cannot be changed.
          </p>
        )}
      </Card>

      <div style={{ height: 16 }} />

      {locations.error && <ErrorNote message={locations.error} />}

      {locations.loading && units.length === 0 ? (
        <Spinner />
      ) : units.length === 0 ? (
        <EmptyState title="No locations yet" hint="Add a district first, then blocks, panchayats and booths." />
      ) : (
        <RowList>
          {visibleRows.map(({ unit, depth, childCount }, i) => {
            const isCollapsed = collapsed.has(unit.id);
            return (
              <Row key={unit.id} last={i === visibleRows.length - 1}>
                <div style={{ display: 'flex', alignItems: 'center', gap: 10, paddingLeft: depth * 22 }}>
                  {childCount > 0 ? (
                    <button
                      type="button"
                      onClick={() => toggle(unit.id)}
                      aria-expanded={!isCollapsed}
                      aria-label={`${isCollapsed ? 'Expand' : 'Collapse'} ${unit.name}`}
                      style={{
                        display: 'flex',
                        alignItems: 'center',
                        justifyContent: 'center',
                        width: 20,
                        height: 20,
                        border: 'none',
                        borderRadius: 4,
                        background: 'none',
                        cursor: 'pointer',
                        color: colors.inkSoft,
                      }}
                    >
                      {isCollapsed ? <ChevronRight size={15} /> : <ChevronDown size={15} />}
                    </button>
                  ) : (
                    // Keeps leaf rows lined up with their expandable siblings.
                    <span style={{ width: 20 }} />
                  )}
                  <Tag color={LEVEL_TONE[unit.level]}>{title(unit.level)}</Tag>
                  <p style={{ fontFamily: fonts.sans, fontSize: 13, fontWeight: 500, color: colors.ink, margin: 0 }}>
                    {unit.name}
                  </p>
                  {isCollapsed && childCount > 0 && (
                    <span style={{ fontFamily: fonts.sans, fontSize: 11, color: colors.muted }}>
                      {childCount} hidden
                    </span>
                  )}
                </div>
                <div style={{ display: 'flex', alignItems: 'center', gap: 14 }}>
                  <span style={{ fontFamily: fonts.sans, fontSize: 12, color: colors.inkSoft }}>
                    Under: {nameOf(unit.parentId)}
                  </span>
                  <Button tone="ghost" small icon={Pencil} onClick={() => edit(unit)}>
                    Edit
                  </Button>
                  <Button tone="ghost" small icon={Trash2} onClick={() => setRemoving(unit)}>
                    Delete
                  </Button>
                </div>
              </Row>
            );
          })}
        </RowList>
      )}

      <div style={{ height: 30 }} />

      <PageHeader
        title="Candidates"
        subtitle="Agents request access for one candidate, and every sentiment entry is recorded toward them."
      />

      <Card padding={16}>
        {candidateError && (
          <p style={{ fontFamily: fonts.sans, fontSize: 12, color: colors.negative, margin: '0 0 10px' }}>
            {candidateError}
          </p>
        )}
        <div style={{ display: 'flex', gap: 12, flexWrap: 'wrap', alignItems: 'flex-start' }}>
          <div style={{ flex: 1, minWidth: 200 }}>
            <Field label="Name" placeholder="e.g. R. Mohanty" value={candidateName} onChange={setCandidateName} />
          </div>
          <div style={{ flex: 1, minWidth: 170 }}>
            <Field
              label="Party (optional)"
              placeholder="e.g. Independent"
              value={candidateParty}
              onChange={setCandidateParty}
            />
          </div>
          <div style={{ width: 200 }}>
            <Select
              label="Panchayat (optional)"
              value={candidateUnitId}
              onChange={setCandidateUnitId}
              placeholder="Whole constituency"
              options={panchayats.map((u) => ({ value: u.id, label: u.name }))}
            />
          </div>
          <div style={{ display: 'flex', gap: 8, paddingTop: 22 }}>
            <Button
              tone="accent"
              icon={editingCandidate ? Pencil : Plus}
              loading={candidateBusy}
              onClick={() => void submitCandidate()}
            >
              {editingCandidate ? 'Save changes' : 'Add'}
            </Button>
            {editingCandidate && (
              <Button tone="ghost" onClick={resetCandidate}>
                Cancel
              </Button>
            )}
          </div>
        </div>
      </Card>

      <div style={{ height: 16 }} />

      {candidates.error && <ErrorNote message={candidates.error} />}

      {candidates.loading && candidateRows.length === 0 ? (
        <Spinner />
      ) : candidateRows.length === 0 ? (
        <EmptyState title="No candidates yet" hint="Agents cannot request access until at least one exists." />
      ) : (
        <RowList>
          {candidateRows.map((candidate, i) => (
            <Row key={candidate.id} last={i === candidateRows.length - 1}>
              <div style={{ display: 'flex', alignItems: 'center', gap: 10 }}>
                <Tag color={colors.marigoldDeep}>Candidate</Tag>
                <p style={{ fontFamily: fonts.sans, fontSize: 13, fontWeight: 500, color: colors.ink, margin: 0 }}>
                  {candidate.name}
                </p>
              </div>
              <div style={{ display: 'flex', alignItems: 'center', gap: 14 }}>
                <span style={{ fontFamily: fonts.sans, fontSize: 12, color: colors.inkSoft }}>
                  {candidate.party ?? 'No party'} · {candidate.unitName ?? 'Whole constituency'}
                </span>
                <Button
                  tone="ghost"
                  small
                  icon={Pencil}
                  onClick={() => {
                    setEditingCandidate(candidate);
                    setCandidateName(candidate.name);
                    setCandidateParty(candidate.party ?? '');
                    setCandidateUnitId(candidate.unitId ?? '');
                    setCandidateError(null);
                  }}
                >
                  Edit
                </Button>
                <Button tone="ghost" small icon={Trash2} onClick={() => setRemovingCandidate(candidate)}>
                  Delete
                </Button>
              </div>
            </Row>
          ))}
        </RowList>
      )}

      <Modal
        open={removingCandidate !== null}
        title={`Delete ${removingCandidate?.name ?? ''}?`}
        subtitle={removingCandidate?.party ?? undefined}
        onClose={() => setRemovingCandidate(null)}
      >
        <p style={{ fontFamily: fonts.sans, fontSize: 13, color: colors.inkSoft, margin: '0 0 14px' }}>
          A candidate can only be deleted while nothing points at them — no sentiment recorded toward them, and no
          access request naming them.
        </p>
        <div style={{ display: 'flex', gap: 8 }}>
          <Button
            tone="accent"
            loading={candidateBusy}
            onClick={() => removingCandidate && void removeCandidate(removingCandidate)}
          >
            Delete
          </Button>
          <Button tone="ghost" onClick={() => setRemovingCandidate(null)}>
            Cancel
          </Button>
        </div>
      </Modal>

      <Modal
        open={removing !== null}
        title={`Delete ${removing?.name ?? ''}?`}
        subtitle={removing?.path}
        onClose={() => setRemoving(null)}
      >
        <p style={{ fontFamily: fonts.sans, fontSize: 13, color: colors.inkSoft, margin: '0 0 14px' }}>
          A location can only be deleted once nothing uses it — no locations under it, and no voters, access requests,
          roll uploads or change requests pointing at it.
        </p>
        <div style={{ display: 'flex', gap: 8 }}>
          <Button tone="accent" loading={busy} onClick={() => removing && void remove(removing)}>
            Delete
          </Button>
          <Button tone="ghost" onClick={() => setRemoving(null)}>
            Cancel
          </Button>
        </div>
      </Modal>
    </div>
  );
}
