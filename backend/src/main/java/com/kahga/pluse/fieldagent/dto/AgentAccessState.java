package com.kahga.pluse.fieldagent.dto;

/**
 * What the Access column says about an agent (FA-1, FA-5).
 *
 * <p>Not the same thing as {@code AccessRequestStatus}: that describes one
 * request, while this describes the agent. An approved grant whose expiry has
 * passed still reads APPROVED in its own row but leaves the agent EXPIRED, and
 * an agent who never asked for anything is NONE rather than missing.
 */
public enum AgentAccessState {
    APPROVED,
    PENDING,
    EXPIRED,
    NONE
}
