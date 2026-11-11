/**
 * How an agent reaches the campaign office when the app cannot help them --
 * a panchayat missing from the master, say, which only an admin can add.
 *
 * The number is set in `.env` as `VITE_SUPPORT_WHATSAPP` so a campaign can
 * point it at its own office without a code change; the default is the number
 * the agents were given.
 */
const DEFAULT_NUMBER = '9836361146';

const digits = (import.meta.env.VITE_SUPPORT_WHATSAPP ?? DEFAULT_NUMBER).replace(/\D/g, '');

/** wa.me takes the country code and nothing else; Indian mobiles are 10 digits. */
const dialled = digits.length === 10 ? `91${digits}` : digits;

/** The number as a person reads it back: +91 98363 61146. */
export const SUPPORT_WHATSAPP_DISPLAY = dialled.length === 12
  ? `+${dialled.slice(0, 2)} ${dialled.slice(2, 7)} ${dialled.slice(7)}`
  : `+${dialled}`;

/**
 * A wa.me link, opening WhatsApp on the phone and web.whatsapp.com on a
 * desktop. The message is only a draft: WhatsApp shows it in the box for the
 * agent to send, change or discard, so nothing is sent by following the link.
 */
export function whatsappLink(message?: string) {
  const text = message ? `?text=${encodeURIComponent(message)}` : '';
  return `https://wa.me/${dialled}${text}`;
}
