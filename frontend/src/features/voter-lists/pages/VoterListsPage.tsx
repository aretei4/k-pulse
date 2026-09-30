import { useMemo, useRef, useState } from 'react';
import { Download, Edit3, FileSpreadsheet, Trash2, UploadCloud, UserPlus } from 'lucide-react';
import {
  Button,
  Card,
  EmptyState,
  ErrorNote,
  Field,
  Modal,
  Row,
  RowList,
  SearchBox,
  Select,
  SentimentPill,
  Spinner,
  useToast,
} from '@/shared/components';
import { Search } from 'lucide-react';
import { PageHeader } from '@/shared/layouts/PageHeader';
import { useAsync } from '@/shared/hooks/useAsync';
import { useDebounced } from '@/shared/hooks/useDebounced';
import { useUnitTree } from '@/shared/hooks/useUnitTree';
import { colors, fonts } from '@/shared/theme';
import { formatDate } from '@/shared/utils/format';
import type { Voter } from '@/shared/types';
import { voterListsApi } from '../services/voterListsApi';
import { VoterFormModal } from '../components/VoterFormModal';
import type { VoterFormValues } from '../components/VoterFormModal';

// The columns the rolls arrive in. Relation and Relation_Name are stored as one
// line; Assembly Part is accepted so the sheet imports unchanged, but not stored.
const TEMPLATE_HEADER = 'house_no,Name,Relation,Relation_Name,Age,Gender,Assembly Part,Epic_no,Ward_No';
const TEMPLATE_SAMPLE = '87,Sabitri Nayak,W/O,Ranjit Nayak,39,F,40/117,ODA1234567,9';

