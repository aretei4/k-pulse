import type { CSSProperties, ReactNode } from 'react';
import { colors, fonts } from '@/shared/theme';

export function Card({ children, style, padding = 16 }: { children: ReactNode; style?: CSSProperties; padding?: number }) {
  return (
    <div
      style={{
        background: colors.card,
        border: `1px solid ${colors.line}`,
        borderRadius: 8,
        padding,
        ...style,
      }}
    >
      {children}
    </div>
  );
}

export function StatCard({ label, value, hint }: { label: string; value: string | number; hint?: string }) {
  return (
    <Card style={{ flex: 1, minWidth: 150 }} padding={0}>
      <div style={{ padding: '12px 16px' }}>
        <p style={{ fontFamily: fonts.sans, fontSize: 11, color: colors.inkSoft, margin: '0 0 4px' }}>{label}</p>
        <p style={{ fontFamily: fonts.mono, fontSize: 22, fontWeight: 500, color: colors.ink, margin: 0 }}>
          {typeof value === 'number' ? value.toLocaleString('en-IN') : value}
        </p>
        {hint && <p style={{ fontFamily: fonts.sans, fontSize: 11, color: colors.muted, margin: '4px 0 0' }}>{hint}</p>}
      </div>
    </Card>
  );
}

export function ChartCard({ title, children, style }: { title: string; children: ReactNode; style?: CSSProperties }) {
  return (
    <Card style={style}>
      <p style={{ fontFamily: fonts.sans, fontSize: 12, color: colors.inkSoft, margin: '0 0 8px' }}>{title}</p>
      {children}
    </Card>
  );
}

export function Legend({ items }: { items: { name: string; color: string }[] }) {
  return (
    <div style={{ display: 'flex', gap: 10, justifyContent: 'center', marginTop: 6, flexWrap: 'wrap' }}>
      {items.map((it) => (
        <span
          key={it.name}
          style={{ fontFamily: fonts.sans, fontSize: 11, color: colors.inkSoft, display: 'flex', alignItems: 'center', gap: 4 }}
        >
          <span style={{ width: 8, height: 8, borderRadius: 8, background: it.color, display: 'inline-block' }} />
          {it.name}
        </span>
      ))}
    </div>
  );
}
