import { createContext, useCallback, useContext, useEffect, useMemo, useState } from 'react';
import type { ReactNode } from 'react';
import { Navigate, useLocation } from 'react-router-dom';
import { authApi } from '@/features/auth/services/authApi';
import { USE_MOCKS, setToken } from '@/lib/apiClient';
import { isAdminRole } from '@/shared/types';
import type { AuthSession, AuthUser, Role } from '@/shared/types';

const USER_KEY = 'kpulse.user';

interface AuthContextValue {
  user: AuthUser | null;
  ready: boolean;
  signIn: (session: AuthSession) => void;
  signOut: () => void;
}

const AuthContext = createContext<AuthContextValue>({
  user: null,
  ready: false,
  signIn: () => undefined,
  signOut: () => undefined,
});

function readStoredUser(): AuthUser | null {
  try {
    const raw = window.localStorage.getItem(USER_KEY);
    return raw ? (JSON.parse(raw) as AuthUser) : null;
  } catch {
    return null;
  }
}

export function AuthProvider({ children }: { children: ReactNode }) {
  const [user, setUser] = useState<AuthUser | null>(null);
  const [ready, setReady] = useState(false);

  useEffect(() => {
    const stored = readStoredUser();
    if (!stored) {
      setReady(true);
      return;
    }
    if (USE_MOCKS) {
      // Mock mode holds the session in memory — re-seat the user after a reload.
      // Imported lazily so the mock module stays out of a production build.
      void import('@/mocks/mockApi').then((m) => m.setMockUser(stored));
      setUser(stored);
      setReady(true);
      return;
    }
    authApi
      .me()
      .then((fresh) => setUser(fresh))
      .catch(() => {
        window.localStorage.removeItem(USER_KEY);
        setToken(null);
      })
      .finally(() => setReady(true));
  }, []);

  const signIn = useCallback((session: AuthSession) => {
    setToken(session.token);
    try {
      window.localStorage.setItem(USER_KEY, JSON.stringify(session.user));
    } catch {
      /* private mode — the session just won't survive a reload */
    }
    setUser(session.user);
  }, []);

  const signOut = useCallback(() => {
    void authApi.logout().catch(() => undefined);
    setToken(null);
    try {
      window.localStorage.removeItem(USER_KEY);
    } catch {
      /* ignore */
    }
    if (USE_MOCKS) void import('@/mocks/mockApi').then((m) => m.setMockUser(null));
    setUser(null);
  }, []);

  const value = useMemo(() => ({ user, ready, signIn, signOut }), [user, ready, signIn, signOut]);
  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

export function useAuth() {
  return useContext(AuthContext);
}

/** Route guard — the UI half of the role split that SecurityConfig enforces server-side. */
export function RequireRole({ role, children }: { role: Role; children: ReactNode }) {
  const { user, ready } = useAuth();
  const location = useLocation();

  // The guard is per route group: /admin takes either admin kind, and what a
  // scoped admin may actually see is enforced by the server, not hidden here.
  const allows = (actual: Role) => (isAdminRole(role) ? isAdminRole(actual) : actual === role);

  if (!ready) return null;
  if (!user) {
    const loginPath = isAdminRole(role) ? '/admin/login' : '/agent/login';
    return <Navigate to={loginPath} replace state={{ from: location.pathname }} />;
  }
  if (!allows(user.role)) {
    return <Navigate to={isAdminRole(user.role) ? '/admin' : '/agent'} replace />;
  }
  return <>{children}</>;
}
