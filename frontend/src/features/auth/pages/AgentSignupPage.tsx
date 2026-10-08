import { useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { UserPlus } from 'lucide-react';
import { Button, ErrorNote, Eyebrow, Field, Heading, Sub, useToast } from '@/shared/components';
import { colors, fonts } from '@/shared/theme';
import { authApi } from '../services/authApi';
import { MIN_PASSWORD_LENGTH } from '../constants';

export function AgentSignupPage() {
  const navigate = useNavigate();
  const { notify } = useToast();
  const [name, setName] = useState('');
  const [email, setEmail] = useState('');
  const [phone, setPhone] = useState('');
  const [address, setAddress] = useState('');
  const [password, setPassword] = useState('');
  const [confirm, setConfirm] = useState('');
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  async function submit() {
    if (!name.trim()) return setError('Enter your full name');
    if (!/^\S+@\S+\.\S+$/.test(email.trim())) return setError('Enter a valid email address');
    if (!/^\d{10}$/.test(phone.trim())) return setError('Enter a 10-digit mobile number');
    if (!address.trim()) return setError('Enter your village and panchayat');
    // Only checked once something has been typed: the password is optional.
    if (password && password.length < MIN_PASSWORD_LENGTH) {
      return setError(`A password must be at least ${MIN_PASSWORD_LENGTH} characters`);
    }
    if (password !== confirm) return setError('The two passwords do not match');

    setBusy(true);
    setError(null);
    try {
      await authApi.agentSignup({
        name: name.trim(),
        email: email.trim(),
        phone: phone.trim(),
        address: address.trim(),
        ...(password ? { password } : {}),
      });
      notify(
        password
          ? 'Account created — sign in with your email and password'
          : 'Account created — sign in with the OTP sent to your phone',
      );
      navigate('/agent/login', { replace: true });
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Could not create the account');
    } finally {
      setBusy(false);
    }
    return undefined;
  }

  return (
    <div style={{ minHeight: '100dvh', display: 'flex', justifyContent: 'center', background: colors.paper }}>
      <form
        onSubmit={(e) => {
          e.preventDefault();
          void submit();
        }}
        style={{ width: '100%', maxWidth: 460, padding: '32px 20px' }}
      >
        <Eyebrow>K-PULSE FIELD</Eyebrow>
        <Heading size={19}>Create account</Heading>
        <Sub>Register as a field agent. An admin approves your unit access separately.</Sub>

        {error && <ErrorNote message={error} />}

        <Field label="Full name" placeholder="Prakash Sahoo" value={name} onChange={setName} />
        <Field label="Email ID" placeholder="agent@example.com" type="email" value={email} onChange={setEmail} />
        <Field
          label="Phone number"
          placeholder="98XXXXXX21"
          type="tel"
          value={phone}
          onChange={(v) => setPhone(v.replace(/\D/g, '').slice(0, 10))}
        />
        <Field label="Address" placeholder="Village, Panchayat" value={address} onChange={setAddress} />

        <div
          style={{
            marginTop: 18,
            paddingTop: 14,
            borderTop: `1px solid ${colors.line}`,
          }}
        >
          <div
            style={{
              fontFamily: fonts.sans,
              fontSize: 11,
              letterSpacing: 0.6,
              textTransform: 'uppercase',
              color: colors.inkSoft,
              marginBottom: 2,
            }}
          >
            Password — optional
          </div>
          <div style={{ fontFamily: fonts.sans, fontSize: 12, color: colors.inkSoft, marginBottom: 10 }}>
            Set one to sign in with your email ID. Leave it blank and you will sign in with an OTP sent to
            your phone, as before.
          </div>

          <Field
            label="Password"
            placeholder={`At least ${MIN_PASSWORD_LENGTH} characters`}
            type="password"
            value={password}
            onChange={setPassword}
          />
          {password && (
            <Field
              label="Confirm password"
              placeholder="Type it again"
              type="password"
              value={confirm}
              onChange={setConfirm}
            />
          )}
        </div>

        <Button icon={UserPlus} type="submit" loading={busy} full>
          Create account
        </Button>

        <div style={{ marginTop: 16 }}>
          <Link
            to="/agent/login"
            style={{ fontFamily: fonts.sans, fontSize: 12, color: colors.inkSoft, textDecoration: 'none' }}
          >
            Already registered? Sign in
          </Link>
        </div>
      </form>
    </div>
  );
}
