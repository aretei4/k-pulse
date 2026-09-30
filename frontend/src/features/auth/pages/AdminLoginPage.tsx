import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { LogIn } from 'lucide-react';
import { Button, ErrorNote, Eyebrow, Field, Heading, InfoNote, Sub } from '@/shared/components';
import { useAuth } from '@/shared/hooks/useAuth';
import { USE_MOCKS } from '@/lib/apiClient';
import { authApi } from '../services/authApi';

export function AdminLoginPage() {
  const navigate = useNavigate();
  const { signIn } = useAuth();
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  async function submit() {
    if (!email.trim() || !password) {
      setError('Enter your work email and password');
      return;
    }
    setBusy(true);
    setError(null);
    try {
      const session = await authApi.adminLogin({ email: email.trim(), password });
      signIn(session);
      navigate('/admin', { replace: true });
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Sign in failed');
    } finally {
      setBusy(false);
    }
  }

  return (
    <div style={{ minHeight: '100dvh', display: 'flex', alignItems: 'center', justifyContent: 'center', padding: 24 }}>
      <form
        onSubmit={(e) => {
          e.preventDefault();
          void submit();
        }}
        style={{ width: '100%', maxWidth: 340 }}
      >
        <Eyebrow>K-PULSE ADMIN</Eyebrow>
        <Heading size={22}>Sign in</Heading>
        <Sub>Manage sentiment data across your constituency.</Sub>

        {error && <ErrorNote message={error} />}

        <Field label="Work email" placeholder="admin@k-pulse.in" type="email" value={email} onChange={setEmail} />
        <Field label="Password" placeholder="••••••••" type="password" value={password} onChange={setPassword} />

        <div style={{ marginTop: 16 }}>
          <Button icon={LogIn} type="submit" loading={busy}>
            {busy ? 'Signing in…' : 'Sign in'}
          </Button>
        </div>

        {USE_MOCKS && (
          <div style={{ marginTop: 16 }}>
            <InfoNote>
              Demo mode — sign in with <strong>admin@k-pulse.in</strong> / <strong>admin123</strong>.
            </InfoNote>
          </div>
        )}
      </form>
    </div>
  );
}
