package com.kahga.pluse.location.controller;

import com.kahga.pluse.common.response.ApiResponse;
import com.kahga.pluse.location.dto.LocationImportResult;
import com.kahga.pluse.location.service.LocationImportService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * Bulk upload of the location master, in two sheets. Super admin only: this is
 * the table voters, access grants and every recorded sentiment hang off, and
 * replacing it is not an everyday admin act. The rule is in SecurityConfig.
 *
 * <p>Both calls take {@code preview=true}, which reports what the file would do
 * and changes nothing. The screen runs a preview first and only then commits.
 */
@RestController
@RequestMapping("/api/admin/locations/import")
@RequiredArgsConstructor
public class LocationImportController {

    private final LocationImportService locationImportService;

    /** Sheet one: district, block and gram panchayat. Load this before the booths. */
    @PostMapping("/hierarchy")
    public ApiResponse<LocationImportResult> importHierarchy(
            @RequestPart("file") MultipartFile file,
            @RequestParam(name = "preview", defaultValue = "false") boolean preview) {
        return ApiResponse.ok(locationImportService.importHierarchy(file, preview), message(preview));
    }

    /** Sheet two: the booths of each gram panchayat, with their polling station. */
    @PostMapping("/booths")
    public ApiResponse<LocationImportResult> importBooths(
            @RequestPart("file") MultipartFile file,
            @RequestParam(name = "preview", defaultValue = "false") boolean preview) {
        return ApiResponse.ok(locationImportService.importBooths(file, preview), message(preview));
    }

    private String message(boolean preview) {
        return preview ? "Preview only — nothing has been changed" : "Location master updated";
    }
}
