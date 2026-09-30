import { NavLink, Outlet, useNavigate } from 'react-router-dom';
import {
  BarChart3,
  ClipboardList,
  FileSpreadsheet,
  FileText,
  Home,
  LogOut,
  MapPin,
  ShieldCheck,
  Trash2,
  Users2,
  UserSquare2,
} from 'lucide-react';
import { colors, fonts } from '@/shared/theme';
import { useAuth } from '@/shared/hooks/useAuth';

const navItems = [
  { to: '/admin', label: 'Dashboard', icon: BarChart3, end: true },
  { to: '/admin/requests', label: 'Access requests', icon: ClipboardList, end: false },
  { to: '/admin/voter-lists', label: 'Voter lists', icon: FileSpreadsheet, end: false },
  { to: '/admin/voter-changes', label: 'Change requests', icon: UserSquare2, end: false },
  { to: '/admin/pre-election', label: 'Pre-election', icon: Home, end: false },
  { to: '/admin/locations', label: 'Locations', icon: MapPin, end: false },
  { to: '/admin/users', label: 'Field agents', icon: Users2, end: false },
  { to: '/admin/admins', label: 'Administrators', icon: ShieldCheck, end: false, superAdminOnly: true },
  { to: '/admin/account-deletions', label: 'Account deletions', icon: Trash2, end: false },
  { to: '/admin/reports', label: 'Reports', icon: FileText, end: false },
];

/**
 * Admin shell with the menu down the left. The sidebar stays put while the page
 * scrolls; below 900px the stylesheet lays it out across the top instead, so a
 * tablet does not lose most of its width to navigation.
 */
export function AdminLayout() {
  const { user, signOut } = useAuth();
  const navigate = useNavigate();

  return (
    <div className="kp-admin" style={{ minHeight: '100vh', background: colors.paper }}>
      <aside
        className="kp-admin-aside"
        style={{
          width: 226,
          flexShrink: 0,
          borderRight: `1px solid ${colors.line}`,
          background: colors.card,
          position: 'sticky',
          top: 0,
          alignSelf: 'flex-start',
          height: '100vh',
          display: 'flex',
          flexDirection: 'column',
          padding: '16px 12px 12px',
          boxSizing: 'border-box',
        }}
      >
        <div style={{ padding: '0 8px 14px' }}>
          <p style={{ fontFamily: fonts.mono, fontSize: 10, letterSpacing: 1, color: colors.marigoldDeep, margin: 0 }}>
            K-PULSE
          </p>
          <p style={{ fontFamily: fonts.serif, fontSize: 17, fontWeight: 600, color: colors.ink, margin: 0 }}>Admin</p>
        </div>

        <nav className="kp-admin-nav" style={{ display: 'flex', flexDirection: 'column', gap: 2, flex: 1 }}>
          {navItems
            .filter((item) => !item.superAdminOnly || user?.role === 'SUPER_ADMIN')
            .map((item) => {
            const Icon = item.icon;
            return (
              <NavLink
                key={item.to}
                to={item.to}
                end={item.end}
                style={({ isActive }) => ({
                  display: 'flex',
                  alignItems: 'center',
                  gap: 9,
                  padding: '9px 10px',
                  borderRadius: 7,
                  textDecoration: 'none',
                  fontFamily: fonts.sans,
                  fontSize: 13,
                  fontWeight: isActive ? 600 : 400,
                  color: isActive ? colors.marigoldDeep : colors.inkSoft,
                  background: isActive ? colors.marigoldWash : 'transparent',
                  whiteSpace: 'nowrap',
                })}
              >
                <Icon size={15} />
                {item.label}
              </NavLink>
            );
          })}
        </nav>

        <div
          className="kp-admin-account"
          style={{ borderTop: `1px solid ${colors.line}`, paddingTop: 10, marginTop: 10 }}
        >
          <p
            style={{
              fontFamily: fonts.sans,
              fontSize: 12,
              color: colors.inkSoft,
              margin: '0 0 8px',
              padding: '0 10px',
              overflow: 'hidden',
              textOverflow: 'ellipsis',
              whiteSpace: 'nowrap',
            }}
          >
            {user?.name}
          </p>
          <button
            onClick={() => {
              signOut();
              navigate('/admin/login', { replace: true });
            }}
            style={{
              display: 'flex',
              alignItems: 'center',
              gap: 6,
              width: '100%',
              background: 'none',
              border: `1px solid ${colors.line}`,
              borderRadius: 7,
              padding: '7px 10px',
              cursor: 'pointer',
              fontFamily: fonts.sans,
              fontSize: 12,
              color: colors.ink,
            }}
          >
            <LogOut size={13} /> Sign out
          </button>
        </div>
      </aside>

      <main className="kp-admin-main kp-fade" style={{ flex: 1, minWidth: 0, padding: 24 }}>
        <div style={{ maxWidth: 1080 }}>
          <Outlet />
        </div>
      </main>
    </div>
  );
}
