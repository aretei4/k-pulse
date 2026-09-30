package com.kahga.pluse.sentiment.service;

import com.kahga.pluse.accessrequest.entity.AccessRequest;
import com.kahga.pluse.accessrequest.service.AccessRequestService;
import com.kahga.pluse.candidate.entity.Candidate;
import com.kahga.pluse.candidate.service.CandidateService;
import com.kahga.pluse.common.exception.BusinessException;
import com.kahga.pluse.sentiment.dto.RecordSentimentRequest;
import com.kahga.pluse.sentiment.entity.SentimentEntry;
import com.kahga.pluse.sentiment.repository.SentimentEntryRepository;
import com.kahga.pluse.user.entity.User;
import com.kahga.pluse.voter.entity.Voter;
import com.kahga.pluse.voter.service.VoterService;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SentimentService {

    private final SentimentEntryRepository sentimentEntryRepository;
    private final VoterService voterService;
    private final CandidateService candidateService;
    private final AccessRequestService accessRequestService;

    public Optional<SentimentEntry> find(UUID voterId, UUID candidateId) {
        return candidateId == null
                ? sentimentEntryRepository.findFirstByVoterIdOrderByUpdatedAtDesc(voterId)
                : sentimentEntryRepository.findByVoterIdAndCandidateId(voterId, candidateId);
    }

    public long countAll() {
        return sentimentEntryRepository.count();
    }

    /**
     * Records or overwrites the agent's entry for this voter and candidate. The
     * candidate must be the one their grant on this booth was approved for
     * (FR-U7) — it is never a free choice at entry time.
     */
    @Transactional
    public SentimentEntry record(User agent, UUID voterId, RecordSentimentRequest payload) {
        Voter voter = voterService.require(voterId);
        AccessRequest grant = accessRequestService.requireGrantFor(agent.getId(), voter.getBooth().getId());

        if (!grant.getCandidate().getId().equals(payload.candidateId())) {
            throw new BusinessException(
                    "Your access to this booth records sentiment for " + grant.getCandidate().getName());
        }
        Candidate candidate = candidateService.require(payload.candidateId());

        Instant now = Instant.now();
        SentimentEntry entry = sentimentEntryRepository
                .findByVoterIdAndCandidateId(voter.getId(), candidate.getId())
                .orElseGet(() -> SentimentEntry.builder()
                        .id(UUID.randomUUID())
                        .voter(voter)
                        .candidate(candidate)
                        .recordedBy(agent)
                        .recordedAt(now)
                        .build());

        entry.setSentiment(payload.sentiment());
        entry.setConfidence(payload.confidence());
        entry.setResident(payload.resident());
        entry.setWardNo(payload.wardNo() == null ? voter.getWardNo() : payload.wardNo());
        entry.setRecordedBy(agent);
        entry.setUpdatedAt(now);

        if (payload.wardNo() != null && !payload.wardNo().equals(voter.getWardNo())) {
            // Ward is captured on the doorstep, so trust the field over the roll.
            voterService.updateWardNo(voter, payload.wardNo());
        }

        return sentimentEntryRepository.save(entry);
    }
}
