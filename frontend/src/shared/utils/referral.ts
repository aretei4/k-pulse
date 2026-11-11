/**
 * The referral number rule, matching ReferralNumbers on the server: the mobile
 * number of whoever sent the agent. Checked here only so the person typing is
 * told at once; the server checks again and has the last word.
 */
export const REFERRAL_HINT = 'The 10-digit mobile number of whoever referred you. Leave blank if nobody did.';

/** Digits only while typing, so a pasted "+91 98610 00011" still lands as ten. */
export function cleanReferral(raw: string) {
  const digits = raw.replace(/[^\d]/g, '');
  if (digits.length === 12 && digits.startsWith('91')) return digits.slice(2);
  return digits.slice(0, 10);
}

/** The reason it cannot be used, or null when it is fine (including empty). */
export function referralProblem(value: string, ownPhone?: string): string | null {
  const digits = cleanReferral(value);
  if (!digits) return null;
  if (!/^[6-9]\d{9}$/.test(digits)) {
    return 'A referral number is the 10-digit mobile number of whoever referred the agent';
  }
  if (ownPhone && digits === ownPhone.replace(/[^\d]/g, '')) {
    return 'An agent cannot be their own referral';
  }
  return null;
}
