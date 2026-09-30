import type { CSSProperties, ReactNode } from 'react';
import type { LucideIcon } from 'lucide-react';
import { colors, fonts } from '@/shared/theme';

type Tone = 'primary' | 'accent' | 'ghost' | 'danger';

export interface ButtonProps {
  children?: ReactNode;
  tone?: Tone;
  icon?: LucideIcon;
  small?: boolean;
  type?: 'button' | 'submit';
  disabled?: boolean;
  loading?: boolean;
  full?: boolean;
  style?: CSSProperties;
  onClick?: () => void;
}

const background: Record<Tone, string> = {
  primary: colors.ink,
  accent: colors.marigold,
  ghost: 'transparent',
  danger: colors.negative,
};

export function Button({
  children,
  tone = 'primary',
  icon: Icon,
  small,
  type = 'button',
  disabled,
  loading,
  full,
  style,
  onClick,
}: ButtonProps) {
  const isGhost = tone === 'ghost';
  const inactive = disabled || loading;
  return (
    <button
      type={type}
      onClick={onClick}
      disabled={inactive}
      style={{
        display: full ? 'flex' : 'inline-flex',
        width: full ? '100%' : undefined,
        justifyContent: 'center',
        alignItems: 'center',
        gap: 6,
        fontFamily: fonts.sans,
        fontWeight: 500,
        fontSize: small ? 12 : 13,
        padding: small ? '6px 10px' : '9px 16px',
        borderRadius: 6,
        background: background[tone],
        color: isGhost ? colors.ink : '#FFF',
        border: isGhost ? `1px solid ${colors.line}` : 'none',
        cursor: inactive ? 'not-allowed' : 'pointer',
        opacity: inactive ? 0.55 : 1,
        transition: 'opacity 120ms ease',
        ...style,
      }}
    >
      {Icon && <Icon size={small ? 13 : 15} className={loading ? 'kp-spin' : undefined} />}
      {children}
    </button>
  );
}
