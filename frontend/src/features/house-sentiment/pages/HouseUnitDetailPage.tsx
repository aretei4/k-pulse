import { useMemo, useState } from 'react';
import { useNavigate, useParams, useSearchParams } from 'react-router-dom';
import { ChevronLeft, Download, Search } from 'lucide-react';
import {
  Button,
  Card,
  ConfidencePill,
  EmptyState,
  ErrorNote,
  SearchBox,
  Spinner,
  StatCard,
  useToast,
} from '@/shared/components';
import { PageHeader } from '@/shared/layouts/PageHeader';
import { useAsync } from '@/shared/hooks/useAsync';
import { useUnitTree } from '@/shared/hooks/useUnitTree';
import { downloadBlob } from '@/lib/apiClient';
import { colors, fonts } from '@/shared/theme';
import { formatDate, percent } from '@/shared/utils/format';
import { houseSentimentApi } from '../services/houseSentimentApi';

const numericCell = {
  padding: '10px 14px',
  textAlign: 'right',
  borderBottom: `1px solid ${colors.line}`,
  fontFamily: fonts.mono,
  fontSize: 12,
} as const;

const textCell = {
  padding: '10px 14px',
  borderBottom: `1px solid ${colors.line}`,
  fontFamily: fonts.sans,
  fontSize: 13,
} as const;

/**
 * Every house recorded inside one unit — the drill-down behind a row of the
 * pre-election report. The filters that produced that row are carried in the
 * query string, so the figures here add up to the row the admin clicked.
 */
