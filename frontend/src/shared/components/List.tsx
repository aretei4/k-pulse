import type { ReactNode } from 'react';
import { colors } from '@/shared/theme';

/** A bordered card list — the rows pattern used across admin tables. */
export function RowList({ children }: { children: ReactNode }) {
  return <div style={{ border: `1px solid ${colors.line}`, borderRadius: 8, overflow: 'hidden' }}>{children}</div>;
}

export function Row({ children, last }: { children: ReactNode; last?: boolean }) {
  return (
    <div
      style={{
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'space-between',
        gap: 12,
        padding: '12px 16px',
        borderBottom: last ? 'none' : `1px solid ${colors.line}`,
        background: colors.card,
      }}
    >
      {children}
    </div>
  );
}
