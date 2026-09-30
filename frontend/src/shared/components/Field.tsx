import type { CSSProperties } from 'react';
import type { LucideIcon } from 'lucide-react';
import { colors, fonts } from '@/shared/theme';

const controlStyle: CSSProperties = {
  width: '100%',
  fontFamily: fonts.sans,
  fontSize: 14,
  padding: '9px 12px',
  borderRadius: 6,
  border: `1px solid ${colors.line}`,
  background: colors.paper,
  color: colors.ink,
  outline: 'none',
};

export interface FieldProps {
  label?: string;
  placeholder?: string;
  type?: string;
  value: string;
  onChange: (value: string) => void;
  error?: string;
  hint?: string;
  disabled?: boolean;
  maxLength?: number;
  style?: CSSProperties;
}

export function Field({
  label,
  placeholder,
  type = 'text',
  value,
  onChange,
  error,
  hint,
  disabled,
  maxLength,
  style,
}: FieldProps) {
  return (
    <div style={{ marginBottom: 12, ...style }}>
      {label && (
        <label style={{ display: 'block', fontFamily: fonts.sans, fontSize: 12, color: colors.inkSoft, marginBottom: 4 }}>
          {label}
        </label>
      )}
      <input
        type={type}
        placeholder={placeholder}
        value={value}
        disabled={disabled}
        maxLength={maxLength}
        onChange={(e) => onChange(e.target.value)}
        style={{ ...controlStyle, borderColor: error ? colors.negative : colors.line, opacity: disabled ? 0.6 : 1 }}
      />
      {error ? (
        <p style={{ fontFamily: fonts.sans, fontSize: 11, color: colors.negative, margin: '4px 0 0' }}>{error}</p>
      ) : hint ? (
        <p style={{ fontFamily: fonts.sans, fontSize: 11, color: colors.muted, margin: '4px 0 0' }}>{hint}</p>
      ) : null}
    </div>
  );
}

export interface SelectOption {
  value: string;
  label: string;
}

export interface SelectProps {
  label?: string;
  options: SelectOption[];
  value: string;
  onChange: (value: string) => void;
  disabled?: boolean;
  placeholder?: string;
  style?: CSSProperties;
}

export function Select({ label, options, value, onChange, disabled, placeholder, style }: SelectProps) {
  return (
    <div style={{ marginBottom: 12, ...style }}>
      {label && (
        <label style={{ display: 'block', fontFamily: fonts.sans, fontSize: 12, color: colors.inkSoft, marginBottom: 4 }}>
          {label}
        </label>
      )}
      <select
        value={value}
        disabled={disabled}
        onChange={(e) => onChange(e.target.value)}
        style={{ ...controlStyle, opacity: disabled ? 0.6 : 1 }}
      >
        {placeholder && <option value="">{placeholder}</option>}
        {options.map((o) => (
          <option key={o.value} value={o.value}>
            {o.label}
          </option>
        ))}
      </select>
    </div>
  );
}

export function SearchBox({
  value,
  onChange,
  placeholder,
  icon: Icon,
}: {
  value: string;
  onChange: (v: string) => void;
  placeholder: string;
  icon?: LucideIcon;
}) {
  return (
    <div
      style={{
        display: 'flex',
        alignItems: 'center',
        gap: 6,
        border: `1px solid ${colors.line}`,
        borderRadius: 6,
        padding: '7px 10px',
        background: colors.card,
      }}
    >
      {Icon && <Icon size={13} color={colors.muted} />}
      <input
        value={value}
        onChange={(e) => onChange(e.target.value)}
        placeholder={placeholder}
        style={{
          border: 'none',
          outline: 'none',
          background: 'transparent',
          fontFamily: fonts.sans,
          fontSize: 12,
          color: colors.ink,
          flex: 1,
        }}
      />
    </div>
  );
}
