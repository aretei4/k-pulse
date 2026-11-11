import { Card, useToast } from '@/shared/components';
import { PageHeader } from '@/shared/layouts/PageHeader';
import { colors, fonts } from '@/shared/theme';
import { ChangePasswordForm } from './ChangePasswordForm';

/**
 * FA-25: what an agent sees after an administrator has reset their password.
 *
 * <p>No navigation out of it on purpose. The password they were given was
 * chosen to be easy to say down a phone line, so it must not stay in use — and
 * the agent cannot reach a voter list until they have replaced it.
 */
export function ForcedPasswordChange() {
  const { notify } = useToast();

  return (
    <div style={{ padding: '16px 20px 0' }}>
      <PageHeader title="Choose a new password" size={18} />
      <Card padding={14}>
        <p style={{ fontFamily: fonts.sans, fontSize: 12, color: colors.inkSoft, margin: '0 0 12px' }}>
          Your administrator gave you a temporary password. Pick one of your own now — the temporary one stops
          working as soon as you do.
        </p>
        <ChangePasswordForm
          needsCurrent
          currentLabel="Temporary password"
          onDone={() => notify('Password changed — you are signed in')}
        />
      </Card>
    </div>
  );
}
