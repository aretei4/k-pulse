package com.kahga.pluse.fieldagent.dto;

import jakarta.validation.constraints.Size;

/**
 * The referral number an admin types on the Field agents panel. Null or blank
 * clears it; anything else is held to the same rule as the sign-up form.
 */
public record UpdateReferralRequest(@Size(max = 20) String referralNo) {}
