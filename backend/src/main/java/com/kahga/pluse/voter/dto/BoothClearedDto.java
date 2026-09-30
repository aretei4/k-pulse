package com.kahga.pluse.voter.dto;

import java.util.UUID;

/**
 * What clearing a booth removed. Sentiment entries go with the voters they were
 * recorded against, so the count is reported back rather than left implied.
 */
public record BoothClearedDto(UUID boothId, String boothName, int voters, long sentimentEntries) {}
