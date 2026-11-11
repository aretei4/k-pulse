import { useRef, useState } from 'react';
import { CheckCircle2, Upload } from 'lucide-react';
import { Button, ErrorNote, InfoNote } from '@/shared/components';
import { colors, fonts } from '@/shared/theme';
import { locationsApi, type LocationImportResult, type MasterSheet } from '../services/locationsApi';

const SHEETS: Array<{ id: MasterSheet; title: string; columns: string; note?: string }> = [
  {
    id: 'hierarchy',
    title: '1. District, block and gram panchayat',
    columns: 'district_name, block_name, gp_name and the LGD code of each',
  },
  {
    id: 'booths',
    title: '2. Booths',
    columns: 'part number, part name, block, gram panchayat and the polling station',
    note: 'A booth is named for its part number and its polling station — columns A and E of the state sheet — since the station is the building an agent is sent to. The part name only stands in where a row leaves the station blank.',
  },
];

function Counts({ result }: { result: LocationImportResult }) {
  return (
    <div style={{ fontFamily: fonts.sans, fontSize: 12, color: colors.ink, marginTop: 8 }}>
      <div>
        {result.rowsRead} row(s) read · <strong>{result.created}</strong> to add ·{' '}
        <strong>{result.updated}</strong> to update
        {result.rowsSkipped > 0 && <> · {result.rowsSkipped} skipped</>}
      </div>
      <div style={{ color: colors.muted, marginTop: 2 }}>
        {result.levels.map((l) => `${l.level.toLowerCase()} +${l.created}/~${l.updated}`).join(' · ')}
      </div>
      {result.notInFile > 0 && (
        <div style={{ color: colors.muted, marginTop: 2 }}>
          {result.notInFile} unit(s) already held are not in this file, and are left alone.
        </div>
      )}
    </div>
  );
}

/**
 * Uploading the location master (super admin only).
 *
 * <p>Every upload is previewed before it can be applied. This is the table the
 * voter roll, access grants and all recorded sentiment hang off, so an admin
 * should see what a file is about to do to it before saying yes.
 */
export function MasterUploadPanel({ onImported }: { onImported: () => void }) {
  const [sheet, setSheet] = useState<MasterSheet>('hierarchy');
  const [file, setFile] = useState<File | null>(null);
  const [preview, setPreview] = useState<LocationImportResult | null>(null);
  const [applied, setApplied] = useState<LocationImportResult | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);
  const picker = useRef<HTMLInputElement>(null);

  function reset() {
    setFile(null);
    setPreview(null);
    setApplied(null);
    setError(null);
    if (picker.current) picker.current.value = '';
  }

  async function run(asPreview: boolean) {
    if (!file) return setError('Choose a .csv or .xlsx file first');
    setBusy(true);
    setError(null);
    try {
      const result = await locationsApi.importMaster(sheet, file, asPreview);
      if (asPreview) {
        setPreview(result);
      } else {
        setApplied(result);
        setPreview(null);
        onImported();
      }
    } catch (err) {
      setError(err instanceof Error ? err.message : 'That file could not be read');
    } finally {
      setBusy(false);
    }
    return undefined;
  }

  const active = SHEETS.find((s) => s.id === sheet)!;

  return (
    <div
      style={{
        border: `1px solid ${colors.line}`,
        borderRadius: 10,
        padding: 16,
        marginBottom: 20,
        background: colors.paper,
      }}
    >
      <div style={{ fontFamily: fonts.serif, fontSize: 15, color: colors.ink, marginBottom: 2 }}>
        Upload the location master
      </div>
      <div style={{ fontFamily: fonts.sans, fontSize: 12, color: colors.inkSoft, marginBottom: 12 }}>
        Two sheets, in this order. The booth sheet names no district, so its gram panchayats must already be
        here before it will load.
      </div>

      <div style={{ display: 'flex', gap: 6, marginBottom: 12 }}>
        {SHEETS.map((s) => {
          const on = s.id === sheet;
          return (
            <button
              key={s.id}
              type="button"
              onClick={() => {
                setSheet(s.id);
                reset();
              }}
              aria-pressed={on}
              style={{
                flex: 1,
                padding: '9px 10px',
                cursor: 'pointer',
                fontFamily: fonts.sans,
                fontSize: 12,
                textAlign: 'left',
                borderRadius: 7,
                border: `1px solid ${on ? colors.marigoldDeep : colors.line}`,
                background: on ? colors.marigoldDeep : 'transparent',
                color: on ? colors.paper : colors.inkSoft,
              }}
            >
              {s.title}
            </button>
          );
        })}
      </div>

      <div style={{ fontFamily: fonts.sans, fontSize: 11, color: colors.muted, marginBottom: 8 }}>
        Needs: {active.columns}. Column order does not matter.
        {active.note && <div style={{ marginTop: 4 }}>{active.note}</div>}
      </div>

      {error && <ErrorNote message={error} />}

      <input
        ref={picker}
        type="file"
        accept=".csv,.xlsx,.xls"
        onChange={(e) => {
          setFile(e.target.files?.[0] ?? null);
          setPreview(null);
          setApplied(null);
          setError(null);
        }}
        style={{ fontFamily: fonts.sans, fontSize: 12, color: colors.ink, marginBottom: 10, display: 'block' }}
      />

      {preview && (
        <div style={{ marginBottom: 10 }}>
          <InfoNote>
            Nothing has been changed yet.
            <Counts result={preview} />
          </InfoNote>
        </div>
      )}

      {applied && (
        <div style={{ marginBottom: 10 }}>
          <InfoNote>
            <span style={{ display: 'inline-flex', alignItems: 'center', gap: 5 }}>
              <CheckCircle2 size={13} /> Master updated.
            </span>
            <Counts result={applied} />
          </InfoNote>
        </div>
      )}

      {(preview ?? applied) && (preview ?? applied)!.problems.length > 0 && (
        <div
          style={{
            maxHeight: 150,
            overflowY: 'auto',
            border: `1px solid ${colors.line}`,
            borderRadius: 7,
            padding: 8,
            marginBottom: 10,
          }}
        >
          {(preview ?? applied)!.problems.map((p) => (
            <div key={p} style={{ fontFamily: fonts.sans, fontSize: 11, color: colors.marigoldDeep }}>
              {p}
            </div>
          ))}
        </div>
      )}

      <div style={{ display: 'flex', gap: 8 }}>
        <Button icon={Upload} small disabled={!file} loading={busy && !preview} onClick={() => void run(true)}>
          Check the file
        </Button>
        {/* Applying is only offered once a preview has shown what it will do. */}
        <Button
          tone="accent"
          small
          disabled={!preview || preview.created + preview.updated === 0}
          loading={busy && Boolean(preview)}
          onClick={() => void run(false)}
        >
          Apply {preview ? `${preview.created + preview.updated} change(s)` : 'changes'}
        </Button>
      </div>
    </div>
  );
}
