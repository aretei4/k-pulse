package com.kahga.pluse.voterchangerequest.dto;

import com.kahga.pluse.voter.entity.Gender;

/** The proposed field values; the SPA sends exactly this shape. */
public record VoterChangePayload(
        String epicNo,
        String name,
        String relation,
        String houseNo,
        Integer age,
        Gender gender,
        Integer wardNo,
        String reason) {}
