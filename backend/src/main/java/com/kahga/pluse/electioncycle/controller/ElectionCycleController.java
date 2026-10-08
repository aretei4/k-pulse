package com.kahga.pluse.electioncycle.controller;

import com.kahga.pluse.common.response.ApiResponse;
import com.kahga.pluse.electioncycle.dto.CreateElectionCycleRequest;
import com.kahga.pluse.electioncycle.dto.ElectionCycleDto;
import com.kahga.pluse.electioncycle.service.ElectionCycleService;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/election-cycles")
@RequiredArgsConstructor
public class ElectionCycleController {

    private final ElectionCycleService electionCycleService;

    @GetMapping
    public ApiResponse<List<ElectionCycleDto>> list() {
        return ApiResponse.ok(electionCycleService.list().stream().map(ElectionCycleDto::from).toList());
    }

    @GetMapping("/current")
    public ApiResponse<ElectionCycleDto> current() {
        return ApiResponse.ok(ElectionCycleDto.from(electionCycleService.current()));
    }

    /** Opening the next cycle closes the current one; its data stays. */
    @PostMapping
    public ApiResponse<ElectionCycleDto> open(@Valid @RequestBody CreateElectionCycleRequest request) {
        return ApiResponse.ok(
                ElectionCycleDto.from(electionCycleService.open(request)),
                "New cycle opened — the previous one is closed and its data kept.");
    }
}
