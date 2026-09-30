import type { ReactNode } from 'react';
import { colors, fonts } from '@/shared/theme';

export function Heading({ children, size = 20 }: { children: ReactNode; size?: number }) {
  return (
    <h2 style={{ fontFamily: fonts.serif, fontWeight: 600, fontSize: size, color: colors.ink, margin: '0 0 4px' }}>
      {children}
    </h2>
  );
}

export function Sub({ children }: { children: ReactNode }) {
  return <p style={{ fontFamily: fonts.sans, fontSize: 13, color: colors.inkSoft, margin: '0 0 16px' }}>{children}</p>;
}

export function Eyebrow({ children }: { children: ReactNode }) {
  return (
    <p style={{ fontFamily: fonts.mono, fontSize: 11, letterSpacing: 1, color: colors.marigoldDeep, margin: '0 0 4px' }}>
      {children}
    </p>
  );
}
