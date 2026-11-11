package com.kahga.pluse.fieldagent.dto;

import java.math.BigDecimal;

/** What a bulk action did, for the message the page shows afterwards (FA-14). */
public record BulkPaymentResult(int agents, boolean paid, BigDecimal total) {}
