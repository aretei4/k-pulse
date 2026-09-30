package com.kahga.pluse.voter.entity;

import com.kahga.pluse.location.entity.Unit;
import java.time.Instant;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** A row of {@code voter}. */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Voter {

    private UUID id;

    private String epicNo;

    private String name;

    /** S/O · W/O · D/O plus the head's name, exactly as printed on the roll. */
    private String relation;

    private String houseNo;

    private int age;

    private Gender gender;

    /** Always loaded with the voter, by join: every voter row we render shows its booth. */
    private Unit booth;

    private Integer wardNo;

    private Instant createdAt;
}
