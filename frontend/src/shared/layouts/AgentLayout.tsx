import { NavLink, Outlet, useNavigate } from 'react-router-dom';
import { BarChart3, Home, LogOut, Users } from 'lucide-react';
import { colors, fonts } from '@/shared/theme';
import { useAuth } from '@/shared/hooks/useAuth';

const navItems = [
  { to: '/agent/voters', label: 'Voters', icon: Users, end: false },
  { to: '/agent/insights', label: 'Insights', icon: BarChart3, end: false },
  { to: '/agent/status', label: 'Status', icon: Home, end: false },
];

/**
 * The agent shell. It fills a phone WebView edge to edge and centres itself on
 * a desktop browser, so `/agent` is usable from either without a second build.
 */
export function AgentLayout() {
  const { user, signOut } = useAuth();
  const navigate = useNavigate();

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
          <Outlet />
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
          {navItems.map((item) => {
            const Icon = item.icon;
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
                  color: isActive ? colors.marigoldDeep : colors.inkSoft,
                })}
              >
                {({ isActive }) => (
                  <>
                    <Icon size={16} strokeWidth={isActive ? 2.4 : 2} />
                    <span style={{ fontFamily: fonts.sans, fontSize: 10, fontWeight: isActive ? 600 : 400 }}>
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
