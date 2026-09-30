import { useCallback, useEffect, useRef, useState } from 'react';

export interface AsyncState<T> {
  data: T | null;
  loading: boolean;
  error: string | null;
  reload: () => void;
  setData: (value: T | null) => void;
}

/**
 * Runs an async loader and keeps its result, with the two things every screen
 * here needs: a manual `reload` after a mutation, and stale-response guarding.
 */
export function useAsync<T>(loader: () => Promise<T>, deps: unknown[] = []): AsyncState<T> {
  const [data, setData] = useState<T | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [nonce, setNonce] = useState(0);
  const requestId = useRef(0);

  // The loader is rebuilt on every render; deps decide when it actually re-runs.
  const run = useCallback(loader, deps);

  useEffect(() => {
    const id = ++requestId.current;
    let alive = true;
    setLoading(true);
    setError(null);
    run()
      .then((result) => {
        if (alive && id === requestId.current) setData(result);
      })
      .catch((err: unknown) => {
        if (alive && id === requestId.current) setError(err instanceof Error ? err.message : 'Something went wrong');
      })
      .finally(() => {
        if (alive && id === requestId.current) setLoading(false);
      });
    return () => {
      alive = false;
    };
  }, [run, nonce]);

  return { data, loading, error, reload: () => setNonce((n) => n + 1), setData };
}