export function HouseUnitDetailPage() {
  const navigate = useNavigate();
  const { notify } = useToast();
  const { unitId = '' } = useParams();
  const [params] = useSearchParams();
  const tree = useUnitTree();
  const [search, setSearch] = useState('');
  const [exporting, setExporting] = useState<'pdf' | 'excel' | null>(null);

  const filter = useMemo(
    () => ({
      candidateId: params.get('candidateId') ?? undefined,
      confidence: (params.get('confidence') ?? undefined) as never,
      from: params.get('from') ?? undefined,
      to: params.get('to') ?? undefined,
    }),
    [params],
  );

  const houses = useAsync(
    () => (unitId ? houseSentimentApi.adminHouses(unitId, filter) : Promise.resolve(null)),
    [unitId, filter],
  );

  const unit = tree.find(unitId);

  /** The file holds what the filters select, not what the search box narrows to. */
  async function exportHouses(format: 'pdf' | 'excel') {
    setExporting(format);
    try {
      const blob = await houseSentimentApi.adminHousesExport(unitId, filter, format);
      const slug = (unit?.name ?? 'unit').toLowerCase().replace(/[^a-z0-9]+/g, '-').replace(/(^-|-$)/g, '');
      const stamp = new Date().toISOString().slice(0, 10);
      downloadBlob(blob, `k-pulse-houses-${slug || 'unit'}-${stamp}.${format === 'pdf' ? 'pdf' : 'xlsx'}`);
      notify(`${format === 'pdf' ? 'PDF' : 'Excel'} downloaded`);
    } catch (err) {
      notify(err instanceof Error ? err.message : 'Export failed', 'error');
    } finally {
      setExporting(null);
    }
  }

  const rows = houses.data ?? [];
  const term = search.trim().toLowerCase();
  const visible = term
    ? rows.filter(
        (r) => r.houseNo.toLowerCase().includes(term) || (r.houseName ?? '').toLowerCase().includes(term),
      )
    : rows;

  const totals = visible.reduce(
    (acc, r) => ({
      people: acc.people + r.headcount,
      residents: acc.residents + r.residentialCount,
      positive: acc.positive + r.positiveCount,
    }),
    { people: 0, residents: 0, positive: 0 },
  );

  return (
    <div>
      <button
        onClick={() => navigate(-1)}
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
        <ChevronLeft size={14} /> Back to the report
      </button>

      <PageHeader
        title={unit ? unit.name : 'Houses'}
        subtitle={
          unit?.path
            ? `${unit.path} · every house recorded here, under the report's filters`
            : "Every house recorded here, under the report's filters"
        }
      />

      {houses.error && <ErrorNote message={houses.error} />}

      {houses.loading && rows.length === 0 ? (
        <Spinner />
      ) : rows.length === 0 ? (
        <EmptyState
          title="No houses recorded in this unit"
          hint="Agents record houses from the Pre-election sentiment screen in the field app."
        />
      ) : (
        <>
          <div style={{ display: 'flex', gap: 12, flexWrap: 'wrap', marginBottom: 14 }}>
            <StatCard label="Houses" value={visible.length} />
            <StatCard label="People" value={totals.people} />
            <StatCard
              label="Residents"
              value={totals.residents}
              hint={`${percent(totals.residents, totals.people)} of people counted`}
            />
            <StatCard
              label="Positive"
              value={percent(totals.positive, totals.residents)}
              hint={`${totals.positive.toLocaleString('en-IN')} of ${totals.residents.toLocaleString('en-IN')} residents`}
            />
          </div>

          <div style={{ display: 'flex', gap: 8, marginBottom: 14, flexWrap: 'wrap', alignItems: 'center' }}>
            <div style={{ flex: '1 1 260px', maxWidth: 320 }}>
              <SearchBox value={search} onChange={setSearch} placeholder="Search house no. or name" icon={Search} />
            </div>
            <Button
              icon={Download}
              tone="accent"
              small
              loading={exporting === 'pdf'}
              onClick={() => void exportHouses('pdf')}
            >
              Download PDF
            </Button>
            <Button
              icon={Download}
              tone="ghost"
              small
              loading={exporting === 'excel'}
              onClick={() => void exportHouses('excel')}
            >
              Download Excel
            </Button>
          </div>

          {visible.length === 0 ? (
            <EmptyState title="No house matches" hint="Clear the search or try another spelling." />
          ) : (
            <Card padding={0}>
              <div style={{ overflowX: 'auto' }}>
                <table style={{ width: '100%', borderCollapse: 'collapse' }}>
                  <thead>
                    <tr style={{ background: colors.paper }}>
                      {[
                        'House no.',
                        'House name',
                        'Booth',
                        'Ward',
                        'People',
                        'Resid.',
                        'Positive',
                        'Neutral',
                        'Negative',
                        'Confidence',
                        'Recorded by',
                        'Updated',
                      ].map((h, i) => (
                        <th
                          key={h}
                          style={{
                            textAlign: i >= 3 && i <= 8 ? 'right' : 'left',
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
                    {visible.map((house) => (
                      <tr key={house.id}>
                        <td style={{ ...textCell, fontFamily: fonts.mono, fontSize: 12 }}>{house.houseNo}</td>
                        <td style={textCell}>{house.houseName}</td>
                        <td style={{ ...textCell, color: colors.inkSoft, fontSize: 12 }}>{house.boothName}</td>
                        <td style={numericCell}>{house.wardNo ?? '—'}</td>
                        <td style={numericCell}>{house.headcount}</td>
                        <td style={numericCell}>{house.residentialCount}</td>
                        <td style={{ ...numericCell, color: colors.positive }}>{house.positiveCount}</td>
                        <td style={{ ...numericCell, color: colors.marigoldDeep }}>{house.neutralCount}</td>
                        <td style={{ ...numericCell, color: colors.negative }}>{house.negativeCount}</td>
                        <td style={textCell}>
                          <ConfidencePill value={house.confidence} />
                        </td>
                        <td style={{ ...textCell, color: colors.inkSoft, fontSize: 12 }}>{house.recordedByName}</td>
                        <td style={{ ...textCell, color: colors.muted, fontSize: 12, whiteSpace: 'nowrap' }}>
                          {formatDate(house.updatedAt)}
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            </Card>
          )}

          <p style={{ fontFamily: fonts.sans, fontSize: 11, color: colors.muted, margin: '10px 0 20px' }}>
            Households, not named voters — no individual name, age, gender or EPIC no. is held for anyone in these
            houses. Sentiment covers residents only.
          </p>
        </>
      )}
    </div>
  );
}
