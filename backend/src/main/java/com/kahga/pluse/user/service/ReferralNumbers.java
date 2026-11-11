package com.kahga.pluse.user.service;

import com.kahga.pluse.common.exception.BusinessException;
import java.util.regex.Pattern;

/**
 * The one rule for a referral number, wherever it is given: the agent's own
 * sign-up form, an admin creating the account, or an admin correcting it later.
 *
 * <p>It is the mobile number of whoever sent the agent, so it is held to the
 * same shape as every other mobile number in K-Pulse rather than kept as free
 * text. A number that is really a name or a code would be a note nobody can
 * ring, which is the one thing this field is for.
 *
 * <p>It is deliberately <em>not</em> checked against the accounts we hold: the
 * person who sent an agent is often a party worker with no K-Pulse login, and
 * refusing their number would mean the agent types a wrong one or nothing.
 */
public final class ReferralNumbers {

    private static final Pattern MOBILE = Pattern.compile("[6-9]\\d{9}");

    private static final Pattern SEPARATORS = Pattern.compile("[\\s\\-()]");

    private ReferralNumbers() {}

    /**
     * Cleans what was typed and refuses anything that is not a mobile number.
     *
     * @param raw what the form sent; blank or null means "none given"
     * @param ownPhone the agent's own number, which cannot be their referral
     * @return the ten digits to store, or null
     */
    public static String normalise(String raw, String ownPhone) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String digits = SEPARATORS.matcher(raw.trim()).replaceAll("");
        // "+91 98610 00011" and "9198610 00011" are the same number written the
        // way a phone shows it. Only stripped when what is left is a whole number.
        if (digits.startsWith("+91") && digits.length() == 13) {
            digits = digits.substring(3);
        } else if (digits.startsWith("91") && digits.length() == 12) {
            digits = digits.substring(2);
        }
        if (!MOBILE.matcher(digits).matches()) {
            throw new BusinessException(
                    "A referral number is the 10-digit mobile number of whoever referred the agent");
        }
        if (ownPhone != null && digits.equals(ownPhone.trim())) {
            throw new BusinessException("An agent cannot be their own referral");
        }
        return digits;
    }
}
