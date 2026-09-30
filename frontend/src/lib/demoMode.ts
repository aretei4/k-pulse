/**
 * Demo mode — the in-browser mock API, switchable at runtime in a production
 * build so the app can be shown without the Spring Boot backend running.
 *
 * Deliberately opt-in rather than a fallback. An automatic "API unreachable, so
 * use mocks" rule would turn a backend outage into a screen full of convincing
 * fake numbers, which is worse than an honest error.
 *
 * Turn on:  /kpulse/agent/login?demo=1
 * Turn off: /kpulse/agent/login?demo=0
 *
 * The choice is kept in sessionStorage because react-router drops the query
 * string on the first in-app navigation, and because it must not leak into
 * another tab or outlive the browser session.
 */

const KEY = 'kpulse.demo';

/** Build-time default. `.env.production` sets this to false. */
const BUILD_DEFAULT = import.meta.env.VITE_USE_MOCKS !== 'false';

function readFlag(): boolean {
  // A build that defaults to mocks (local `npm run dev`) is always in demo mode.
  if (BUILD_DEFAULT) return true;

  let stored: string | null = null;
  try {
    stored = window.sessionStorage.getItem(KEY);
  } catch {
    /* private mode / storage disabled — fall through to the URL */
  }

  const param = new URLSearchParams(window.location.search).get('demo');
  if (param === '1' || param === 'true') {
    try {
      window.sessionStorage.setItem(KEY, '1');
    } catch {
      /* ignore — demo still works for this page load */
    }
    return true;
  }
  if (param === '0' || param === 'false') {
    try {
      window.sessionStorage.removeItem(KEY);
      window.localStorage.removeItem('kpulse.token');
      window.localStorage.removeItem('kpulse.user');
    } catch {
      /* ignore */
    }
    return false;
  }

  return stored === '1';
}

/**
 * Resolved once at module load. Everything downstream reads a stable value, so
 * the app cannot be half in demo mode after a render.
 */
export const DEMO_MODE = readFlag();

/** True when demo mode was switched on at runtime rather than baked in. */
export const DEMO_IS_RUNTIME = DEMO_MODE && !BUILD_DEFAULT;

/** Drops demo mode and reloads at a clean URL. */
export function exitDemo() {
  try {
    window.sessionStorage.removeItem(KEY);
    window.localStorage.removeItem('kpulse.token');
    window.localStorage.removeItem('kpulse.user');
  } catch {
    /* ignore */
  }
  const url = new URL(window.location.href);
  url.searchParams.delete('demo');
  window.location.replace(url.toString());
}
