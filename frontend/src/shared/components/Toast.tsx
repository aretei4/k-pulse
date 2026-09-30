import { createContext, useCallback, useContext, useMemo, useState } from 'react';
import type { ReactNode } from 'react';
import { CheckCircle2, XCircle } from 'lucide-react';
import { colors, fonts } from '@/shared/theme';

interface Toast {
  id: number;
  message: string;
  tone: 'success' | 'error';
}

const ToastContext = createContext<{ notify: (message: string, tone?: 'success' | 'error') => void }>({
  notify: () => undefined,
});

export function ToastProvider({ children }: { children: ReactNode }) {
  const [toasts, setToasts] = useState<Toast[]>([]);

  const notify = useCallback((message: string, tone: 'success' | 'error' = 'success') => {
    const id = Date.now() + Math.random();
    setToasts((prev) => [...prev, { id, message, tone }]);
    window.setTimeout(() => setToasts((prev) => prev.filter((t) => t.id !== id)), 3200);
  }, []);

  const value = useMemo(() => ({ notify }), [notify]);

  return (
    <ToastContext.Provider value={value}>
      {children}
      <div
        style={{
          position: 'fixed',
          bottom: 20,
          left: '50%',
          transform: 'translateX(-50%)',
          display: 'flex',
          flexDirection: 'column',
          gap: 8,
          zIndex: 200,
          pointerEvents: 'none',
        }}
      >
        {toasts.map((t) => (
          <div
            key={t.id}
            className="kp-fade"
            style={{
              display: 'flex',
              alignItems: 'center',
              gap: 8,
              background: colors.ink,
              color: '#FFF',
              borderRadius: 8,
              padding: '10px 14px',
              fontFamily: fonts.sans,
              fontSize: 12,
              boxShadow: '0 6px 18px rgba(21,42,56,0.22)',
              maxWidth: 340,
            }}
          >
            {t.tone === 'success' ? (
              <CheckCircle2 size={14} color="#9DBFA6" />
            ) : (
              <XCircle size={14} color="#E7A491" />
            )}
            {t.message}
          </div>
        ))}
      </div>
    </ToastContext.Provider>
  );
}

export function useToast() {
  return useContext(ToastContext);
}
