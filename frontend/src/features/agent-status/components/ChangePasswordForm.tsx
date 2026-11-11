import { useState } from 'react';
import { KeyRound } from 'lucide-react';
import { Button, ErrorNote, Field } from '@/shared/components';
import { colors, fonts } from '@/shared/theme';
import { useAuth } from '@/shared/hooks/useAuth';
import { agentPasswordApi } from '../services/agentPasswordApi';

/** The rule the agent's own password is held to, stated the way the server states it. */
export const PASSWORD_RULE = 'At least 8 characters with a letter and a number, and no spaces.';

export function meetsRule(password: string) {
  return password.length >= 8 && /[A-Za-z]/.test(password) && /\d/.test(password) && !/\s/.test(password);
}

/**
 * The agent choosing their own password (FA-24, FA-25).
 *
 * <p>Used twice: on their own Status page when they simply want to change it,
 * and full-screen after an admin reset, where it is the only thing they can do.
 * `needsCurrent` is false only for an agent who has no password at all yet.
 */
export function ChangePasswordForm({
  needsCurrent,
  currentLabel = 'Current password',
  onDone,
}: {
  needsCurrent: boolean;
  currentLabel?: string;
  onDone: () => void;
}) {
  const { updateUser } = useAuth();
  const [current, setCurrent] = useState('');
  const [next, setNext] = useState('');
  const [again, setAgain] = useState('');
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  async function save() {
    if (needsCurrent && !current) return setError('Enter the password you have now');
    if (!meetsRule(next)) return setError(PASSWORD_RULE);
    if (next !== again) return setError('The two new passwords do not match');

    setBusy(true);
    setError(null);
    try {
      await agentPasswordApi.change({ currentPassword: needsCurrent ? current : undefined, newPassword: next });
      // The forced screen watches this flag, so it has to come down with the save.
      updateUser({ mustChangePassword: false });
      onDone();
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Could not change your password');
    } finally {
      setBusy(false);
    }
    return undefined;
  }

  return (
    <>
      {error && <ErrorNote message={error} />}
      {needsCurrent && (
        <Field label={currentLabel} type="password" revealable value={current} onChange={setCurrent} />
      )}
      <Field label="New password" type="password" revealable value={next} onChange={setNext} hint={PASSWORD_RULE} />
      <Field label="New password again" type="password" revealable value={again} onChange={setAgain} />
      <Button tone="accent" icon={KeyRound} full loading={busy} onClick={() => void save()}>
        Save new password
      </Button>
      <p style={{ fontFamily: fonts.sans, fontSize: 11, color: colors.muted, margin: '8px 0 0' }}>
        Nobody else can see your password, not even an administrator. They can only set a temporary one.
      </p>
    </>
  );
}
