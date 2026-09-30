package com.kahga.pluse.voter.controller;

import com.kahga.pluse.common.response.PageResult;
import com.kahga.pluse.accessrequest.entity.AccessRequest;
import com.kahga.pluse.accessrequest.service.AccessRequestService;
import com.kahga.pluse.common.response.ApiResponse;
import com.kahga.pluse.common.response.PageResponse;
import com.kahga.pluse.security.CurrentUserService;
import com.kahga.pluse.sentiment.dto.SentimentEntryDto;
import com.kahga.pluse.sentiment.entity.SentimentValue;
import com.kahga.pluse.sentiment.service.SentimentService;
import com.kahga.pluse.user.entity.User;
import com.kahga.pluse.voter.dto.VoterDto;
import com.kahga.pluse.voter.entity.Voter;
import com.kahga.pluse.voter.repository.VoterSearchBy;
import com.kahga.pluse.voter.service.VoterService;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * The agent's window onto the roll. Every read is clamped to the booths their
 * live grants cover — the UI filter is a convenience, this is the boundary.
 */
@RestController
@RequestMapping("/api/agent/voters")
@RequiredArgsConstructor
public class AgentVoterController {

    private final VoterService voterService;
    private final SentimentService sentimentService;
    private final AccessRequestService accessRequestService;
    private final CurrentUserService currentUserService;

    @GetMapping
    public ApiResponse<PageResponse<VoterDto>> list(
            @RequestParam(required = false) UUID boothId,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String sentiment,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {

        User agent = currentUserService.user();
        Set<UUID> allowed = accessRequestService.accessibleBoothIds(agent.getId());

        UUID candidateId = null;
        if (boothId != null) {
            AccessRequest grant = accessRequestService.requireGrantFor(agent.getId(), boothId);
            candidateId = grant.getCandidate().getId();
        }

        boolean notRecorded = "NOT_RECORDED".equalsIgnoreCase(sentiment);
        SentimentValue value = notRecorded || sentiment == null || sentiment.isBlank() || "ALL".equalsIgnoreCase(sentiment)
                ? null
                : SentimentValue.valueOf(sentiment);

        PageResult<Voter> voters =
                voterService.search(
                        allowed, boothId, search, VoterSearchBy.HOUSE_NO, value, notRecorded, candidateId, page, size);
        List<VoterDto> content = voterService.toDtos(voters.content(), candidateId);
        return ApiResponse.ok(new PageResponse<>(
                content, voters.page(), voters.size(), voters.totalElements(), Math.max(1, voters.totalPages())));
    }

    @GetMapping("/{id}")
    public ApiResponse<Map<String, Object>> detail(@PathVariable UUID id) {
        User agent = currentUserService.user();
        Voter voter = voterService.require(id);
        AccessRequest grant = accessRequestService.requireGrantFor(agent.getId(), voter.getBooth().getId());
        UUID candidateId = grant.getCandidate().getId();

        SentimentEntryDto entry = sentimentService
                .find(voter.getId(), candidateId)
                .map(SentimentEntryDto::from)
                .orElse(null);

        // A two-key object rather than a dedicated DTO: the SPA reads { voter, entry }.
        java.util.HashMap<String, Object> body = new java.util.HashMap<>();
        body.put("voter", voterService.toDto(voter, candidateId));
        body.put("entry", entry);
        return ApiResponse.ok(body);
    }
}
