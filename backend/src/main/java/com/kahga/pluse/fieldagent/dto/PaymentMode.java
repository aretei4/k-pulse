package com.kahga.pluse.fieldagent.dto;

/** How an agent was paid. Recorded only — K-Pulse moves no money (FR-A20 §9). */
public enum PaymentMode {
    UPI,
    BANK,
    CASH
}
