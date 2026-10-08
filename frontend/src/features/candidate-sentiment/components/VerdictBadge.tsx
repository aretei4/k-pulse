import { ThumbsDown, ThumbsUp } from 'lucide-react';
import { colors, fonts } from '@/shared/theme';
import type { SentimentSplit } from '@/shared/types';

/**
 * The headline call: 👍 when positive share leads negative, 👎 otherwise.
 *
 * "No data yet" is its own state on purpose — a unit nobody has canvassed must
 * not read as a candidate doing badly there.
 */
export function VerdictBadge({ totals }: { totals: SentimentSplit }) {
  if (totals.verdict === 'NO_DATA') {
    return (
      <div
        style={{
          display: 'flex',
          alignItems: 'center',
          gap: 8,
          padding: '10px 16px',
          borderRadius: 999,
          border: `1px solid ${colors.line}`,
          background: colors.card,
        }}
      >
        <span style={{ fontFamily: fonts.sans, fontSize: 13, fontWeight: 600, color: colors.inkSoft }}>
          No data yet
        </span>
      </div>
    );
  }

  const positive = totals.verdict === 'POSITIVE';
  const Icon = positive ? ThumbsUp : ThumbsDown;
  const lead = `${totals.netLead > 0 ? '+' : ''}${totals.netLead.toFixed(1)} net lead`;

  return (
    <div
      style={{
        display: 'flex',
        alignItems: 'center',
        gap: 10,
        padding: '10px 18px',
        borderRadius: 999,
        background: positive ? colors.positive : colors.negative,
        color: '#FFFFFF',
      }}
    >
      <Icon size={18} />
      <div>
        <p style={{ fontFamily: fonts.sans, fontSize: 14, fontWeight: 700, margin: 0, letterSpacing: 0.4 }}>
          {positive ? 'POSITIVE' : 'NEGATIVE'}
        </p>
        <p style={{ fontFamily: fonts.mono, fontSize: 11, margin: '1px 0 0', opacity: 0.9 }}>{lead}</p>
      </div>
    </div>
  );
}
