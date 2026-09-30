import type { LucideIcon } from 'lucide-react';
import { colors, fonts } from '@/shared/theme';

export interface Segment<T extends string> {
  value: T;
  label: string;
  icon?: LucideIcon;
  activeColor?: string;
  activeWash?: string;
}

export function SegmentedControl<T extends string>({
  segments,
  value,
  onChange,
  stacked,
  pill,
}: {
  segments: Segment<T>[];
  value: T | null;
  onChange: (value: T) => void;
  stacked?: boolean;
  pill?: boolean;
}) {
  return (
    <div style={{ display: 'flex', gap: 8 }}>
      {segments.map((seg) => {
        const active = value === seg.value;
        const activeColor = seg.activeColor ?? colors.marigoldDeep;
        const activeWash = seg.activeWash ?? colors.marigoldWash;
        const Icon = seg.icon;
        return (
          <button
            key={seg.value}
            type="button"
            onClick={() => onChange(seg.value)}
            style={{
              flex: 1,
              textAlign: 'center',
              padding: stacked ? '10px 0' : '8px 0',
              borderRadius: pill ? 20 : 6,
              border: active ? `1.5px solid ${activeColor}` : `1px solid ${colors.line}`,
              background: active ? activeWash : 'transparent',
              color: active ? activeColor : colors.inkSoft,
              cursor: 'pointer',
              fontFamily: fonts.sans,
              fontSize: 12,
              fontWeight: 500,
            }}
          >
            {Icon && <Icon size={16} style={{ marginBottom: 2 }} />}
            <p style={{ margin: 0 }}>{seg.label}</p>
          </button>
        );
      })}
    </div>
  );
}
