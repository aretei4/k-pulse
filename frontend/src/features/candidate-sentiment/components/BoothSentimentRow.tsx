import { Link } from 'react-router-dom';
import { colors, fonts } from '@/shared/theme';
import type { BoothStatus, CandidateSentimentRow } from '@/shared/types';

const statusLabel: Record<BoothStatus, string> = {
  SAFE: 'Safe',
  WATCH: 'Watch',
  AT_RISK: 'At risk',
  NO_DATA: 'No data',
};

const statusColor: Record<BoothStatus, string> = {
  SAFE: colors.positive,
  WATCH: colors.marigoldDeep,
  AT_RISK: colors.negative,
  NO_DATA: colors.muted,
};

/**
 * One unit as a stacked bar of positive / neutral / negative, as a share of what
 * has been recorded there — not of the roll, which is why a booth with three
 * entries can still read 100%.
 */
export function BoothSentimentRow({
  row,
  onDrill,
}: {
  row: CandidateSentimentRow;
  onDrill?: (row: CandidateSentimentRow) => void;
}) {
  const { split } = row;
  const empty = split.total === 0;

  return (
    <div style={{ display: 'flex', alignItems: 'center', gap: 10, padding: '7px 0' }}>
      <button
        onClick={() => row.drillable && onDrill?.(row)}
        disabled={!row.drillable}
        title={row.drillable ? `See inside ${row.unitName}` : undefined}
        style={{
          width: 96,
          textAlign: 'left',
          background: 'none',
          border: 'none',
          padding: 0,
          cursor: row.drillable ? 'pointer' : 'default',
          fontFamily: fonts.sans,
          fontSize: 12,
          color: row.drillable ? colors.ink : colors.inkSoft,
          textDecoration: row.drillable ? 'underline' : 'none',
          flexShrink: 0,
        }}
      >
        {row.unitName}
      </button>

      <div style={{ flex: 1, display: 'flex', height: 22, borderRadius: 4, overflow: 'hidden', minWidth: 0 }}>
        {empty ? (
          <div
            style={{
              flex: 1,
              background: colors.paper,
              border: `1px dashed ${colors.line}`,
              fontFamily: fonts.sans,
              fontSize: 10,
              color: colors.muted,
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'center',
            }}
          >
            nothing recorded
          </div>
        ) : (
          [
            { value: split.positivePercent, color: colors.positive },
            { value: split.neutralPercent, color: colors.marigold },
            { value: split.negativePercent, color: colors.negative },
          ].map((part, i) =>
            part.value === 0 ? null : (
              <div
                key={i}
                style={{
                  width: `${part.value}%`,
                  background: part.color,
                  display: 'flex',
                  alignItems: 'center',
                  justifyContent: 'center',
                  fontFamily: fonts.mono,
                  fontSize: 10,
                  color: i === 1 ? colors.ink : '#FFFFFF',
                }}
              >
                {part.value >= 8 ? Math.round(part.value) : ''}
              </div>
            ),
          )
        )}
      </div>

      <span
        style={{
          width: 62,
          flexShrink: 0,
          textAlign: 'center',
          fontFamily: fonts.sans,
          fontSize: 10,
          padding: '2px 0',
          borderRadius: 4,
          color: statusColor[row.status],
          border: `1px solid ${statusColor[row.status]}`,
        }}
      >
        {statusLabel[row.status]}
      </span>

      {/* FR-A12 lives behind this link; until interventions ship it opens the
          unit's recorded houses, which is the evidence an admin would act on. */}
      <Link
        to={`/admin/pre-election/unit/${row.unitId}`}
        style={{
          width: 72,
          flexShrink: 0,
          fontFamily: fonts.sans,
          fontSize: 11,
          color: colors.inkSoft,
          textAlign: 'right',
        }}
      >
        Log action
      </Link>
    </div>
  );
}
