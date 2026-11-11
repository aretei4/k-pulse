import { NavLink, Outlet, useLocation, useNavigate } from 'react-router-dom';
import { BarChart3, FileText, Home, LogOut, Users } from 'lucide-react';
import { colors, fonts } from '@/shared/theme';
import { useAuth } from '@/shared/hooks/useAuth';
import { ForcedPasswordChange } from '@/features/agent-status/components/ForcedPasswordChange';

const navItems = [
  { to: '/agent/voters', label: 'Voters', icon: Users, end: false },
  { to: '/agent/insights', label: 'Insights', icon: BarChart3, end: false },
  // AG-1: only for an agent an admin has given the Reports page to. The server
  // refuses the calls either way; this keeps a tab that would 403 off screen.
  { to: '/agent/reports', label: 'Reports', icon: FileText, end: false, needsReports: true },
  { to: '/agent/status', label: 'Status', icon: Home, end: false },
];

/**
 * Downloading a list is reached from Insights and belongs to it, but its route
 * sits under /agent/voters because that is the data it prints. Left to the
 * paths alone the wrong tab would light up, so this one is named.
 */
const DOWNLOAD_PATH = '/agent/voters/download';

/**
 * The agent shell. It fills a phone WebView edge to edge and centres itself on
 * a desktop browser, so `/agent` is usable from either without a second build.
 */
export function AgentLayout() {
  const { user, signOut } = useAuth();
  const navigate = useNavigate();
  const { pathname } = useLocation();

  return (
    <div style={{ minHeight: '100dvh', background: colors.paper, display: 'flex', justifyContent: 'center' }}>
      <div
        style={{
          width: '100%',
          maxWidth: 460,
          minHeight: '100dvh',
          display: 'flex',
          flexDirection: 'column',
          background: colors.paper,
          borderLeft: `1px solid ${colors.line}`,
          borderRight: `1px solid ${colors.line}`,
        }}
      >
        <header
          style={{
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'space-between',
            padding: '12px 20px 8px',
            borderBottom: `1px solid ${colors.line}`,
            background: colors.card,
          }}
        >
          <div>
            <p style={{ fontFamily: fonts.mono, fontSize: 10, letterSpacing: 1, color: colors.marigoldDeep, margin: 0 }}>
              K-PULSE FIELD
            </p>
            <p style={{ fontFamily: fonts.sans, fontSize: 13, fontWeight: 500, color: colors.ink, margin: 0 }}>
              {user?.name}
            </p>
          </div>
          <button
            onClick={() => {
              signOut();
              navigate('/agent/login', { replace: true });
            }}
            aria-label="Sign out"
            style={{ background: 'none', border: 'none', cursor: 'pointer', color: colors.inkSoft, padding: 4 }}
          >
            <LogOut size={16} />
          </button>
        </header>

        <main style={{ flex: 1, overflowY: 'auto', paddingBottom: 12 }} className="kp-fade">
          {/* FA-25: a password an admin set is temporary. Until it is replaced
              this is the only screen the agent gets, which is the whole point
              of letting an admin choose an easy one. */}
          {user?.mustChangePassword ? <ForcedPasswordChange /> : <Outlet />}
        </main>

        <nav
          style={{
            display: 'flex',
            borderTop: `1px solid ${colors.line}`,
            background: colors.card,
            paddingBottom: 'env(safe-area-inset-bottom)',
            position: 'sticky',
            bottom: 0,
          }}
        >
          {navItems
            .filter((item) => !item.needsReports || user?.reportsEnabled)
            .map((item) => {
            const Icon = item.icon;
            const downloading = pathname === DOWNLOAD_PATH;
            const active = (match: boolean) =>
              downloading ? item.to === '/agent/insights' : match;
            return (
              <NavLink
                key={item.to}
                to={item.to}
                end={item.end}
                style={({ isActive }) => ({
                  flex: 1,
                  display: 'flex',
                  flexDirection: 'column',
                  alignItems: 'center',
                  gap: 2,
                  padding: '9px 0 8px',
                  textDecoration: 'none',
                  color: active(isActive) ? colors.marigoldDeep : colors.inkSoft,
                })}
              >
                {({ isActive }) => (
                  <>
                    <Icon size={16} strokeWidth={active(isActive) ? 2.4 : 2} />
                    <span
                      style={{ fontFamily: fonts.sans, fontSize: 10, fontWeight: active(isActive) ? 600 : 400 }}
                    >
                      {item.label}
                    </span>
                  </>
                )}
              </NavLink>
            );
          })}
        </nav>
      </div>
    </div>
  );
}
