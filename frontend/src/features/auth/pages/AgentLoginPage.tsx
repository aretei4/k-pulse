import { useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { ArrowLeft, LogIn, Send } from 'lucide-react';
import { Button, ErrorNote, Eyebrow, Field, Heading, InfoNote, Sub } from '@/shared/components';
import { colors, fonts } from '@/shared/theme';
import { USE_MOCKS } from '@/lib/apiClient';
import { useAuth } from '@/shared/hooks/useAuth';
import { authApi } from '../services/authApi';

type Method = 'password' | 'otp';

/**
 * Both ways in are shown at once rather than hidden behind a link: agents who
 * registered before passwords existed have only the OTP, and agents who set one
 * should not have to hunt for it.
 */
function MethodTabs({ method, onChoose }: { method: Method; onChoose: (next: Method) => void }) {
  const tabs: Array<{ id: Method; label: string }> = [
    { id: 'password', label: 'Email & password' },
    { id: 'otp', label: 'Phone OTP' },
  ];
  return (
    <div style={{ display: 'flex', gap: 6, margin: '14px 0 4px' }}>
      {tabs.map((tab) => {
        const on = method === tab.id;
        return (
          <button
            key={tab.id}
            type="button"
            onClick={() => onChoose(tab.id)}
            aria-pressed={on}
            style={{
              flex: 1,
              padding: '9px 10px',
              cursor: 'pointer',
              fontFamily: fonts.sans,
              fontSize: 12,
              borderRadius: 7,
              border: `1px solid ${on ? colors.marigoldDeep : colors.line}`,
              background: on ? colors.marigoldDeep : 'transparent',
              color: on ? colors.paper : colors.inkSoft,
            }}
          >
            {tab.label}
          </button>
        );
      })}
    </div>
  );
}

export function AgentLoginPage() {
  const navigate = useNavigate();
  const { signIn } = useAuth();
  const [method, setMethod] = useState<Method>('password');
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [phone, setPhone] = useState('');
  const [otp, setOtp] = useState('');
  const [otpSent, setOtpSent] = useState(false);
  const [devOtp, setDevOtp] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  function choose(next: Method) {
    setMethod(next);
    setError(null);
  }

  async function signInWithPassword() {
    if (!/^\S+@\S+\.\S+$/.test(email.trim())) {
      setError('Enter the email ID you registered with');
      return;
    }
    if (!password) {
      setError('Enter your password');
      return;
    }
    setBusy(true);
    setError(null);
    try {
      const session = await authApi.agentLogin({ email: email.trim(), password });
      signIn(session);
      navigate('/agent', { replace: true });
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Could not sign in');
    } finally {
      setBusy(false);
    }
  }

  async function sendOtp() {
    if (!/^\d{10}$/.test(phone.trim())) {
      setError('Enter the 10-digit mobile number you registered with');
      return;
    }
    setBusy(true);
    setError(null);
    try {
      const result = await authApi.requestOtp(phone.trim());
      setOtpSent(true);
      setDevOtp(result.devOtp ?? null);
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Could not send the OTP');
    } finally {
      setBusy(false);
    }
  }

  async function verify() {
    if (otp.trim().length !== 6) {
      setError('Enter the 6-digit code');
      return;
    }
    setBusy(true);
    setError(null);
    try {
      const session = await authApi.verifyOtp(phone.trim(), otp.trim());
      signIn(session);
      navigate('/agent', { replace: true });
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Verification failed');
    } finally {
      setBusy(false);
    }
  }

  return (
    <div style={{ minHeight: '100dvh', display: 'flex', justifyContent: 'center', background: colors.paper }}>
      <form
        onSubmit={(e) => {
          e.preventDefault();
          if (method === 'password') {
            void signInWithPassword();
            return;
          }
          void (otpSent ? verify() : sendOtp());
        }}
        style={{ width: '100%', maxWidth: 460, padding: '32px 20px' }}
      >
        <Eyebrow>K-PULSE FIELD</Eyebrow>
        <Heading size={19}>Agent sign in</Heading>
        <Sub>
          {method === 'password'
            ? 'Sign in with your email ID and password.'
            : otpSent
              ? `Enter the code sent to ${phone}.`
              : 'Enter your registered mobile number.'}
        </Sub>

        <MethodTabs method={method} onChoose={choose} />

        {error && <ErrorNote message={error} />}

        {method === 'password' ? (
          <>
            <Field
              label="Email ID"
              placeholder="agent@example.com"
              type="email"
              value={email}
              onChange={setEmail}
            />
            <Field
              label="Password"
              placeholder="Your password"
              type="password"
              value={password}
              onChange={setPassword}
            />
          </>
        ) : (
          <>
            <Field
              label="Mobile number"
              placeholder="98XXXXXX21"
              type="tel"
              value={phone}
              onChange={(v) => setPhone(v.replace(/\D/g, '').slice(0, 10))}
              disabled={otpSent}
            />

            {otpSent && (
              <Field
                label="OTP"
                placeholder="6-digit code"
                type="tel"
                value={otp}
                onChange={(v) => setOtp(v.replace(/\D/g, '').slice(0, 6))}
                hint={devOtp ? `Demo mode — your code is ${devOtp}` : undefined}
              />
            )}
          </>
        )}

        <Button
          icon={method === 'password' || otpSent ? LogIn : Send}
          type="submit"
          loading={busy}
          full
        >
          {method === 'password' ? 'Sign in' : otpSent ? 'Verify and continue' : 'Send OTP'}
        </Button>

        {method === 'password' && (
          <div
            style={{
              marginTop: 14,
              fontFamily: fonts.sans,
              fontSize: 12,
              color: colors.inkSoft,
            }}
          >
            No password set?{' '}
            <button
              type="button"
              onClick={() => choose('otp')}
              style={{
                background: 'none',
                border: 'none',
                padding: 0,
                cursor: 'pointer',
                fontFamily: fonts.sans,
                fontSize: 12,
                color: colors.marigoldDeep,
                textDecoration: 'underline',
              }}
            >
              Sign in with an OTP instead
            </button>
          </div>
        )}

        {method === 'otp' && otpSent && (
          <button
            type="button"
            onClick={() => {
              setOtpSent(false);
              setOtp('');
              setError(null);
            }}
            style={{
              display: 'flex',
              alignItems: 'center',
              gap: 4,
              marginTop: 14,
              background: 'none',
              border: 'none',
              cursor: 'pointer',
              fontFamily: fonts.sans,
              fontSize: 12,
              color: colors.inkSoft,
            }}
          >
            <ArrowLeft size={13} /> Use a different number
          </button>
        )}

        <div style={{ marginTop: 18 }}>
          <Link
            to="/agent/signup"
            style={{ fontFamily: fonts.sans, fontSize: 12, color: colors.marigoldDeep, textDecoration: 'none' }}
          >
            New agent? Create an account
          </Link>
        </div>

        {USE_MOCKS && (
          <div style={{ marginTop: 20 }}>
            <InfoNote>
              {method === 'password' ? (
                <>
                  Demo logins: <strong>prakash@example.com</strong> or <strong>anita@example.com</strong>, password{' '}
                  <strong>agent123</strong>.
                </>
              ) : (
                <>
                  Demo numbers: <strong>9861000011</strong> (Prakash Sahoo), <strong>9861000012</strong> (Anita Das).
                  OTP <strong>123456</strong>.
                </>
              )}
            </InfoNote>
          </div>
        )}
      </form>
    </div>
  );
}
