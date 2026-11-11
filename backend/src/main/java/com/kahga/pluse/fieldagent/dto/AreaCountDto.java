package com.kahga.pluse.fieldagent.dto;

import java.util.UUID;

/** One option in the District, Block or Panchayat dropdown: "Bhadrak (1,415)" (FA-3). */
public record AreaCountDto(UUID unitId, String name, long agents) {}
