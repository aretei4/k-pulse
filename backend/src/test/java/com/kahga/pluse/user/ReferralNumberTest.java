package com.kahga.pluse.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.kahga.pluse.common.exception.BusinessException;
import com.kahga.pluse.user.service.ReferralNumbers;
import org.junit.jupiter.api.Test;

/**
 * The referral rule, which runs on three screens: the agent's sign-up, the
 * admin's create-agent form and the admin's panel. A plain JVM test — the rule
 * is the whole point and needs no database to assert.
 */
class ReferralNumberTest {

    private static final String OWN = "9861000777";

    @Test
    void nothingGivenIsNothingStored() {
        assertThat(ReferralNumbers.normalise(null, OWN)).isNull();
        assertThat(ReferralNumbers.normalise("", OWN)).isNull();
        assertThat(ReferralNumbers.normalise("   ", OWN)).isNull();
    }

    @Test
    void aTenDigitMobileIsKept() {
        assertThat(ReferralNumbers.normalise("9861000011", OWN)).isEqualTo("9861000011");
        assertThat(ReferralNumbers.normalise("  6861000011 ", OWN)).isEqualTo("6861000011");
    }

    /** Written the way a phone shows it, which is how it will be read off one. */
    @Test
    void spacesDashesAndACountryCodeAreCleanedAway() {
        assertThat(ReferralNumbers.normalise("98610-00011", OWN)).isEqualTo("9861000011");
        assertThat(ReferralNumbers.normalise("+91 98610 00011", OWN)).isEqualTo("9861000011");
        assertThat(ReferralNumbers.normalise("91 9861000011", OWN)).isEqualTo("9861000011");
    }

    /** A ten-digit number that merely begins 91 is not a country code. */
    @Test
    void aNumberBeginningNineOneIsLeftAlone() {
        assertThat(ReferralNumbers.normalise("9199887766", OWN)).isEqualTo("9199887766");
    }

    @Test
    void anythingThatIsNotAMobileNumberIsRefused() {
        for (String bad : new String[] {"98610", "98610000111", "0861000011", "5861000011", "ward 7", "abc1234567"}) {
            assertThatThrownBy(() -> ReferralNumbers.normalise(bad, OWN))
                    .as("%s should be refused", bad)
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("10-digit mobile number");
        }
    }

    @Test
    void anAgentCannotBeTheirOwnReferral() {
        assertThatThrownBy(() -> ReferralNumbers.normalise(OWN, OWN))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("own referral");
    }
}
