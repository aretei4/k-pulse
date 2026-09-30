package com.kahga.pluse.user.entity;

import com.kahga.pluse.location.entity.Unit;
import com.kahga.pluse.location.entity.UnitLevel;
import java.util.Set;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * What one ADMIN account can see: a unit, plus everything under it (FR-A9).
 *
 * <p>A booth is deliberately not a valid scope — that granularity belongs to the
 * field agent's access request. A SUPER_ADMIN has no scope at all; full access
 * comes from the role, so there is nothing here to resolve.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AccessScope {

    public static final Set<UnitLevel> ALLOWED = Set.of(UnitLevel.DISTRICT, UnitLevel.BLOCK, UnitLevel.PANCHAYAT);

    private UnitLevel level;

    /** Carries only its id when read back with the user; callers resolve the rest. */
    private Unit unit;
}