export function VoterListsPage() {
  const { notify } = useToast();
  const tree = useUnitTree();
  const fileInput = useRef<HTMLInputElement>(null);

  const [panchayatId, setPanchayatId] = useState('');
  const [boothId, setBoothId] = useState('');
  const [search, setSearch] = useState('');
  const [page, setPage] = useState(0);
  const [uploading, setUploading] = useState(false);
  const [editing, setEditing] = useState<Voter | null>(null);
  const [adding, setAdding] = useState(false);
  const [saving, setSaving] = useState(false);
  const [clearing, setClearing] = useState(false);
  const [confirmClear, setConfirmClear] = useState(false);
  const [typedBoothName, setTypedBoothName] = useState('');

  const debouncedSearch = useDebounced(search);

  const panchayats = tree.byLevel('PANCHAYAT');
  const boothChoices = useMemo(
    () => (panchayatId ? tree.boothsUnder(panchayatId) : tree.byLevel('BOOTH')),
    [panchayatId, tree],
  );

  const voters = useAsync(
    () => voterListsApi.list({ boothId: boothId || undefined, search: debouncedSearch || undefined, page, size: 20 }),
    [boothId, debouncedSearch, page],
  );
  const uploads = useAsync(() => voterListsApi.uploads(), []);

  const uploadTargetId = boothId || panchayatId;
  const boothName = boothChoices.find((b) => b.id === boothId)?.name ?? 'this booth';
  // The list is already filtered to the booth, so with no search term its total
  // is the size of the whole roll there.
  const boothVoterCount = debouncedSearch ? null : voters.data?.totalElements ?? null;

  async function handleFile(file: File) {
    if (!uploadTargetId) {
      notify('Pick the panchayat or booth this roll belongs to first', 'error');
      return;
    }
    if (!/\.(csv|xlsx|xls)$/i.test(file.name)) {
      notify('Upload the roll as .csv, .xlsx or .xls', 'error');
      return;
    }
    setUploading(true);
    try {
      const result = await voterListsApi.import(file, uploadTargetId);
      notify(`${result.fileName} imported · ${result.rowCount.toLocaleString('en-IN')} rows`);
      uploads.reload();
      voters.reload();
    } catch (err) {
      notify(err instanceof Error ? err.message : 'Upload failed', 'error');
    } finally {
      setUploading(false);
      if (fileInput.current) fileInput.current.value = '';
    }
  }

  async function clearBooth() {
    if (!boothId) return;
    setClearing(true);
    try {
      const result = await voterListsApi.clearBooth(boothId);
      notify(
        `${result.boothName} cleared · ${result.voters.toLocaleString('en-IN')} voters and ` +
          `${result.sentimentEntries.toLocaleString('en-IN')} sentiment entries deleted`,
      );
      setConfirmClear(false);
      setTypedBoothName('');
      setPage(0);
      voters.reload();
    } catch (err) {
      notify(err instanceof Error ? err.message : 'Could not clear the booth', 'error');
    } finally {
      setClearing(false);
    }
  }

  function downloadTemplate() {
    const blob = new Blob([`\ufeff${TEMPLATE_HEADER}\n${TEMPLATE_SAMPLE}\n`], {
      type: 'text/csv;charset=utf-8',
    });
    const url = URL.createObjectURL(blob);
    const a = document.createElement('a');
    a.href = url;
    a.download = 'k-pulse-voter-template.csv';
    a.click();
    URL.revokeObjectURL(url);
  }

  async function saveVoter(values: VoterFormValues) {
    setSaving(true);
    try {
      if (editing) {
        await voterListsApi.update(editing.id, values);
        notify('Voter updated');
      } else {
        if (!boothId) {
          notify('Pick a booth before adding a voter', 'error');
          return;
        }
        await voterListsApi.create({ ...values, boothId });
        notify('Voter added');
      }
      setEditing(null);
      setAdding(false);
      voters.reload();
    } catch (err) {
      notify(err instanceof Error ? err.message : 'Could not save the voter', 'error');
    } finally {
      setSaving(false);
    }
  }

  async function removeVoter(voter: Voter) {
    if (!window.confirm(`Delete ${voter.name} from the roll? This cannot be undone.`)) return;
    try {
      await voterListsApi.remove(voter.id);
      notify('Voter removed');
      voters.reload();
    } catch (err) {
      notify(err instanceof Error ? err.message : 'Could not delete', 'error');
    }
  }

  const rows = voters.data?.content ?? [];
  const uploadRows = uploads.data ?? [];

  return (
    <div>
      <PageHeader
        title="Voter lists"
        subtitle="Upload one roll file per booth or panchayat. Field agents only ever see what they're approved for."
        action={
          <div style={{ display: 'flex', gap: 8 }}>
            {boothId && (
              <Button tone="danger" icon={Trash2} onClick={() => setConfirmClear(true)}>
                Delete all in booth
              </Button>
            )}
            <Button tone="ghost" icon={Download} onClick={downloadTemplate}>
              Template
            </Button>
            <Button tone="accent" icon={UserPlus} onClick={() => setAdding(true)}>
              Add voter
            </Button>
          </div>
        }
      />

      <div style={{ display: 'flex', gap: 10, flexWrap: 'wrap', marginBottom: 14 }}>
        <Select
          label="Panchayat"
          options={[{ value: '', label: 'All panchayats' }, ...panchayats.map((p) => ({ value: p.id, label: p.name }))]}
          value={panchayatId}
          onChange={(v) => {
            setPanchayatId(v);
            setBoothId('');
            setPage(0);
          }}
          style={{ marginBottom: 0, minWidth: 190 }}
        />
        <Select
          label="Booth"
          options={[{ value: '', label: 'All booths' }, ...boothChoices.map((b) => ({ value: b.id, label: b.name }))]}
          value={boothId}
          onChange={(v) => {
            setBoothId(v);
            setPage(0);
          }}
          style={{ marginBottom: 0, minWidth: 170 }}
        />
        <div style={{ flex: 1, minWidth: 220, alignSelf: 'flex-end' }}>
          <SearchBox value={search} onChange={setSearch} placeholder="Search by name or EPIC no." icon={Search} />
        </div>
      </div>

      <input
        ref={fileInput}
        type="file"
        accept=".csv,.xlsx,.xls"
        style={{ display: 'none' }}
        onChange={(e) => {
          const file = e.target.files?.[0];
          if (file) void handleFile(file);
        }}
      />

      <div
        onClick={() => fileInput.current?.click()}
        onDragOver={(e) => e.preventDefault()}
        onDrop={(e) => {
          e.preventDefault();
          const file = e.dataTransfer.files?.[0];
          if (file) void handleFile(file);
        }}
        style={{
          border: `1.5px dashed ${colors.line}`,
          borderRadius: 8,
          padding: '24px 16px',
          textAlign: 'center',
          background: colors.card,
          marginBottom: 16,
          cursor: 'pointer',
        }}
      >
        <UploadCloud size={22} color={colors.marigoldDeep} className={uploading ? 'kp-spin' : undefined} />
        <p style={{ fontFamily: fonts.sans, fontSize: 13, color: colors.ink, margin: '8px 0 4px' }}>
          {uploading ? 'Importing…' : 'Drop a .csv or .xlsx file, or browse'}
        </p>
        <p style={{ fontFamily: fonts.sans, fontSize: 11, color: colors.muted, margin: 0 }}>
          Columns: {TEMPLATE_HEADER.split(',').join(' · ')}
          {uploadTargetId ? '' : ' — pick a panchayat or booth first'}
        </p>
      </div>

      <p style={{ fontFamily: fonts.sans, fontSize: 12, color: colors.inkSoft, margin: '0 0 8px' }}>Recent uploads</p>
      {uploadRows.length === 0 ? (
        <EmptyState title="No uploads yet" hint="Imported files are listed here with their row counts." />
      ) : (
        <RowList>
          {uploadRows.map((upload, i) => (
            <Row key={upload.id} last={i === uploadRows.length - 1}>
              <div style={{ display: 'flex', alignItems: 'center', gap: 10, minWidth: 0 }}>
                <FileSpreadsheet size={16} color={colors.inkSoft} />
                <div>
                  <p style={{ fontFamily: fonts.mono, fontSize: 12, color: colors.ink, margin: 0 }}>{upload.fileName}</p>
                  <p style={{ fontFamily: fonts.sans, fontSize: 11, color: colors.muted, margin: '2px 0 0' }}>
                    {upload.unitName} · {upload.rowCount.toLocaleString('en-IN')} rows · {formatDate(upload.uploadedAt)}
                    {upload.message ? ` · ${upload.message}` : ''}
                  </p>
                </div>
              </div>
            </Row>
          ))}
        </RowList>
      )}

      <p style={{ fontFamily: fonts.sans, fontSize: 12, color: colors.inkSoft, margin: '20px 0 8px' }}>
        Voters {voters.data ? `· ${voters.data.totalElements.toLocaleString('en-IN')}` : ''}
      </p>

      {voters.error && <ErrorNote message={voters.error} />}

      {voters.loading && rows.length === 0 ? (
        <Spinner />
      ) : rows.length === 0 ? (
        <EmptyState title="No voters match this filter" hint="Upload a roll, or widen the search." />
      ) : (
        <Card padding={0}>
          <table style={{ width: '100%', borderCollapse: 'collapse', fontFamily: fonts.sans, fontSize: 13 }}>
            <thead>
              <tr style={{ background: colors.paper }}>
                {['EPIC no.', 'Name', 'Relation', 'Age', 'Gender', 'Booth', 'Sentiment', ''].map((h) => (
                  <th
                    key={h}
                    style={{
                      textAlign: 'left',
                      padding: '10px 14px',
                      fontSize: 11,
                      fontWeight: 500,
                      color: colors.inkSoft,
                      borderBottom: `1px solid ${colors.line}`,
                      whiteSpace: 'nowrap',
                    }}
                  >
                    {h}
                  </th>
                ))}
              </tr>
            </thead>
            <tbody>
              {rows.map((voter) => (
                <tr key={voter.id}>
                  <td style={cell}>
                    <span style={{ fontFamily: fonts.mono, fontSize: 12 }}>{voter.epicNo}</span>
                  </td>
                  <td style={cell}>{voter.name}</td>
                  <td style={{ ...cell, color: colors.inkSoft }}>{voter.relation}</td>
                  <td style={cell}>{voter.age}</td>
                  <td style={cell}>{voter.gender}</td>
                  <td style={{ ...cell, color: colors.inkSoft }}>{voter.boothName}</td>
                  <td style={cell}>
                    <SentimentPill value={voter.sentiment} />
                  </td>
                  <td style={{ ...cell, textAlign: 'right', whiteSpace: 'nowrap' }}>
                    <button onClick={() => setEditing(voter)} style={iconButton} aria-label={`Edit ${voter.name}`}>
                      <Edit3 size={14} color={colors.inkSoft} />
                    </button>
                    <button onClick={() => void removeVoter(voter)} style={iconButton} aria-label={`Delete ${voter.name}`}>
                      <Trash2 size={14} color={colors.negative} />
                    </button>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </Card>
      )}

      {voters.data && voters.data.totalPages > 1 && (
        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginTop: 12 }}>
          <Button tone="ghost" small disabled={page === 0} onClick={() => setPage((p) => Math.max(0, p - 1))}>
            Previous
          </Button>
          <span style={{ fontFamily: fonts.sans, fontSize: 12, color: colors.inkSoft }}>
            Page {page + 1} of {voters.data.totalPages}
          </span>
          <Button
            tone="ghost"
            small
            disabled={page + 1 >= voters.data.totalPages}
            onClick={() => setPage((p) => p + 1)}
          >
            Next
          </Button>
        </div>
      )}

      <VoterFormModal
        open={adding || editing !== null}
        title={editing ? 'Edit voter' : 'Add voter'}
        subtitle={
          editing
            ? `${editing.boothName} · changes apply to the live roll immediately`
            : boothId
              ? `${tree.find(boothId)?.path ?? ''} · added straight to the live roll`
              : 'Pick a booth in the filter above first'
        }
        voter={editing}
        submitLabel={editing ? 'Save changes' : 'Add voter'}
        busy={saving}
        onClose={() => {
          setEditing(null);
          setAdding(false);
        }}
        onSubmit={(values) => void saveVoter(values)}
      />

      <Modal
        open={confirmClear}
        title={`Delete every voter in ${boothName}?`}
        subtitle="This cannot be undone."
        onClose={() => {
          setConfirmClear(false);
          setTypedBoothName('');
        }}
      >
        <p style={{ fontFamily: fonts.sans, fontSize: 13, color: colors.inkSoft, margin: '0 0 12px' }}>
          {boothVoterCount === null
            ? `Every voter in ${boothName} will be deleted.`
            : `All ${boothVoterCount.toLocaleString('en-IN')} voters in ${boothName} will be deleted.`}{' '}
          <strong style={{ color: colors.negative }}>
            The sentiment your agents recorded against them goes too
          </strong>{' '}
          — it is stored per voter, so it cannot outlive them. Access requests and the upload history stay.
        </p>
        <Field
          label={`Type ${boothName} to confirm`}
          placeholder={boothName}
          value={typedBoothName}
          onChange={setTypedBoothName}
        />
        <div style={{ display: 'flex', gap: 8, marginTop: 4 }}>
          <Button
            tone="danger"
            icon={Trash2}
            loading={clearing}
            disabled={typedBoothName.trim() !== boothName}
            onClick={() => void clearBooth()}
          >
            Delete all
          </Button>
          <Button
            tone="ghost"
            onClick={() => {
              setConfirmClear(false);
              setTypedBoothName('');
            }}
          >
            Cancel
          </Button>
        </div>
      </Modal>
    </div>
  );
}

const cell = {
  padding: '10px 14px',
  borderBottom: `1px solid ${colors.line}`,
  color: colors.ink,
} as const;

const iconButton = {
  background: 'none',
  border: 'none',
  cursor: 'pointer',
  padding: 4,
} as const;
