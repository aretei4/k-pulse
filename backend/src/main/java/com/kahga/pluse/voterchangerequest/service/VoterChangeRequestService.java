package com.kahga.pluse.voterchangerequest.service;

import com.kahga.pluse.accessrequest.service.AccessRequestService;
import com.kahga.pluse.common.exception.BusinessException;
import com.kahga.pluse.common.exception.NotFoundException;
import com.kahga.pluse.location.entity.Unit;
import com.kahga.pluse.location.entity.UnitLevel;
import com.kahga.pluse.location.service.LocationService;
import com.kahga.pluse.user.entity.User;
import com.kahga.pluse.voter.dto.VoterRequest;
import com.kahga.pluse.voter.entity.Voter;
import com.kahga.pluse.user.service.AdminScopeService;
import com.kahga.pluse.voter.service.VoterService;
import com.kahga.pluse.voterchangerequest.dto.ProposeVoterChangeRequest;
import com.kahga.pluse.voterchangerequest.dto.VoterChangePayload;
import com.kahga.pluse.voterchangerequest.entity.VoterChangeRequest;
import com.kahga.pluse.voterchangerequest.entity.VoterChangeStatus;
import com.kahga.pluse.voterchangerequest.entity.VoterChangeType;
import com.kahga.pluse.voterchangerequest.repository.VoterChangeRequestRepository;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Holds agent proposals until an admin decides. This class never writes to the
 * voter table itself — approval delegates to VoterService, so there stays
 * exactly one place that mutates the roll.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class VoterChangeRequestService {

    private final VoterChangeRequestRepository repository;
    private final VoterService voterService;
    private final LocationService locationService;
    private final AccessRequestService accessRequestService;
    private final AdminScopeService adminScopeService;

    /** FR-A9: a scoped admin only reviews proposals from booths inside their area. */
    public List<VoterChangeRequest> list(VoterChangeStatus status) {
        List<VoterChangeRequest> all = status == null
                ? repository.findAllByOrderByProposedAtDesc()
                : repository.findByStatusOrderByProposedAtDesc(status);
        Set<UUID> allowed = adminScopeService.boothIds();
        return allowed == null
                ? all
                : all.stream()
                        .filter(change -> allowed.contains(change.getBooth().getId()))
                        .toList();
    }

    public List<VoterChangeRequest> listForAgent(UUID agentId) {
        return repository.findByAgentIdOrderByProposedAtDesc(agentId);
    }

    public long countPending() {
        return repository.countByStatus(VoterChangeStatus.PENDING);
    }

    @Transactional
    public VoterChangeRequest propose(User agent, ProposeVoterChangeRequest payload) {
        Unit booth = locationService.require(payload.boothId());
        if (booth.getLevel() != UnitLevel.BOOTH) {
            throw new BusinessException("Voter changes are proposed against a booth");
        }
        accessRequestService.requireGrantFor(agent.getId(), booth.getId());

        Voter target = null;
        if (payload.type() != VoterChangeType.ADD) {
            if (payload.voterId() == null) {
                throw new BusinessException("Pick the voter this change applies to");
            }
            target = voterService.require(payload.voterId());
            if (!target.getBooth().getId().equals(booth.getId())) {
                throw new BusinessException("That voter is not in this booth");
            }
        }

        VoterChangePayload fields = payload.payload();
        VoterChangeRequest change = VoterChangeRequest.builder()
                .id(UUID.randomUUID())
                .changeType(payload.type())
                .status(VoterChangeStatus.PENDING)
                .agent(agent)
                .voter(target)
                .voterName(target == null ? fields.name() : target.getName())
                .booth(booth)
                .epicNo(fields.epicNo())
                .name(fields.name())
                .relation(fields.relation())
                .houseNo(fields.houseNo())
                .age(fields.age())
                .gender(fields.gender())
                .wardNo(fields.wardNo())
                .reason(fields.reason())
                .proposedAt(Instant.now())
                .build();
        return repository.save(change);
    }

    @Transactional
    public VoterChangeRequest approve(UUID id, String note) {
        VoterChangeRequest change = require(id);
        requirePending(change);

        switch (change.getChangeType()) {
            case ADD -> voterService.create(toVoterRequest(change), change.getBooth().getId());
            case EDIT -> {
                if (change.getVoter() == null) {
                    throw new BusinessException("The voter this edit targets no longer exists");
                }
                voterService.update(change.getVoter().getId(), toVoterRequest(change));
            }
            case DELETE -> {
                if (change.getVoter() == null) {
                    throw new BusinessException("The voter this deletion targets no longer exists");
                }
                UUID voterId = change.getVoter().getId();
                // Drop the FK first: the row is about to disappear.
                change.setVoter(null);
                repository.save(change);
                voterService.delete(voterId);
            }
        }

        change.setStatus(VoterChangeStatus.APPROVED);
        change.setDecidedAt(Instant.now());
        change.setReviewerNote(note);
        return repository.save(change);
    }

    @Transactional
    public VoterChangeRequest reject(UUID id, String note) {
        VoterChangeRequest change = require(id);
        requirePending(change);
        change.setStatus(VoterChangeStatus.REJECTED);
        change.setDecidedAt(Instant.now());
        change.setReviewerNote(note);
        return repository.save(change);
    }

    public VoterChangeRequest require(UUID id) {
        return repository.findById(id).orElseThrow(() -> new NotFoundException("Change request not found"));
    }

    private void requirePending(VoterChangeRequest change) {
        if (change.getStatus() != VoterChangeStatus.PENDING) {
            throw new BusinessException("That proposal has already been decided");
        }
    }

    private VoterRequest toVoterRequest(VoterChangeRequest change) {
        return new VoterRequest(
                change.getBooth().getId(),
                change.getEpicNo(),
                change.getName() == null ? change.getVoterName() : change.getName(),
                change.getRelation(),
                change.getHouseNo(),
                change.getAge() == null ? 0 : change.getAge(),
                change.getGender(),
                change.getWardNo());
    }
}
