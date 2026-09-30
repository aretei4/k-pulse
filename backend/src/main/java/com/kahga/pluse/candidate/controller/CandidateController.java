package com.kahga.pluse.candidate.controller;

import com.kahga.pluse.candidate.dto.CandidateDto;
import com.kahga.pluse.candidate.service.CandidateService;
import com.kahga.pluse.common.response.ApiResponse;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/candidates")
@RequiredArgsConstructor
public class CandidateController {

    private final CandidateService candidateService;

    @GetMapping
    public ApiResponse<List<CandidateDto>> list() {
        return ApiResponse.ok(candidateService.findAll().stream().map(CandidateDto::from).toList());
    }
}
