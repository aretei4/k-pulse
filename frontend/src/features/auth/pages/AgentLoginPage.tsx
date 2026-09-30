import { useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { ArrowLeft, LogIn, Send } from 'lucide-react';
import { Button, ErrorNote, Eyebrow, Field, Heading, InfoNote, Sub } from '@/shared/components';
import { colors, fonts } from '@/shared/theme';
import { USE_MOCKS } from '@/lib/apiClient';
import { useAuth } from '@/shared/hooks/useAuth';
import { authApi } from '../services/authApi';

export function AgentLoginPage() {
  const navigate = useNavigate();
  const { signIn } = useAuth();
  const [phone, setPhone] = useState('');
  const [otp, setOtp] = useState('');
  const [otpSent, setOtpSent] = useState(false);
  const [devOtp, setDevOtp] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

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
          void (otpSent ? verify() : sendOtp());
        }}
        style={{ width: '100%', maxWidth: 460, padding: '32px 20px' }}
      >
        <Eyebrow>K-PULSE FIELD</Eyebrow>
        <Heading size={19}>Agent sign in</Heading>
        <Sub>{otpSent ? `Enter the code sent to ${phone}.` : 'Enter your registered mobile number.'}</Sub>

        {error && <ErrorNote message={error} />}

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

        <Button icon={otpSent ? LogIn : Send} type="submit" loading={busy} full>
          {otpSent ? 'Verify and continue' : 'Send OTP'}
        </Button>

        {otpSent && (
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
              Demo numbers: <strong>9861000011</strong> (Prakash Sahoo), <strong>9861000012</strong> (Anita Das). OTP{' '}
              <strong>123456</strong>.
            </InfoNote>
          </div>
        )}
      </form>
    </div>
  );
}
