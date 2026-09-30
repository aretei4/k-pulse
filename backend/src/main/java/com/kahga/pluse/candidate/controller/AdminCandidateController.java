package com.kahga.pluse.candidate.controller;

import com.kahga.pluse.candidate.dto.CandidateDto;
import com.kahga.pluse.candidate.dto.CandidateRequest;
import com.kahga.pluse.candidate.service.CandidateService;
import com.kahga.pluse.common.response.ApiResponse;
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
 * Managing candidates, admin only. Reading them stays on /api/candidates, which
 * agents need for their own screens.
 */
@RestController
@RequestMapping("/api/admin/candidates")
@RequiredArgsConstructor
public class AdminCandidateController {

    private final CandidateService candidateService;

    @PostMapping
    public ApiResponse<CandidateDto> create(@Valid @RequestBody CandidateRequest request) {
        return ApiResponse.ok(CandidateDto.from(candidateService.create(request)), "Candidate added");
    }

    @PutMapping("/{id}")
    public ApiResponse<CandidateDto> update(@PathVariable UUID id, @Valid @RequestBody CandidateRequest request) {
        return ApiResponse.ok(CandidateDto.from(candidateService.update(id, request)), "Candidate updated");
    }

    @DeleteMapping("/{id}")
    public ApiResponse<CandidateDto> delete(@PathVariable UUID id) {
        candidateService.delete(id);
        return ApiResponse.ok(null, "Candidate deleted");
    }
}
