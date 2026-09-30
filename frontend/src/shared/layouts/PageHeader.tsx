import type { ReactNode } from 'react';
import { Heading, Sub } from '@/shared/components';

export function PageHeader({
  title,
  subtitle,
  action,
  size = 20,
}: {
  title: string;
  subtitle?: string;
  action?: ReactNode;
  size?: number;
}) {
  return (
    <div
      style={{
        display: 'flex',
        justifyContent: 'space-between',
        alignItems: 'flex-start',
        gap: 16,
        flexWrap: 'wrap',
      }}
    >
      <div>
        <Heading size={size}>{title}</Heading>
        {subtitle && <Sub>{subtitle}</Sub>}
      </div>
      {action}
    </div>
  );
}
