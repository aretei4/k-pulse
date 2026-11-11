import { useState } from 'react';
import { KeyRound } from 'lucide-react';
import { Button, Card, Modal, Spinner, Tag, useToast } from '@/shared/components';
import { useAsync } from '@/shared/hooks/useAsync';
import { colors, fonts } from '@/shared/theme';
import { formatDate } from '@/shared/utils/format';
import { agentPasswordApi } from '../services/agentPasswordApi';
import { ChangePasswordForm } from './ChangePasswordForm';

/**
 * FA-24: the agent's own sign-in settings.
 *
 * <p>Asking is all the agent can do — only an admin sets a password, and they
 * hand it over in person or by phone. Nothing is sent by SMS, so the card says
 * so rather than leaving the agent waiting for a message.
 */
export function SignInCard() {
  const { notify } = useToast();
  const status = useAsync(() => agentPasswordApi.status(), []);
  const [changing, setChanging] = useState(false);
  const [busy, setBusy] = useState(false);

  async function ask(request: boolean) {
    setBusy(true);
    try {
      await (request ? agentPasswordApi.requestReset() : agentPasswordApi.withdrawRequest());
      notify(request ? 'Your administrator will set a new password for you' : 'Request withdrawn');
      status.reload();
    } catch (err) {
      notify(err instanceof Error ? err.message : 'Could not send the request', 'error');
    } finally {
      setBusy(false);
    }
  }

  const waiting = Boolean(status.data?.resetRequestedAt);

  return (
    <Card padding={14} style={{ marginBottom: 14 }}>
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', gap: 8 }}>
        <p style={{ fontFamily: fonts.serif, fontSize: 15, color: colors.ink, margin: 0 }}>Signing in</p>
        {waiting && <Tag color={colors.marigoldDeep}>Reset requested</Tag>}
      </div>

      {status.loading && <Spinner />}

      {status.data && (
        <>
          <p style={{ fontFamily: fonts.sans, fontSize: 12, color: colors.inkSoft, margin: '6px 0 0' }}>
            {status.data.passwordSet
              ? 'You can sign in with your mobile number and a one-time code, or with your email and password.'
              : 'You sign in with a one-time code sent to your mobile number. You have no password yet.'}
          </p>

          {waiting && (
            <p style={{ fontFamily: fonts.sans, fontSize: 12, color: colors.marigoldDeep, margin: '8px 0 0' }}>
              Asked on {formatDate(status.data.resetRequestedAt)}. Your administrator will give you a temporary
              password — it is not sent by SMS.
            </p>
          )}

          <div style={{ display: 'flex', gap: 8, marginTop: 12, flexWrap: 'wrap' }}>
            <Button tone="ghost" small icon={KeyRound} onClick={() => setChanging(true)}>
              {status.data.passwordSet ? 'Change password' : 'Set a password'}
            </Button>
            {waiting ? (
              <Button tone="ghost" small loading={busy} onClick={() => void ask(false)}>
                Withdraw request
              </Button>
            ) : (
              <Button tone="ghost" small loading={busy} onClick={() => void ask(true)}>
                Ask the admin to reset it
              </Button>
            )}
          </div>
        </>
      )}

      <Modal
        open={changing}
        title={status.data?.passwordSet ? 'Change your password' : 'Set a password'}
        subtitle="Only you will know it."
        onClose={() => setChanging(false)}
      >
        <ChangePasswordForm
          needsCurrent={Boolean(status.data?.passwordSet)}
          onDone={() => {
            setChanging(false);
            notify('Password changed');
            status.reload();
          }}
        />
      </Modal>
    </Card>
  );
}
