package com.kahga.pluse.voter.dto;

import com.kahga.pluse.sentiment.entity.ConfidenceLevel;
import com.kahga.pluse.sentiment.entity.SentimentEntry;
import com.kahga.pluse.sentiment.entity.SentimentValue;
import com.kahga.pluse.voter.entity.Gender;
import com.kahga.pluse.voter.entity.Voter;
import java.util.UUID;

public record VoterDto(
        UUID id,
        String epicNo,
        String name,
        String relation,
        String houseNo,
        int age,
        Gender gender,
        UUID boothId,
        String boothName,
        Integer wardNo,
        SentimentValue sentiment,
        ConfidenceLevel confidence) {

    public static VoterDto from(Voter voter, SentimentEntry entry) {
        return new VoterDto(
                voter.getId(),
                voter.getEpicNo(),
                voter.getName(),
                voter.getRelation(),
                voter.getHouseNo(),
                voter.getAge(),
                voter.getGender(),
                voter.getBooth().getId(),
                voter.getBooth().getName(),
                voter.getWardNo(),
                entry == null ? null : entry.getSentiment(),
                entry == null ? null : entry.getConfidence());
    }
}
