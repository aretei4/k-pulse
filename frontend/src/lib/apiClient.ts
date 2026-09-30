import { DEMO_MODE } from './demoMode';

const BASE_URL = import.meta.env.VITE_API_BASE_URL ?? '';

/**
 * Whether calls are served by the in-browser mock API instead of the real one.
 * Resolved at module load from the build default plus the runtime demo flag —
 * see demoMode.ts. `src/mocks` stays behind a dynamic import below, so a real
 * user never downloads the seed dataset; it is a separate chunk fetched only
 * when demo mode is actually on.
 */
export const USE_MOCKS = DEMO_MODE;

const TOKEN_KEY = 'kpulse.token';

export class ApiError extends Error {
  status: number;
  constructor(message: string, status: number) {
    super(message);
    this.name = 'ApiError';
    this.status = status;
  }
}

export function getToken(): string | null {
  try {
    return window.localStorage.getItem(TOKEN_KEY);
  } catch {
    return null;
  }
}

export function setToken(token: string | null) {
  try {
    if (token) window.localStorage.setItem(TOKEN_KEY, token);
    else window.localStorage.removeItem(TOKEN_KEY);
  } catch {
    /* private-mode browsers: the session simply won't survive a reload */
  }
}

export interface RequestOptions {
  method?: 'GET' | 'POST' | 'PUT' | 'PATCH' | 'DELETE';
  body?: unknown;
  query?: Record<string, string | number | boolean | undefined | null>;
  /** Multipart upload — set instead of `body`. */
  formData?: FormData;
  /** Expect a file back rather than JSON. */
  blob?: boolean;
}

function buildUrl(path: string, query?: RequestOptions['query']) {
  const qs = new URLSearchParams();
  Object.entries(query ?? {}).forEach(([k, v]) => {
    if (v !== undefined && v !== null && v !== '') qs.append(k, String(v));
  });
  const suffix = qs.toString() ? `?${qs}` : '';
  return `${BASE_URL}${path}${suffix}`;
}

/**
 * The single door to the Spring Boot API. In mock mode every call is answered
 * from `src/mocks` instead, so the whole UI is demoable with no backend running.
 */
export async function request<T>(path: string, options: RequestOptions = {}): Promise<T> {
  const { method = 'GET', body, query, formData, blob } = options;

  if (USE_MOCKS) {
    const { handleMock } = await import('@/mocks/mockApi');
    return handleMock<T>(method, path, { body, query, formData });
  }

  const headers: Record<string, string> = {};
  const token = getToken();
  if (token) headers.Authorization = `Bearer ${token}`;
  if (body !== undefined) headers['Content-Type'] = 'application/json';

  const response = await fetch(buildUrl(path, query), {
    method,
    headers,
    body: formData ?? (body !== undefined ? JSON.stringify(body) : undefined),
  });

  if (!response.ok) {
    let message = `Request failed (${response.status})`;
    try {
      const problem = await response.json();
      message = problem.message ?? problem.error ?? message;
    } catch {
      /* non-JSON error body */
    }
    if (response.status === 401) setToken(null);
    throw new ApiError(message, response.status);
  }

  if (blob) return (await response.blob()) as unknown as T;
  if (response.status === 204) return undefined as T;

  // The backend wraps payloads in ApiResponse { success, data, message }.
  const json = await response.json();
  return (json && typeof json === 'object' && 'data' in json ? json.data : json) as T;
}

export function downloadBlob(blob: Blob, fileName: string) {
  const url = URL.createObjectURL(blob);
  const anchor = document.createElement('a');
  anchor.href = url;
  anchor.download = fileName;
  document.body.appendChild(anchor);
  anchor.click();
  anchor.remove();
  URL.revokeObjectURL(url);
}
