package com.kahga.pluse.location.controller;

import com.kahga.pluse.common.response.ApiResponse;
import com.kahga.pluse.location.dto.UnitDto;
import com.kahga.pluse.location.dto.UnitRequest;
import com.kahga.pluse.location.service.LocationService;
import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Editing the location tree, admin only. Reading it stays on /api/units, which
 * agents need too; everything here is under /api/admin and role-checked.
 */
@RestController
@RequestMapping("/api/admin/units")
@RequiredArgsConstructor
public class AdminLocationController {

    private final LocationService locationService;

    @PostMapping
    public ApiResponse<UnitDto> create(@Valid @RequestBody UnitRequest request) {
        return ApiResponse.ok(UnitDto.from(locationService.create(request)), "Location added");
    }

    @PutMapping("/{id}")
    public ApiResponse<UnitDto> update(@PathVariable UUID id, @Valid @RequestBody UnitRequest request) {
        return ApiResponse.ok(UnitDto.from(locationService.update(id, request)), "Location updated");
    }

    @DeleteMapping("/{id}")
    public ApiResponse<UnitDto> delete(@PathVariable UUID id) {
        locationService.delete(id);
        return ApiResponse.ok(null, "Location deleted");
    }
}
