import { useEffect, useMemo, useState } from 'react';
import { Download, Search } from 'lucide-react';
import {
  Button,
  EmptyState,
  ErrorNote,
  SearchBox,
  Select,
  Spinner,
  Tag,
  useToast,
} from '@/shared/components';
import { PageHeader } from '@/shared/layouts/PageHeader';
import { useAsync } from '@/shared/hooks/useAsync';
import { useAuth } from '@/shared/hooks/useAuth';
import { useDebounced } from '@/shared/hooks/useDebounced';
import { downloadBlob } from '@/lib/apiClient';
import { colors, fonts } from '@/shared/theme';
import type { AgentAccessState, AreaCount, FieldAgent, PaymentMode } from '@/shared/types';
import { fieldAgentsApi } from '../services/fieldAgentsApi';
import { AccessPill } from '../components/AccessPill';
import { AgentDetailPanel } from '../components/AgentDetailPanel';

type Tab = 'ALL' | 'PAID' | 'UNPAID';

const accessOptions = [
  { value: '', label: 'Any access' },
  { value: 'APPROVED', label: 'Approved' },
  { value: 'PENDING', label: 'Pending' },
  { value: 'EXPIRED', label: 'Expired' },
  { value: 'NONE', label: 'No access' },
];

const sizeOptions = [25, 50, 100].map((n) => ({ value: String(n), label: `${n} per page` }));

/** FA-25: who has the Reports page in the app. */
const reportsOptions = [
  { value: '', label: 'Any reports' },
  { value: 'true', label: 'Reports on' },
  { value: 'false', label: 'Reports off' },
];

const number = (value: number) => value.toLocaleString('en-IN');

/** The option list for one geography level, with its counts (FA-3). */
function areaChoices(options: AreaCount[] | null, placeholder: string) {
  return [
    { value: '', label: placeholder },
    ...(options ?? []).map((option) => ({ value: option.unitId, label: `${option.name} (${number(option.agents)})` })),
  ];
}

function Cell({ children, width, align }: { children: React.ReactNode; width?: number; align?: 'right' }) {
  return (
    <td
      style={{
        padding: '10px 10px',
        borderBottom: `1px solid ${colors.line}`,
        fontFamily: fonts.sans,
        fontSize: 12,
        color: colors.ink,
        width,
        textAlign: align,
        verticalAlign: 'top',
      }}
    >
      {children}
    </td>
  );
}

function Head({
  children,
  onClick,
  arrow,
  align,
}: {
  children: React.ReactNode;
  onClick?: () => void;
  arrow?: string;
  align?: 'right';
}) {
  return (
    <th
      onClick={onClick}
      style={{
        padding: '8px 10px',
        textAlign: align ?? 'left',
        fontFamily: fonts.sans,
        fontSize: 10,
        letterSpacing: '0.08em',
        textTransform: 'uppercase',
        color: colors.muted,
        fontWeight: 600,
        borderBottom: `1px solid ${colors.line}`,
        cursor: onClick ? 'pointer' : 'default',
        userSelect: 'none',
      }}
    >
      {children}
      {arrow}
    </th>
  );
}

/**
 * FR-A20. Every field agent in the admin's area, with their payment status.
 *
 * <p>Built for ten thousand agents: nothing is filtered, sorted, counted or
 * paged in the browser (FA-N2). Each change of a filter is a new request, and
 * the selection is dropped with it — a tick made against one list must not
 * carry over to a different one.
 */
