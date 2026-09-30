package com.kahga.pluse.candidate.entity;

import com.kahga.pluse.location.entity.Unit;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** A row of {@code candidate}. */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Candidate {

    private UUID id;

    private String name;

    private String party;

    /**
     * The panchayat this candidate contests in, or null for one standing across
     * the whole constituency. Only the id is populated when a candidate is read
     * as part of another row; the candidate list joins the unit for its name.
     */
    private Unit unit;
}
