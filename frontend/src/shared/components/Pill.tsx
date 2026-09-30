import type { ReactNode } from 'react';
import { colors, fonts } from '@/shared/theme';
import type { ConfidenceLevel, SentimentValue } from '@/shared/types';

const sentimentTone: Record<SentimentValue, { fg: string; bg: string; label: string }> = {
  POSITIVE: { fg: colors.positive, bg: colors.positiveWash, label: 'Positive' },
  NEUTRAL: { fg: colors.neutral, bg: colors.neutralWash, label: 'Neutral' },
  NEGATIVE: { fg: colors.negative, bg: colors.negativeWash, label: 'Negative' },
};

export function SentimentPill({ value }: { value: SentimentValue | null | undefined }) {
  if (!value) {
    return (
      <span
        style={{
          fontFamily: fonts.sans,
          fontSize: 11,
          color: colors.muted,
          border: `1px solid ${colors.line}`,
          borderRadius: 20,
          padding: '2px 8px',
          whiteSpace: 'nowrap',
        }}
      >
        Not recorded
      </span>
    );
  }
  const tone = sentimentTone[value];
  return (
    <span
      style={{
        fontFamily: fonts.sans,
        fontSize: 11,
        fontWeight: 500,
        borderRadius: 20,
        padding: '2px 8px',
        color: tone.fg,
        background: tone.bg,
        whiteSpace: 'nowrap',
      }}
    >
      {tone.label}
    </span>
  );
}

export function ConfidencePill({ value }: { value: ConfidenceLevel }) {
  const label = value.charAt(0) + value.slice(1).toLowerCase();
  return (
    <span
      style={{
        fontFamily: fonts.sans,
        fontSize: 11,
        borderRadius: 20,
        padding: '2px 8px',
        color: colors.marigoldDeep,
        background: colors.marigoldWash,
        whiteSpace: 'nowrap',
      }}
    >
      {label}
    </span>
  );
}

type StatusKind = 'PENDING' | 'APPROVED' | 'REJECTED' | 'EXPIRED' | 'REVOKED' | 'ACTIVE' | 'INACTIVE';

const statusTone: Record<StatusKind, { fg: string; bg: string }> = {
  PENDING: { fg: colors.marigoldDeep, bg: colors.marigoldWash },
  APPROVED: { fg: colors.positive, bg: colors.positiveWash },
  ACTIVE: { fg: colors.positive, bg: colors.positiveWash },
  REJECTED: { fg: colors.negative, bg: colors.negativeWash },
  REVOKED: { fg: colors.negative, bg: colors.negativeWash },
  INACTIVE: { fg: colors.muted, bg: '#EFEADF' },
  EXPIRED: { fg: colors.muted, bg: '#EFEADF' },
};

export function StatusPill({ status }: { status: string }) {
  const tone = statusTone[status as StatusKind] ?? statusTone.PENDING;
  const label = status.charAt(0) + status.slice(1).toLowerCase();
  return (
    <span
      style={{
        fontFamily: fonts.sans,
        fontSize: 11,
        fontWeight: 500,
        borderRadius: 20,
        padding: '2px 8px',
        color: tone.fg,
        background: tone.bg,
        whiteSpace: 'nowrap',
      }}
    >
      {label}
    </span>
  );
}

export function Tag({ children, color }: { children: ReactNode; color: string }) {
  return (
    <span
      style={{
        fontFamily: fonts.sans,
        fontSize: 11,
        fontWeight: 600,
        color,
        background: `${color}1A`,
        borderRadius: 20,
        padding: '2px 8px',
        whiteSpace: 'nowrap',
      }}
    >
      {children}
    </span>
  );
}