export function FieldAgentsPage() {
  const { notify } = useToast();
  const { user } = useAuth();

  const [districtId, setDistrictId] = useState('');
  const [blockId, setBlockId] = useState('');
  const [panchayatId, setPanchayatId] = useState('');
  const [access, setAccess] = useState('');
  const [reports, setReports] = useState('');
  const [search, setSearch] = useState('');
  const [tab, setTab] = useState<Tab>('ALL');
  const [sort, setSort] = useState<'NAME' | 'VOTERS'>('NAME');
  const [desc, setDesc] = useState(false);
  const [page, setPage] = useState(0);
  const [size, setSize] = useState(25);
  const [selected, setSelected] = useState<string[]>([]);
  const [allMatching, setAllMatching] = useState(false);
  const [open, setOpen] = useState<FieldAgent | null>(null);
  const [bulkPaying, setBulkPaying] = useState(false);
  const [bulkAmount, setBulkAmount] = useState('1500');
  const [bulkMode, setBulkMode] = useState<PaymentMode>('UPI');
  const [busy, setBusy] = useState(false);
  const [exporting, setExporting] = useState(false);

  const debouncedSearch = useDebounced(search);
  const paid = tab === 'ALL' ? undefined : tab === 'PAID';

  const filters = useMemo(
    () => ({
      districtId: districtId || undefined,
      blockId: blockId || undefined,
      panchayatId: panchayatId || undefined,
      access: (access || undefined) as AgentAccessState | undefined,
      reports: reports === '' ? undefined : reports === 'true',
      q: debouncedSearch || undefined,
    }),
    [districtId, blockId, panchayatId, access, reports, debouncedSearch],
  );

  const agents = useAsync(
    () => fieldAgentsApi.list({ ...filters, paid, sort, desc, page, size }),
    [filters, paid, sort, desc, page, size],
  );

  const districts = useAsync(() => fieldAgentsApi.areas('DISTRICT'), []);
  const blocks = useAsync(
    () => (districtId ? fieldAgentsApi.areas('BLOCK', districtId) : Promise.resolve([])),
    [districtId],
  );
  const panchayats = useAsync(
    () => (blockId ? fieldAgentsApi.areas('PANCHAYAT', blockId) : Promise.resolve([])),
    [blockId],
  );

  // FA-13: a selection belongs to the list it was made in.
  useEffect(() => {
    setSelected([]);
    setAllMatching(false);
  }, [filters, paid, sort, desc, page, size]);

  // FA-2: any change of filter goes back to the first page.
  useEffect(() => {
    setPage(0);
  }, [filters, paid, size]);

  const rows = agents.data?.items ?? [];
  const total = agents.data?.total ?? 0;
  const pageSelected = rows.length > 0 && rows.every((agent) => selected.includes(agent.id));
  const selectionCount = allMatching ? total : selected.length;
  const filtered = Boolean(
    districtId || blockId || panchayatId || access || reports || debouncedSearch || tab !== 'ALL',
  );
  const scoped = Boolean(user?.scopeUnitId);

  function sortBy(column: 'NAME' | 'VOTERS') {
    if (sort === column) setDesc(!desc);
    else {
      setSort(column);
      setDesc(column === 'VOTERS');
    }
    setPage(0);
  }

  function clearFilters() {
    setDistrictId('');
    setBlockId('');
    setPanchayatId('');
    setAccess('');
    setReports('');
    setSearch('');
    setTab('ALL');
  }

  async function markSelection(markPaid: boolean) {
    const amount = Number(bulkAmount);
    if (markPaid && !(amount > 0)) return notify('Enter the amount each agent was paid', 'error');

    setBusy(true);
    try {
      const result = await fieldAgentsApi.markMany({
        selection: allMatching ? 'ALL_MATCHING' : 'IDS',
        ids: allMatching ? undefined : selected,
        districtId: filters.districtId,
        blockId: filters.blockId,
        panchayatId: filters.panchayatId,
        access: filters.access,
        paidFilter: paid,
        search: filters.q,
        paid: markPaid,
        amount: markPaid ? amount : undefined,
        paidOn: markPaid ? new Date().toISOString().slice(0, 10) : undefined,
        mode: markPaid ? bulkMode : undefined,
      });
      notify(
        `${number(result.agents)} agent(s) marked as ${result.paid ? 'paid' : 'not paid'}` +
          (result.total ? ` · ₹${number(result.total)}` : ''),
      );
      setSelected([]);
      setAllMatching(false);
      setBulkPaying(false);
      agents.reload();
    } catch (err) {
      notify(err instanceof Error ? err.message : 'Could not change the payment status', 'error');
    } finally {
      setBusy(false);
    }
    return undefined;
  }

  /** AD-4: the ticked rows, or every agent the filters match. */
  async function setReportsForSelection(enabled: boolean) {
    setBusy(true);
    try {
      const result = await fieldAgentsApi.setReportsAccessMany({
        selection: allMatching ? 'ALL_MATCHING' : 'IDS',
        ids: allMatching ? undefined : selected,
        districtId: filters.districtId,
        blockId: filters.blockId,
        panchayatId: filters.panchayatId,
        access: filters.access,
        paidFilter: paid,
        reportsFilter: filters.reports,
        search: filters.q,
        enabled,
      });
      notify(
        `${number(result.agents)} agent(s) ${result.enabled ? 'given' : 'no longer have'} the Reports page`,
      );
      setSelected([]);
      setAllMatching(false);
      agents.reload();
    } catch (err) {
      notify(err instanceof Error ? err.message : 'Could not change reports access', 'error');
    } finally {
      setBusy(false);
    }
  }

  async function exportCsv() {
    setExporting(true);
    try {
      const blob = await fieldAgentsApi.exportCsv({ ...filters, paid, sort, desc });
      downloadBlob(blob, `field-agents-${new Date().toISOString().slice(0, 10)}.csv`);
    } catch (err) {
      notify(err instanceof Error ? err.message : 'Could not build the export', 'error');
    } finally {
      setExporting(false);
    }
  }

  const tabs: Array<{ id: Tab; label: string; count: number | undefined }> = [
    { id: 'ALL', label: 'All', count: agents.data?.allCount },
    { id: 'PAID', label: 'Paid', count: agents.data?.paidCount },
    { id: 'UNPAID', label: 'Not paid', count: agents.data?.unpaidCount },
  ];

  return (
    <div style={{ display: 'flex', gap: 16, alignItems: 'flex-start' }}>
      <div style={{ flex: 1, minWidth: 0 }}>
        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'start', gap: 12 }}>
          <PageHeader
            title="Field agents"
            subtitle={
              agents.data
                ? `${number(agents.data.allCount)} agents.${
                    scoped ? '' : ' Choose a district and block to narrow the list.'
                  }`
                : 'Loading…'
            }
          />
          <Button tone="ghost" small icon={Download} loading={exporting} onClick={() => void exportCsv()}>
            Export CSV
          </Button>
        </div>

        <div style={{ display: 'flex', gap: 10, flexWrap: 'wrap' }}>
          <Select
            label="District"
            options={areaChoices(districts.data, `All districts${agents.data ? ` (${number(agents.data.allCount)})` : ''}`)}
            value={districtId}
            onChange={(value) => {
              setDistrictId(value);
              setBlockId('');
              setPanchayatId('');
            }}
            style={{ minWidth: 200, flex: 1 }}
          />
          <Select
            label="Block"
            options={areaChoices(blocks.data, districtId ? 'All blocks' : 'Pick a district first')}
            value={blockId}
            disabled={!districtId}
            onChange={(value) => {
              setBlockId(value);
              setPanchayatId('');
            }}
            style={{ minWidth: 200, flex: 1 }}
          />
          <Select
            label="Panchayat"
            options={areaChoices(panchayats.data, blockId ? 'All panchayats' : 'Pick a block first')}
            value={panchayatId}
            disabled={!blockId}
            onChange={setPanchayatId}
            style={{ minWidth: 200, flex: 1 }}
          />
        </div>

        <div style={{ display: 'flex', gap: 10, alignItems: 'end', flexWrap: 'wrap' }}>
          <Select
            label="Access"
            options={accessOptions}
            value={access}
            onChange={setAccess}
            style={{ minWidth: 160 }}
          />
          <Select
            label="Reports"
            options={reportsOptions}
            value={reports}
            onChange={setReports}
            style={{ minWidth: 150 }}
          />
          <div style={{ flex: 1, minWidth: 220, marginBottom: 12 }}>
            <SearchBox
              value={search}
              onChange={setSearch}
              placeholder="Search name, mobile, agent ID or referral"
              icon={Search}
            />
          </div>
          {filtered && (
            <button
              type="button"
              onClick={clearFilters}
              style={{
                background: 'none',
                border: 'none',
                cursor: 'pointer',
                marginBottom: 20,
                fontFamily: fonts.sans,
                fontSize: 12,
                color: colors.marigoldDeep,
                textDecoration: 'underline',
              }}
            >
              Clear filters
            </button>
          )}
        </div>

        {/* FA-7: counts for the filters as they stand, so the two always add up to All. */}
        <div style={{ display: 'inline-flex', border: `1px solid ${colors.line}`, borderRadius: 8, overflow: 'hidden' }}>
          {tabs.map((item) => {
            const on = tab === item.id;
            return (
              <button
                key={item.id}
                type="button"
                onClick={() => setTab(item.id)}
                style={{
                  padding: '7px 14px',
                  border: 'none',
                  cursor: 'pointer',
                  fontFamily: fonts.sans,
                  fontSize: 12,
                  fontWeight: on ? 600 : 400,
                  background: on ? colors.ink : 'transparent',
                  color: on ? colors.paper : colors.inkSoft,
                }}
              >
                {item.label} {item.count === undefined ? '' : number(item.count)}
              </button>
            );
          })}
        </div>

        {selectionCount > 0 && (
          <div
            style={{
              marginTop: 10,
              padding: 10,
              borderRadius: 8,
              background: colors.marigoldWash,
              border: `1px solid ${colors.line}`,
              display: 'flex',
              gap: 10,
              alignItems: 'center',
              flexWrap: 'wrap',
            }}
          >
            <span style={{ fontFamily: fonts.sans, fontSize: 12, color: colors.ink }}>
              {number(selectionCount)} selected
            </span>
            {bulkPaying ? (
              <>
                <input
                  value={bulkAmount}
                  onChange={(e) => setBulkAmount(e.target.value)}
                  aria-label="Amount each"
                  style={{
                    width: 90,
                    padding: '6px 8px',
                    borderRadius: 6,
                    border: `1px solid ${colors.line}`,
                    fontFamily: fonts.sans,
                    fontSize: 12,
                  }}
                />
                <select
                  value={bulkMode}
                  onChange={(e) => setBulkMode(e.target.value as PaymentMode)}
                  aria-label="Mode"
                  style={{
                    padding: '6px 8px',
                    borderRadius: 6,
                    border: `1px solid ${colors.line}`,
                    fontFamily: fonts.sans,
                    fontSize: 12,
                  }}
                >
                  <option value="UPI">UPI</option>
                  <option value="BANK">Bank transfer</option>
                  <option value="CASH">Cash</option>
                </select>
                <span style={{ fontFamily: fonts.sans, fontSize: 12, color: colors.inkSoft }}>
                  {number(selectionCount)} agents · ₹{number(selectionCount * (Number(bulkAmount) || 0))}
                </span>
                <Button tone="accent" small loading={busy} onClick={() => void markSelection(true)}>
                  Confirm
                </Button>
                <Button tone="ghost" small onClick={() => setBulkPaying(false)}>
                  Cancel
                </Button>
              </>
            ) : (
              <>
                <Button small onClick={() => setBulkPaying(true)}>
                  Mark paid
                </Button>
                <Button tone="ghost" small loading={busy} onClick={() => void markSelection(false)}>
                  Mark not paid
                </Button>
                <Button small loading={busy} onClick={() => void setReportsForSelection(true)}>
                  Give reports access
                </Button>
                <Button tone="ghost" small loading={busy} onClick={() => void setReportsForSelection(false)}>
                  Remove reports access
                </Button>
                <Button
                  tone="ghost"
                  small
                  onClick={() => {
                    setSelected([]);
                    setAllMatching(false);
                  }}
                >
                  Clear
                </Button>
              </>
            )}
          </div>
        )}

        {pageSelected && !allMatching && total > rows.length && (
          <button
            type="button"
            onClick={() => setAllMatching(true)}
            style={{
              marginTop: 8,
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
            Select all {number(total)} matching
          </button>
        )}

        {agents.error && <ErrorNote message={agents.error} />}

        <div
          style={{
            marginTop: 10,
            background: colors.card,
            border: `1px solid ${colors.line}`,
            borderRadius: 10,
            overflow: 'hidden',
          }}
        >
          <table style={{ width: '100%', borderCollapse: 'collapse' }}>
            <thead>
              <tr>
                <Head>
                  <input
                    type="checkbox"
                    aria-label="Select this page"
                    checked={pageSelected}
                    onChange={(e) => {
                      setAllMatching(false);
                      setSelected(e.target.checked ? rows.map((agent) => agent.id) : []);
                    }}
                  />
                </Head>
                <Head onClick={() => sortBy('NAME')} arrow={sort === 'NAME' ? (desc ? ' ↓' : ' ↑') : ''}>
                  Agent
                </Head>
                <Head>Area</Head>
                <Head>Access</Head>
                <Head onClick={() => sortBy('VOTERS')} align="right" arrow={sort === 'VOTERS' ? (desc ? ' ↓' : ' ↑') : ''}>
                  Voters
                </Head>
                <Head>Payment</Head>
              </tr>
            </thead>
            <tbody>
              {rows.map((agent) => (
                <tr
                  key={agent.id}
                  onClick={() => setOpen(agent)}
                  style={{ cursor: 'pointer', background: open?.id === agent.id ? colors.paper : undefined }}
                >
                  <Cell width={34}>
                    <input
                      type="checkbox"
                      aria-label={`Select ${agent.name}`}
                      checked={allMatching || selected.includes(agent.id)}
                      onClick={(e) => e.stopPropagation()}
                      onChange={(e) => {
                        setAllMatching(false);
                        setSelected((ids) =>
                          e.target.checked ? [...ids, agent.id] : ids.filter((id) => id !== agent.id),
                        );
                      }}
                    />
                  </Cell>
                  <Cell>
                    <span style={{ color: colors.marigoldDeep, fontWeight: 500 }}>{agent.name}</span>
                    <div style={{ color: colors.muted, fontSize: 11, marginTop: 2 }}>
                      {agent.phone ?? agent.agentCode}
                      {/* FA-24: spotting the agents waiting on a password, among thousands. */}
                      {agent.passwordResetRequestedAt && (
                        <span style={{ marginLeft: 6 }}>
                          <Tag color={colors.marigoldDeep}>Reset asked</Tag>
                        </span>
                      )}
                      {agent.reportsEnabled && (
                        <span style={{ marginLeft: 6 }}>
                          <Tag color={colors.positive}>Reports</Tag>
                        </span>
                      )}
                    </div>
                  </Cell>
                  <Cell>
                    {agent.panchayatName ?? '—'}
                    <div style={{ color: colors.muted, fontSize: 11, marginTop: 2 }}>
                      {[agent.blockName, agent.districtName].filter(Boolean).join(' · ')}
                    </div>
                  </Cell>
                  <Cell>
                    <AccessPill access={agent.access} />
                  </Cell>
                  <Cell align="right">{number(agent.votersRecorded)}</Cell>
                  <Cell>
                    <Tag color={agent.paid ? colors.positive : colors.marigoldDeep}>
                      {agent.paid ? 'Paid' : 'Not paid'}
                    </Tag>
                  </Cell>
                </tr>
              ))}
            </tbody>
          </table>

          {agents.loading && rows.length === 0 && <Spinner />}
          {!agents.loading && rows.length === 0 && (
            <EmptyState
              title="No agents match these filters"
              hint={filtered ? 'Clear the filters to see everyone in your area.' : 'No field agents yet.'}
              action={
                filtered ? (
                  <Button tone="ghost" small onClick={clearFilters}>
                    Clear filters
                  </Button>
                ) : undefined
              }
            />
          )}
        </div>

        <div
          style={{
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'space-between',
            gap: 10,
            marginTop: 10,
            flexWrap: 'wrap',
          }}
        >
          <span style={{ fontFamily: fonts.sans, fontSize: 12, color: colors.muted }}>
            {total === 0
              ? 'Nothing to show'
              : `Showing ${number(page * size + 1)}–${number(Math.min((page + 1) * size, total))} of ${number(total)}`}
          </span>
          <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
            <Select
              options={sizeOptions}
              value={String(size)}
              onChange={(value) => setSize(Number(value))}
              style={{ marginBottom: 0, width: 130 }}
            />
            <Button tone="ghost" small disabled={page === 0} onClick={() => setPage((p) => Math.max(0, p - 1))}>
              Previous
            </Button>
            <span style={{ fontFamily: fonts.sans, fontSize: 12, color: colors.inkSoft }}>
              Page {page + 1} of {Math.max(1, agents.data?.totalPages ?? 1)}
            </span>
            <Button
              tone="ghost"
              small
              disabled={page + 1 >= (agents.data?.totalPages ?? 1)}
              onClick={() => setPage((p) => p + 1)}
            >
              Next
            </Button>
          </div>
        </div>
      </div>

      {open && (
        <AgentDetailPanel
          agent={open}
          onClose={() => setOpen(null)}
          onChanged={() => {
            agents.reload();
          }}
        />
      )}
    </div>
  );
}
