package com.kahga.pluse.location.dto;

import com.kahga.pluse.location.entity.Unit;
import com.kahga.pluse.location.entity.UnitLevel;
import java.util.UUID;

public record UnitDto(UUID id, UnitLevel level, String name, String path, UUID parentId) {

    public static UnitDto from(Unit unit) {
        return new UnitDto(
                unit.getId(),
                unit.getLevel(),
                unit.getName(),
                unit.getPath(),
                unit.getParent() == null ? null : unit.getParent().getId());
    }
}
