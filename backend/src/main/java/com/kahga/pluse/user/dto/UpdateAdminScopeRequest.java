package com.kahga.pluse.user.dto;

import com.kahga.pluse.location.entity.UnitLevel;
import java.util.UUID;

/** Moves an admin to another district, block or panchayat (FR-A13). */
public record UpdateAdminScopeRequest(UnitLevel scopeLevel, UUID scopeUnitId) {}
