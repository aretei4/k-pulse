import { useState } from 'react';
import { Link } from 'react-router-dom';
import { Trash2 } from 'lucide-react';
import { Button, ErrorNote, Field, InfoNote } from '@/shared/components';
import { colors, fonts } from '@/shared/theme';
import { accountDeletionApi } from '../services/accountDeletionApi';

const listItem = { fontFamily: fonts.sans, fontSize: 13, color: colors.inkSoft, margin: '0 0 4px' } as const;

/**
 * "/delete-account" — the public page Google Play requires: reachable without
 * signing in, says what deletion removes, and files the request.
 *
 * Filing does not delete anything. An administrator reviews the request, checks
 * the person asking is the agent named, and approves; approval erases the
 * account and every entry that agent recorded.
 */
export function DeleteAccountPage() {
  const [phone, setPhone] = useState('');
  const [reason, setReason] = useState('');
  const [error, setError] = useState<string | null>(null);
  const [done, setDone] = useState(false);
  const [busy, setBusy] = useState(false);

  async function submit() {
    if (!/^\d{10}$/.test(phone.trim())) return setError('Enter the 10-digit mobile number you registered with');

    setBusy(true);
    setError(null);
    try {
      await accountDeletionApi.requestPublic({ phone: phone.trim(), reason: reason.trim() || undefined });
      setDone(true);
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Could not send the request. Please try again.');
    } finally {
      setBusy(false);
    }
    return undefined;
  }

  return (
    <div style={{ minHeight: '100dvh', padding: '32px 20px' }}>
      <div style={{ maxWidth: 560, margin: '0 auto' }}>
        <p
          style={{
            fontFamily: fonts.mono,
            fontSize: 11,
            letterSpacing: 1,
            color: colors.marigoldDeep,
            margin: '0 0 6px',
          }}
        >
          K-PULSE
        </p>
        <h1 style={{ fontFamily: fonts.serif, fontWeight: 700, fontSize: 26, color: colors.ink, margin: '0 0 6px' }}>
          Delete your account
        </h1>
        <p style={{ fontFamily: fonts.sans, fontSize: 14, color: colors.inkSoft, margin: '0 0 20px' }}>
          Ask for your K-Pulse field agent account to be removed. This page is for agents of the K-Pulse app; it is not
          a way to remove a voter from the electoral roll.
        </p>

        {done ? (
          <InfoNote>
            Request received. An administrator will check with you on this number before your account is deleted. You
            can close this page.
          </InfoNote>
        ) : (
          <>
            <div
              style={{
                border: `1px solid ${colors.line}`,
                borderRadius: 10,
                background: colors.card,
                padding: 16,
                marginBottom: 18,
              }}
            >
              <p style={{ fontFamily: fonts.sans, fontSize: 13, fontWeight: 500, color: colors.ink, margin: '0 0 8px' }}>
                What is deleted
              </p>
              <p style={listItem}>• Your account: name, mobile number, email address and postal address</p>
              <p style={listItem}>• Every sentiment entry you recorded, for every voter and candidate</p>
              <p style={listItem}>• Every pre-election house tally you recorded</p>
              <p style={listItem}>• Your access requests, and any voter changes you proposed</p>
              <p style={{ ...listItem, margin: '10px 0 0', color: colors.marigoldDeep }}>
                This cannot be undone, and the campaign loses the canvassing work recorded under your account. The
                electoral roll itself is not affected: voter records stay, because they do not belong to your account.
              </p>
            </div>

            <p style={{ fontFamily: fonts.sans, fontSize: 13, color: colors.inkSoft, margin: '0 0 12px' }}>
              An administrator reviews every request and confirms with you before anything is deleted. If you are signed
              in to the app, you can also ask from the Status screen.
            </p>

            {error && <ErrorNote message={error} />}

            <Field
              label="Registered mobile number"
              placeholder="98XXXXXX21"
              type="tel"
              value={phone}
              onChange={(v) => setPhone(v.replace(/[^0-9]/g, '').slice(0, 10))}
              maxLength={10}
            />
            <Field
              label="Reason (optional)"
              placeholder="e.g. I have left the campaign"
              value={reason}
              onChange={setReason}
              maxLength={500}
            />

            <Button tone="danger" icon={Trash2} loading={busy} onClick={() => void submit()}>
              Request account deletion
            </Button>
          </>
        )}

        <p style={{ fontFamily: fonts.sans, fontSize: 12, color: colors.muted, margin: '20px 0 0' }}>
          <Link to="/" style={{ color: colors.inkSoft }}>
            K-Pulse home
          </Link>
          {' · '}
          <a href="privacy.html" style={{ color: colors.inkSoft }}>
            Privacy policy
          </a>
        </p>
      </div>
    </div>
  );
}
