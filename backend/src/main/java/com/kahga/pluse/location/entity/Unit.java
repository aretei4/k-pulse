package com.kahga.pluse.location.entity;

import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * District > Block > Panchayat > Booth, modelled as one self-referencing table
 * rather than four. Access requests, voter rolls and report rollups all target
 * "a unit at some level", so a single table keeps that one code path instead of
 * four near-identical ones.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Unit {

    private UUID id;

    private UnitLevel level;

    private String name;

    /** Readable trail, e.g. "Bhadrak > Tihidi > Kansabansa > Booth 12". */
    private String path;

    /**
     * Only the id is populated when read back. Callers need the parent's id,
     * never a walk up the tree, so the table is not joined to itself.
     */
    private Unit parent;
}
