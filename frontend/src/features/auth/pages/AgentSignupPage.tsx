import { useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { UserPlus } from 'lucide-react';
import { Button, ErrorNote, Eyebrow, Field, Heading, Sub, useToast } from '@/shared/components';
import { colors, fonts } from '@/shared/theme';
import { authApi } from '../services/authApi';

export function AgentSignupPage() {
  const navigate = useNavigate();
  const { notify } = useToast();
  const [name, setName] = useState('');
  const [email, setEmail] = useState('');
  const [phone, setPhone] = useState('');
  const [address, setAddress] = useState('');
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  async function submit() {
    if (!name.trim()) return setError('Enter your full name');
    if (!/^\S+@\S+\.\S+$/.test(email.trim())) return setError('Enter a valid email address');
    if (!/^\d{10}$/.test(phone.trim())) return setError('Enter a 10-digit mobile number');
    if (!address.trim()) return setError('Enter your village and panchayat');

    setBusy(true);
    setError(null);
    try {
      await authApi.agentSignup({ name: name.trim(), email: email.trim(), phone: phone.trim(), address: address.trim() });
      notify('Account created — sign in with the OTP sent to your phone');
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
