package com.kahga.pluse.voter.service;

import com.kahga.pluse.common.response.PageResult;
import com.kahga.pluse.common.exception.BusinessException;
import com.kahga.pluse.common.exception.NotFoundException;
import com.kahga.pluse.location.entity.Unit;
import com.kahga.pluse.location.entity.UnitLevel;
import com.kahga.pluse.location.service.LocationService;
import com.kahga.pluse.sentiment.entity.SentimentEntry;
import com.kahga.pluse.sentiment.entity.SentimentValue;
import com.kahga.pluse.sentiment.repository.SentimentEntryRepository;
import com.kahga.pluse.voter.dto.VoterDto;
import com.kahga.pluse.voter.dto.BoothClearedDto;
import com.kahga.pluse.voter.dto.VoterRequest;
import com.kahga.pluse.voter.entity.Gender;
import com.kahga.pluse.voter.entity.Voter;
import com.kahga.pluse.voter.repository.VoterRepository;
import com.kahga.pluse.voter.repository.VoterSearchBy;
import java.time.Instant;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The only class that writes to the voter table. Agent-proposed changes come
 * through VoterChangeRequestService, which calls in here once an admin approves.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class VoterService {

    private final VoterRepository voterRepository;
    private final SentimentEntryRepository sentimentEntryRepository;
    private final LocationService locationService;

    public Voter require(UUID id) {
        return voterRepository.findById(id).orElseThrow(() -> new NotFoundException("Voter not found"));
    }

    public PageResult<Voter> search(
            Collection<UUID> boothIds,
            UUID boothId,
            String search,
            VoterSearchBy searchBy,
            SentimentValue sentiment,
            boolean notRecorded,
            UUID candidateId,
            int page,
            int size) {

        return voterRepository.search(
                boothIds, boothId, search, searchBy, sentiment, notRecorded, candidateId,
                Math.max(0, page), Math.min(Math.max(1, size), 500));
    }

    /** Attaches each voter's recorded sentiment in one extra query, not N. */
    public List<VoterDto> toDtos(List<Voter> voters, UUID candidateId) {
        if (voters.isEmpty()) {
            return List.of();
        }
        List<UUID> ids = voters.stream().map(Voter::getId).toList();
        Map<UUID, SentimentEntry> byVoter = new HashMap<>();
        for (SentimentEntry entry : sentimentEntryRepository.findByVoterIdIn(ids)) {
            if (candidateId != null && !entry.getCandidate().getId().equals(candidateId)) {
                continue;
            }
            byVoter.merge(
                    entry.getVoter().getId(),
                    entry,
                    (existing, next) -> next.getUpdatedAt().isAfter(existing.getUpdatedAt()) ? next : existing);
        }
        return voters.stream().map(v -> VoterDto.from(v, byVoter.get(v.getId()))).toList();
    }

    public VoterDto toDto(Voter voter, UUID candidateId) {
        return toDtos(List.of(voter), candidateId).get(0);
    }

    @Transactional
    public Voter create(VoterRequest request, UUID boothId) {
        Unit booth = requireBooth(boothId);
        String epicNo = normaliseEpic(request.epicNo());
        if (voterRepository.existsByEpicNoIgnoreCase(epicNo)) {
            throw new BusinessException("A voter with EPIC no. " + epicNo + " already exists");
        }
        Voter voter = Voter.builder()
                .id(UUID.randomUUID())
                .epicNo(epicNo)
                .name(request.name().trim())
                .relation(request.relation())
                .houseNo(request.houseNo())
                .age(request.age())
                .gender(request.gender() == null ? Gender.OTHER : request.gender())
                .booth(booth)
                .wardNo(request.wardNo())
                .createdAt(Instant.now())
                .build();
        return voterRepository.save(voter);
    }

    @Transactional
    public Voter update(UUID id, VoterRequest request) {
        Voter voter = require(id);
        if (request.epicNo() != null && !request.epicNo().isBlank()) {
            String epicNo = request.epicNo().trim();
            voterRepository
                    .findByEpicNoIgnoreCase(epicNo)
                    .filter(other -> !other.getId().equals(id))
                    .ifPresent(other -> {
                        throw new BusinessException("EPIC no. " + epicNo + " belongs to another voter");
                    });
            voter.setEpicNo(epicNo);
        }
        if (request.name() != null) {
            voter.setName(request.name().trim());
        }
        if (request.relation() != null) {
            voter.setRelation(request.relation());
        }
        if (request.houseNo() != null) {
            voter.setHouseNo(request.houseNo());
        }
        if (request.age() > 0) {
            voter.setAge(request.age());
        }
        if (request.gender() != null) {
            voter.setGender(request.gender());
        }
        if (request.wardNo() != null) {
            voter.setWardNo(request.wardNo());
        }
        if (request.boothId() != null) {
            voter.setBooth(requireBooth(request.boothId()));
        }
        return voterRepository.save(voter);
    }

    @Transactional
    public void delete(UUID id) {
        Voter voter = require(id);
        voterRepository.delete(voter);
    }

    /**
     * Sentiment entry captures the ward on the doorstep, and that overrides the
     * roll. It has to go through here: setting it on a loaded Voter persists nothing.
     */
    @Transactional
    public void updateWardNo(Voter voter, Integer wardNo) {
        voter.setWardNo(wardNo);
        voterRepository.save(voter);
    }

    /**
     * Empties one booth's roll. The schema cascades each voter's sentiment
     * entries, so recorded work goes with them; the count is returned so the
     * caller can say exactly what was destroyed.
     */
    @Transactional
    public BoothClearedDto deleteAllInBooth(UUID boothId) {
        Unit booth = locationService.require(boothId);
        if (booth.getLevel() == UnitLevel.BOOTH) {
            long entries = sentimentEntryRepository.countByBoothId(boothId);
            int removed = voterRepository.deleteByBoothId(boothId);
            return new BoothClearedDto(booth.getId(), booth.getName(), removed, entries);
        }
        throw new BusinessException(
                "Pick a booth: " + booth.getName() + " is a " + booth.getLevel().name() + ", not a booth");
    }

    private Unit requireBooth(UUID boothId) {
        if (boothId == null) {
            throw new BusinessException("Pick the booth this voter belongs to");
        }
        Unit unit = locationService.require(boothId);
        if (unit.getLevel() != UnitLevel.BOOTH) {
            throw new BusinessException("Voters attach to a booth, not a " + unit.getLevel().name().toLowerCase());
        }
        return unit;
    }

    private String normaliseEpic(String epicNo) {
        if (epicNo == null || epicNo.isBlank()) {
            // Rolls occasionally arrive without an EPIC number; keep the row addressable.
            return "TMP" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        }
        return epicNo.trim().toUpperCase();
    }
}
