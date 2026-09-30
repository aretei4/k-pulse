package com.kahga.pluse.location.controller;

import com.kahga.pluse.common.response.ApiResponse;
import com.kahga.pluse.location.dto.UnitDto;
import com.kahga.pluse.location.entity.UnitLevel;
import com.kahga.pluse.location.service.LocationService;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/units")
@RequiredArgsConstructor
public class LocationController {

    private final LocationService locationService;

    @GetMapping
    public ApiResponse<List<UnitDto>> list(
            @RequestParam(required = false) UnitLevel level, @RequestParam(required = false) UUID parentId) {
        return ApiResponse.ok(locationService.find(level, parentId).stream().map(UnitDto::from).toList());
    }
}
