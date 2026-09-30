package com.kahga.pluse.candidate.service;

import com.kahga.pluse.accessrequest.repository.AccessRequestRepository;
import com.kahga.pluse.candidate.dto.CandidateRequest;
import com.kahga.pluse.candidate.entity.Candidate;
import com.kahga.pluse.candidate.repository.CandidateRepository;
import com.kahga.pluse.common.exception.BusinessException;
import com.kahga.pluse.common.exception.NotFoundException;
import com.kahga.pluse.location.entity.Unit;
import com.kahga.pluse.location.entity.UnitLevel;
import com.kahga.pluse.location.service.LocationService;
import com.kahga.pluse.sentiment.repository.SentimentEntryRepository;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CandidateService {

    private final CandidateRepository candidateRepository;
    private final LocationService locationService;
    private final AccessRequestRepository accessRequestRepository;
    private final SentimentEntryRepository sentimentEntryRepository;

    public List<Candidate> findAll() {
        return candidateRepository.findAllByOrderByNameAsc();
    }

    public Candidate require(UUID id) {
        return candidateRepository.findById(id).orElseThrow(() -> new NotFoundException("Unknown candidate"));
    }

    @Transactional
    public Candidate create(CandidateRequest request) {
        String name = cleanName(request.name());
        requireNameFree(name, null);
        return candidateRepository.save(Candidate.builder()
                .id(UUID.randomUUID())
                .name(name)
                .party(cleanParty(request.party()))
                .unit(resolvePanchayat(request.unitId()))
                .build());
    }

    @Transactional
    public Candidate update(UUID id, CandidateRequest request) {
        Candidate candidate = require(id);
        String name = cleanName(request.name());
        requireNameFree(name, id);
        candidate.setName(name);
        candidate.setParty(cleanParty(request.party()));
        candidate.setUnit(resolvePanchayat(request.unitId()));
        return candidateRepository.save(candidate);
    }

    /**
     * Only removable while nothing points at it. Sentiment is recorded toward a
     * candidate, so deleting one with entries would erase recorded work.
     */
    @Transactional
    public void delete(UUID id) {
        Candidate candidate = require(id);
        long entries = sentimentEntryRepository.countByCandidateId(id);
        if (entries > 0) {
            throw new BusinessException(
                    entries + " sentiment entr(ies) were recorded toward " + candidate.getName()
                            + ", so it cannot be deleted.",
                    HttpStatus.CONFLICT);
        }
        long grants = accessRequestRepository.countByCandidateId(id);
        if (grants > 0) {
            throw new BusinessException(
                    grants + " access request(s) name " + candidate.getName() + ", so it cannot be deleted.",
                    HttpStatus.CONFLICT);
        }
        candidateRepository.delete(id);
    }

    /**
     * A candidate may be mapped to the panchayat they contest in, or to nothing
     * at all when they stand across the constituency. It is a label on the
     * candidate: it does not restrict which units an agent may request access
     * for, and reporting still rolls up from booths.
     */
    private Unit resolvePanchayat(UUID unitId) {
        if (unitId == null) {
            return null;
        }
        Unit unit = locationService.require(unitId);
        if (unit.getLevel() != UnitLevel.PANCHAYAT) {
            throw new BusinessException(
                    "A candidate maps to a panchayat, and " + unit.getName() + " is a " + unit.getLevel().name());
        }
        return unit;
    }

    private String cleanName(String name) {
        String clean = name == null ? "" : name.trim();
        if (clean.isEmpty()) {
            throw new BusinessException("Enter the candidate's name");
        }
        return clean;
    }

    private String cleanParty(String party) {
        if (party == null) {
            return null;
        }
        String clean = party.trim();
        return clean.isEmpty() ? null : clean;
    }

    private void requireNameFree(String name, UUID ignoreId) {
        boolean taken = candidateRepository.findAllByOrderByNameAsc().stream()
                .filter(c -> ignoreId == null || !c.getId().equals(ignoreId))
                .anyMatch(c -> c.getName().equalsIgnoreCase(name));
        if (taken) {
            throw new BusinessException("There is already a candidate called " + name, HttpStatus.CONFLICT);
        }
    }
}
