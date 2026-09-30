import { FlaskConical, X } from 'lucide-react';
import { DEMO_IS_RUNTIME, exitDemo } from '@/lib/demoMode';
import { colors, fonts } from '@/shared/theme';

/**
 * Standing marker that the data on screen is fabricated. Only shown when demo
 * mode was switched on at runtime in a production build — during local
 * development mocks are the norm and a permanent badge would just be noise.
 *
 * Floats above the agent layout's bottom navigation rather than displacing
 * layout, so demo screenshots match what the real app looks like.
 */
export function DemoBanner() {
  if (!DEMO_IS_RUNTIME) return null;

  return (
    <div
      role="status"
      style={{
        position: 'fixed',
        left: '50%',
        transform: 'translateX(-50%)',
        bottom: 'calc(72px + env(safe-area-inset-bottom, 0px))',
        zIndex: 900,
        display: 'flex',
        alignItems: 'center',
        gap: 8,
        padding: '7px 10px 7px 12px',
        borderRadius: 20,
        background: colors.ink,
        color: '#FFF',
        boxShadow: '0 2px 10px rgba(21,42,56,0.28)',
        fontFamily: fonts.sans,
        fontSize: 12,
        maxWidth: 'calc(100vw - 32px)',
      }}
    >
      <FlaskConical size={13} style={{ flexShrink: 0, color: colors.marigold }} />
      <span>
        <strong style={{ fontWeight: 600 }}>Demo data</strong> — sample records, not a live system
      </span>
      <button
        type="button"
        onClick={exitDemo}
        aria-label="Exit demo mode"
        title="Exit demo mode"
        style={{
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'center',
          width: 20,
          height: 20,
          flexShrink: 0,
          borderRadius: 20,
          border: 'none',
          cursor: 'pointer',
          background: 'rgba(255,255,255,0.16)',
          color: '#FFF',
        }}
      >
        <X size={12} />
      </button>
    </div>
  );
}
