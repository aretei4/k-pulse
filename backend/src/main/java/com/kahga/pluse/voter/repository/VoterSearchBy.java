package com.kahga.pluse.voter.repository;

/**
 * What the voter list's search box matches besides the name. Admins work from
 * the roll, where the EPIC no. is the identifier; agents work on the doorstep,
 * where the house number is what they have in front of them.
 */
public enum VoterSearchBy {
    EPIC_NO,
    HOUSE_NO
}
