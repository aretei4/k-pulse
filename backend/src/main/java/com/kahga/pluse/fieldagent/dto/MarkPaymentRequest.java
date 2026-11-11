package com.kahga.pluse.fieldagent.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Marking one agent paid or not paid (FA-11, FA-12).
 *
 * <p>Everything but {@code paid} is ignored when marking not paid: that case
 * clears the payment rather than describing one.
 */
public record MarkPaymentRequest(
        boolean paid,
        @DecimalMin("0.0") @Digits(integer = 8, fraction = 2) BigDecimal amount,
        LocalDate paidOn,
        PaymentMode mode,
        @Size(max = 120) String reference) {}
