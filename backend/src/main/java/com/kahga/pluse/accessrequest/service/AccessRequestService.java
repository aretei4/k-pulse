package com.kahga.pluse.accessrequest.service;

import com.kahga.pluse.accessrequest.dto.AgentBoothDto;
import com.kahga.pluse.accessrequest.dto.CreateAccessRequestDto;
import com.kahga.pluse.accessrequest.entity.AccessRequest;
import com.kahga.pluse.accessrequest.entity.AccessRequestStatus;
import com.kahga.pluse.accessrequest.repository.AccessRequestRepository;
import com.kahga.pluse.candidate.entity.Candidate;
import com.kahga.pluse.candidate.service.CandidateService;
import com.kahga.pluse.common.exception.BusinessException;
import com.kahga.pluse.common.exception.NotFoundException;
import com.kahga.pluse.config.KPulseProperties;
import com.kahga.pluse.location.entity.Unit;
import com.kahga.pluse.location.service.LocationService;
import com.kahga.pluse.user.entity.User;
import com.kahga.pluse.user.service.AdminScopeService;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AccessRequestService {

    private final AccessRequestRepository accessRequestRepository;
    private final AdminScopeService adminScopeService;
    private final LocationService locationService;
    private final CandidateService candidateService;
    private final KPulseProperties properties;

    /** FR-A9: a scoped admin only reviews requests for units inside their own area. */
    public List<AccessRequest> list(AccessRequestStatus status) {
        List<AccessRequest> all = status == null
                ? accessRequestRepository.findAllByOrderByRequestedAtDesc()
                : accessRequestRepository.findByStatusOrderByRequestedAtDesc(status);
        Set<UUID> allowed = adminScopeService.unitIds();
        return allowed == null
                ? all
                : all.stream()
                        .filter(request -> allowed.contains(request.getUnit().getId()))
                        .toList();
    }

    public List<AccessRequest> listForAgent(UUID agentId) {
        return accessRequestRepository.findByAgentIdOrderByRequestedAtDesc(agentId);
    }

    public long countPending() {
        return accessRequestRepository.countByStatus(AccessRequestStatus.PENDING);
    }

    @Transactional
    public AccessRequest create(User agent, CreateAccessRequestDto payload) {
        Unit unit = locationService.require(payload.unitId());
        Candidate candidate = candidateService.require(payload.candidateId());

        if (accessRequestRepository.existsByAgentIdAndUnitIdAndCandidateIdAndStatus(
                agent.getId(), unit.getId(), candidate.getId(), AccessRequestStatus.PENDING)) {
            throw new BusinessException(
                    "You already have a pending request for that unit and candidate", HttpStatus.CONFLICT);
        }

        AccessRequest request = AccessRequest.builder()
                .id(UUID.randomUUID())
                .agent(agent)
                .unit(unit)
                .candidate(candidate)
                .status(AccessRequestStatus.PENDING)
                .requestedAt(Instant.now())
                .build();
        return accessRequestRepository.save(request);
    }

    @Transactional
    public AccessRequest approve(UUID id, Integer months, String note) {
        AccessRequest request = require(id);
        if (request.getStatus() != AccessRequestStatus.PENDING) {
            throw new BusinessException("That request has already been decided");
        }
        int duration = months == null ? properties.getAccess().getDefaultExpiryMonths() : months;
        request.setStatus(AccessRequestStatus.APPROVED);
        request.setDecidedAt(Instant.now());
        request.setExpiresAt(Instant.now().atZone(ZoneOffset.UTC).plusMonths(duration).toInstant());
        request.setReviewerNote(note);
        return accessRequestRepository.save(request);
    }

    @Transactional
    public AccessRequest reject(UUID id, String note) {
        AccessRequest request = require(id);
        if (request.getStatus() != AccessRequestStatus.PENDING) {
            throw new BusinessException("That request has already been decided");
        }
        request.setStatus(AccessRequestStatus.REJECTED);
        request.setDecidedAt(Instant.now());
        request.setReviewerNote(note);
        return accessRequestRepository.save(request);
    }

    @Transactional
    public AccessRequest revoke(UUID id) {
        AccessRequest request = require(id);
        if (request.getStatus() != AccessRequestStatus.APPROVED) {
            throw new BusinessException("Only an approved grant can be revoked");
        }
        request.setStatus(AccessRequestStatus.REVOKED);
        request.setExpiresAt(Instant.now().truncatedTo(ChronoUnit.SECONDS));
        return accessRequestRepository.save(request);
    }

    public AccessRequest require(UUID id) {
        return accessRequestRepository.findById(id).orElseThrow(() -> new NotFoundException("Access request not found"));
    }

    public List<AccessRequest> liveGrants(UUID agentId) {
        return accessRequestRepository.findByAgentIdAndStatus(agentId, AccessRequestStatus.APPROVED).stream()
                .filter(AccessRequest::isLive)
                .toList();
    }

    /** Every booth the agent may open, one row per booth+candidate pairing. */
    public List<AgentBoothDto> accessibleBooths(UUID agentId) {
        List<AgentBoothDto> booths = new ArrayList<>();
        for (AccessRequest grant : liveGrants(agentId)) {
            for (UUID boothId : locationService.boothIdsUnder(grant.getUnit().getId())) {
                Unit booth = locationService.require(boothId);
                booths.add(new AgentBoothDto(
                        booth.getId(),
                        booth.getName(),
                        booth.getPath(),
                        grant.getCandidate().getId(),
                        grant.getCandidate().getName(),
                        grant.getId(),
                        grant.getExpiresAt()));
            }
        }
        return booths;
    }

    public Set<UUID> accessibleBoothIds(UUID agentId) {
        Set<UUID> ids = new LinkedHashSet<>();
        liveGrants(agentId).forEach(grant -> ids.addAll(locationService.boothIdsUnder(grant.getUnit().getId())));
        return ids;
    }

    /** Throws unless the agent currently holds a grant covering this booth. */
    public AccessRequest requireGrantFor(UUID agentId, UUID boothId) {
        return liveGrants(agentId).stream()
                .filter(grant -> locationService.boothIdsUnder(grant.getUnit().getId()).contains(boothId))
                .findFirst()
                .orElseThrow(() -> new BusinessException("You do not have access to that booth", HttpStatus.FORBIDDEN));
    }
}
