package com.kahga.pluse.housesentiment.service;

import com.kahga.pluse.accessrequest.entity.AccessRequest;
import com.kahga.pluse.accessrequest.service.AccessRequestService;
import com.kahga.pluse.candidate.entity.Candidate;
import com.kahga.pluse.candidate.service.CandidateService;
import com.kahga.pluse.common.exception.BusinessException;
import com.kahga.pluse.common.exception.NotFoundException;
import com.kahga.pluse.housesentiment.dto.HouseInsightsDto;
import com.kahga.pluse.housesentiment.dto.RecordHouseSentimentRequest;
import com.kahga.pluse.housesentiment.entity.HouseSentimentEntry;
import com.kahga.pluse.housesentiment.repository.HouseSentimentEntryRepository;
import com.kahga.pluse.location.entity.Unit;
import com.kahga.pluse.location.service.LocationService;
import com.kahga.pluse.user.entity.User;
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.Locale;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Pre-election, house-level sentiment (FR-U12). It rides on exactly the same
 * approval as named-voter sentiment — a live grant on the booth, for the
 * candidate that grant names — and touches no voter record at any point.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class HouseSentimentService {

    private final HouseSentimentEntryRepository houseSentimentEntryRepository;
    private final AccessRequestService accessRequestService;
    private final CandidateService candidateService;
    private final LocationService locationService;

    /**
     * Every house recorded for this booth and candidate, whoever recorded it, so
     * two agents working the same booth do not tally the same house twice.
     */
    public List<HouseSentimentEntry> list(User agent, UUID boothId, UUID candidateId) {
        requireGrant(agent, boothId, candidateId);
        return houseSentimentEntryRepository.findByBoothAndCandidate(boothId, candidateId);
    }

    /**
     * Records a house, or overwrites the tally already held for that house
     * number in this booth — revisiting a house corrects it rather than adding a
     * second row.
     */
    @Transactional
    public HouseSentimentEntry record(User agent, RecordHouseSentimentRequest payload) {
        AccessRequest grant = requireGrant(agent, payload.boothId(), payload.candidateId());
        Candidate candidate = candidateService.require(payload.candidateId());
        Unit booth = locationService.require(payload.boothId());
        String houseNo = normaliseHouseNo(payload.houseNo());
        checkCounts(payload);

        Instant now = Instant.now();
        HouseSentimentEntry entry = houseSentimentEntryRepository
                .findByHouse(booth.getId(), grant.getCandidate().getId(), houseNo)
                .orElseGet(() -> HouseSentimentEntry.builder()
                        .id(UUID.randomUUID())
                        .recordedAt(now)
                        .build());

        entry.setBooth(booth);
        entry.setCandidate(candidate);
        entry.setHouseNo(houseNo);
        entry.setHouseName(payload.houseName().trim());
        entry.setWardNo(payload.wardNo());
        entry.setHeadcount(payload.headcount());
        entry.setResidentialCount(payload.residentialCount());
        entry.setPositiveCount(payload.positiveCount());
        entry.setNeutralCount(payload.neutralCount());
        entry.setNegativeCount(payload.negativeCount());
        entry.setConfidence(payload.confidence());
        entry.setRecordedBy(agent);
        entry.setUpdatedAt(now);

        return houseSentimentEntryRepository.save(entry);
    }

    /**
     * Totals for the pre-election charts. One booth when the agent picks one,
     * otherwise every booth their live grants cover — the same scoping rule the
     * named-voter insights use.
     */
    public HouseInsightsDto insights(User agent, UUID boothId, UUID candidateId) {
        Set<UUID> scope;
        if (boothId != null) {
            accessRequestService.requireGrantFor(agent.getId(), boothId);
            scope = new LinkedHashSet<>(Set.of(boothId));
        } else {
            scope = accessRequestService.accessibleBoothIds(agent.getId());
        }
        return houseSentimentEntryRepository.insights(scope, candidateId);
    }

    @Transactional
    public void delete(User agent, UUID id) {
        HouseSentimentEntry entry = houseSentimentEntryRepository
                .findById(id)
                .orElseThrow(() -> new NotFoundException("House entry not found"));
        requireGrant(agent, entry.getBooth().getId(), entry.getCandidate().getId());
        houseSentimentEntryRepository.delete(id);
    }

    /** The booth must be open to this agent, and for the candidate they are asking about (FR-U7). */
    private AccessRequest requireGrant(User agent, UUID boothId, UUID candidateId) {
        AccessRequest grant = accessRequestService.requireGrantFor(agent.getId(), boothId);
        if (!grant.getCandidate().getId().equals(candidateId)) {
            throw new BusinessException(
                    "Your access to this booth records sentiment for " + grant.getCandidate().getName());
        }
        return grant;
    }

    /**
     * The three counts are a breakdown of the residents, not independent
     * numbers: only the people who actually live at the house are read for
     * sentiment. A tally that does not add up is rejected rather than stored and
     * quietly skewing any later roll-up.
     */
    private void checkCounts(RecordHouseSentimentRequest payload) {
        if (payload.residentialCount() > payload.headcount()) {
            throw new BusinessException("A house cannot have more residents than people");
        }
        int split = payload.positiveCount() + payload.neutralCount() + payload.negativeCount();
        if (split != payload.residentialCount()) {
            throw new BusinessException("Positive, neutral and negative add up to " + split + ", but the house has "
                    + payload.residentialCount() + " resident(s)");
        }
    }

    /** "4/a" and " 4/A " are the same house, so the stored form decides which rows collide. */
    private String normaliseHouseNo(String houseNo) {
        return houseNo.trim().toUpperCase(Locale.ROOT);
    }
}
