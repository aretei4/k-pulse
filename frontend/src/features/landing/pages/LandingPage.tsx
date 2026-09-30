import { Link } from 'react-router-dom';
import { Monitor, Smartphone } from 'lucide-react';
import { colors, fonts } from '@/shared/theme';
import { DEMO_MODE } from '@/lib/demoMode';

const cardStyle = {
  display: 'flex',
  alignItems: 'flex-start',
  gap: 12,
  background: colors.card,
  border: `1px solid ${colors.line}`,
  borderRadius: 10,
  padding: 18,
  textDecoration: 'none',
  flex: 1,
  minWidth: 240,
} as const;

/**
 * "/" — the unauthenticated splash. Browsers land here and pick a side; the
 * mobile shell skips it and opens /agent directly.
 */
export function LandingPage() {
  return (
    <div
      style={{
        minHeight: '100dvh',
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'center',
        padding: 24,
      }}
    >
      <div style={{ width: '100%', maxWidth: 640 }}>
        <p style={{ fontFamily: fonts.mono, fontSize: 11, letterSpacing: 1, color: colors.marigoldDeep, margin: '0 0 6px' }}>
          K-PULSE
        </p>
        <h1 style={{ fontFamily: fonts.serif, fontWeight: 700, fontSize: 30, color: colors.ink, margin: '0 0 6px' }}>
          Voter sentiment, booth by booth
        </h1>
        <p style={{ fontFamily: fonts.sans, fontSize: 14, color: colors.inkSoft, margin: '0 0 24px', maxWidth: 460 }}>
          Field agents record sentiment per voter against a candidate. Admins approve access, curate the roll, and read
          the rollup from booth to district.
        </p>

        <div style={{ display: 'flex', gap: 14, flexWrap: 'wrap' }}>
          <Link to="/admin" style={cardStyle}>
            <Monitor size={20} color={colors.marigoldDeep} />
            <div>
              <p style={{ fontFamily: fonts.sans, fontSize: 14, fontWeight: 600, color: colors.ink, margin: '0 0 4px' }}>
                Admin
              </p>
              <p style={{ fontFamily: fonts.sans, fontSize: 12, color: colors.inkSoft, margin: 0 }}>
                Dashboard, access requests, voter lists, reports.
              </p>
            </div>
          </Link>

          <Link to="/agent" style={cardStyle}>
            <Smartphone size={20} color={colors.marigoldDeep} />
            <div>
              <p style={{ fontFamily: fonts.sans, fontSize: 14, fontWeight: 600, color: colors.ink, margin: '0 0 4px' }}>
                Field agent
              </p>
              <p style={{ fontFamily: fonts.sans, fontSize: 12, color: colors.inkSoft, margin: 0 }}>
                Request access, work your voter list, record sentiment.
              </p>
            </div>
          </Link>
        </div>

        {!DEMO_MODE && (
          <p style={{ fontFamily: fonts.sans, fontSize: 12, color: colors.inkSoft, margin: '20px 0 0' }}>
            No backend to hand?{' '}
            <a
              href="?demo=1"
              style={{ color: colors.marigoldDeep, textDecoration: 'none', fontWeight: 500 }}
            >
              Open the demo
            </a>{' '}
            — sample data, served entirely in your browser.
          </p>
        )}

        <p style={{ fontFamily: fonts.sans, fontSize: 11, color: colors.muted, margin: '24px 0 0' }}>
          Constituency S18-98 · Bhadrak, Odisha
        </p>
      </div>
    </div>
  );
}
