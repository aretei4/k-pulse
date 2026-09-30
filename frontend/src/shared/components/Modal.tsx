import type { ReactNode } from 'react';
import { X } from 'lucide-react';
import { colors, fonts } from '@/shared/theme';
import { Heading } from './Heading';

export function Modal({
  open,
  title,
  subtitle,
  onClose,
  children,
  width = 460,
}: {
  open: boolean;
  title: string;
  subtitle?: string;
  onClose: () => void;
  children: ReactNode;
  width?: number;
}) {
  if (!open) return null;
  return (
    <div
      onClick={onClose}
      style={{
        position: 'fixed',
        inset: 0,
        background: 'rgba(21,42,56,0.34)',
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'center',
        padding: 20,
        zIndex: 150,
      }}
    >
      <div
        className="kp-fade"
        onClick={(e) => e.stopPropagation()}
        style={{
          background: colors.paper,
          borderRadius: 10,
          border: `1px solid ${colors.line}`,
          width: '100%',
          maxWidth: width,
          maxHeight: '86vh',
          overflowY: 'auto',
          padding: 20,
        }}
      >
        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start' }}>
          <div>
            <Heading size={18}>{title}</Heading>
            {subtitle && (
              <p style={{ fontFamily: fonts.sans, fontSize: 12, color: colors.inkSoft, margin: '0 0 14px' }}>{subtitle}</p>
            )}
          </div>
          <button onClick={onClose} style={{ background: 'none', border: 'none', cursor: 'pointer', color: colors.inkSoft }}>
            <X size={16} />
          </button>
        </div>
        {children}
      </div>
    </div>
  );
}
