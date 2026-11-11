package com.kahga.pluse.voter.entity;

/**
 * The social category an agent records for a voter.
 *
 * <p>Deliberately the reservation categories rather than individual caste names:
 * a list of names differs by district and would need a master table of its own,
 * while these five are the ones the field actually asks for and are what any
 * break-up of a booth would group by. {@code null} is its own answer -- not
 * recorded yet -- so there is no UNKNOWN here.
 */
public enum Caste {
    GENERAL,
    SEBC,
    SC,
    ST,
    MINORITY,
    OTHER
}
