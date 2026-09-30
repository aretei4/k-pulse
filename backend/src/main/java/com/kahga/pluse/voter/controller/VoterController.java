package com.kahga.pluse.voter.controller;

import com.kahga.pluse.common.response.PageResult;
import com.kahga.pluse.common.response.ApiResponse;
import com.kahga.pluse.common.response.PageResponse;
import com.kahga.pluse.security.CurrentUserService;
import com.kahga.pluse.sentiment.entity.SentimentValue;
import com.kahga.pluse.voter.dto.BoothClearedDto;
import com.kahga.pluse.voter.dto.VoterDto;
import com.kahga.pluse.voter.dto.VoterRequest;
import com.kahga.pluse.voter.dto.VoterUploadDto;
import com.kahga.pluse.voter.entity.Voter;
import com.kahga.pluse.voter.repository.VoterUploadRepository;
import com.kahga.pluse.voter.service.VoterImportService;
import com.kahga.pluse.user.service.AdminScopeService;
import com.kahga.pluse.voter.repository.VoterSearchBy;
import com.kahga.pluse.voter.service.VoterService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/** Admin-side roll management: browse, hand-edit, and bulk import. */
@RestController
@RequestMapping("/api/admin/voters")
@RequiredArgsConstructor
public class VoterController {

    private final VoterService voterService;
    private final AdminScopeService adminScopeService;
    private final VoterImportService voterImportService;
    private final VoterUploadRepository voterUploadRepository;
    private final CurrentUserService currentUserService;

    @GetMapping
    public ApiResponse<PageResponse<VoterDto>> list(
            @RequestParam(required = false) UUID boothId,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String sentiment,
            @RequestParam(required = false) UUID candidateId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "25") int size) {

        boolean notRecorded = "NOT_RECORDED".equalsIgnoreCase(sentiment);
        SentimentValue value = notRecorded || sentiment == null || sentiment.isBlank() || "ALL".equalsIgnoreCase(sentiment)
                ? null
                : SentimentValue.valueOf(sentiment);

        PageResult<Voter> voters =
                voterService.search(
                        adminScopeService.boothIds(),
                        boothId,
                        search,
                        VoterSearchBy.EPIC_NO,
                        value,
                        notRecorded,
                        candidateId,
                        page,
                        size);
        List<VoterDto> content = voterService.toDtos(voters.content(), candidateId);
        return ApiResponse.ok(new PageResponse<>(
                content, voters.page(), voters.size(), voters.totalElements(), Math.max(1, voters.totalPages())));
    }

    @PostMapping
    public ApiResponse<VoterDto> create(@Valid @RequestBody VoterRequest request) {
        return ApiResponse.ok(voterService.toDto(voterService.create(request, request.boothId()), null));
    }

    @PutMapping("/{id}")
    public ApiResponse<VoterDto> update(@PathVariable UUID id, @Valid @RequestBody VoterRequest request) {
        return ApiResponse.ok(voterService.toDto(voterService.update(id, request), null));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable UUID id) {
        voterService.delete(id);
        return ApiResponse.ok(null);
    }

    /**
     * Deletes every voter in one booth. A literal path segment, so it is matched
     * ahead of DELETE /{id}.
     */
    @DeleteMapping("/booth/{boothId}")
    public ApiResponse<BoothClearedDto> clearBooth(@PathVariable UUID boothId) {
        BoothClearedDto cleared = voterService.deleteAllInBooth(boothId);
        return ApiResponse.ok(
                cleared, cleared.voters() + " voter(s) deleted from " + cleared.boothName());
    }

    @GetMapping("/uploads")
    public ApiResponse<List<VoterUploadDto>> uploads() {
        return ApiResponse.ok(
                voterUploadRepository.findAllByOrderByUploadedAtDesc().stream().map(VoterUploadDto::from).toList());
    }

    @PostMapping("/import")
    public ApiResponse<VoterUploadDto> importRoll(
            @RequestPart("file") MultipartFile file, @RequestPart("unitId") String unitId) {
        var upload = voterImportService.importWorkbook(file, UUID.fromString(unitId), currentUserService.user());
        return ApiResponse.ok(VoterUploadDto.from(upload), upload.getRowCount() + " voters imported");
    }
}
