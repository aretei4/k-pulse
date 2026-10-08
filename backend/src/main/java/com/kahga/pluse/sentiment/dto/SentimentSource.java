package com.kahga.pluse.sentiment.dto;

/**
 * Which dataset feeds the candidate view. House-level counts are people, so
 * combining them with named-voter entries weights each house by its headcount —
 * which is what FR-A15 asks for.
 */
public enum SentimentSource {
    ALL,
    VOTER,
    HOUSE
}
