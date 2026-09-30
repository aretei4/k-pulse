import type { ReactNode } from 'react';
import { Loader2 } from 'lucide-react';
import { colors, fonts } from '@/shared/theme';

export function Spinner({ label = 'Loading…' }: { label?: string }) {
  return (
    <div
      style={{
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'center',
        gap: 8,
        padding: 32,
        fontFamily: fonts.sans,
        fontSize: 13,
        color: colors.inkSoft,
      }}
    >
      <Loader2 size={16} className="kp-spin" />
      {label}
    </div>
  );
}

export function EmptyState({ title, hint, action }: { title: string; hint?: string; action?: ReactNode }) {
  return (
    <div
      style={{
        border: `1px dashed ${colors.line}`,
        borderRadius: 8,
        padding: '28px 16px',
        textAlign: 'center',
        background: colors.card,
      }}
    >
      <p style={{ fontFamily: fonts.sans, fontSize: 13, color: colors.ink, margin: 0 }}>{title}</p>
      {hint && <p style={{ fontFamily: fonts.sans, fontSize: 11, color: colors.muted, margin: '4px 0 0' }}>{hint}</p>}
      {action && <div style={{ marginTop: 12 }}>{action}</div>}
    </div>
  );
}

export function ErrorNote({ message }: { message: string }) {
  return (
    <div
      style={{
        border: `1px solid ${colors.negative}`,
        background: colors.negativeWash,
        borderRadius: 6,
        padding: '8px 12px',
        marginBottom: 12,
        fontFamily: fonts.sans,
        fontSize: 12,
        color: colors.negative,
      }}
    >
      {message}
    </div>
  );
}

export function InfoNote({ children }: { children: ReactNode }) {
  return (
    <div
      style={{
        border: `1px dashed ${colors.line}`,
        borderRadius: 8,
        padding: 10,
        background: colors.card,
        fontFamily: fonts.sans,
        fontSize: 11,
        color: colors.inkSoft,
      }}
    >
      {children}
    </div>
  );
}
