package com.kahga.pluse.sentiment;

import static org.assertj.core.api.Assertions.assertThat;

import com.kahga.pluse.accessrequest.dto.AgentBoothDto;
import com.kahga.pluse.accessrequest.service.AccessRequestService;
import com.kahga.pluse.sentiment.dto.RecordSentimentRequest;
import com.kahga.pluse.sentiment.entity.ConfidenceLevel;
import com.kahga.pluse.sentiment.entity.SentimentValue;
import com.kahga.pluse.sentiment.service.SentimentService;
import com.kahga.pluse.user.entity.Role;
import com.kahga.pluse.user.entity.User;
import com.kahga.pluse.user.repository.UserRepository;
import com.kahga.pluse.voter.entity.Caste;
import com.kahga.pluse.voter.entity.Voter;
import com.kahga.pluse.voter.repository.VoterRepository;
import com.kahga.pluse.voter.repository.VoterSearchBy;
import com.kahga.pluse.voter.service.VoterService;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

/**
 * The caste the agent picks on the assessment screen. It is held against the
 * voter rather than the entry, because it does not change between candidates or
 * between election cycles -- so what matters is that recording sentiment writes
 * it through, and that a later entry which leaves it unanswered does not wipe it.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class VoterCasteTest {

    @Autowired
    private SentimentService sentimentService;

    @Autowired
    private AccessRequestService accessRequestService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private VoterRepository voterRepository;

    @Autowired
    private VoterService voterService;

    @Test
    void recordingAnAssessmentWritesTheCasteOntoTheVoter() {
        Doorstep at = doorstep();

        sentimentService.record(at.agent(), at.voterId(), request(at, Caste.SC));

        assertThat(voterService.require(at.voterId()).getCaste()).isEqualTo(Caste.SC);
    }

    /** The same voter is recorded again for the next candidate: do not ask twice. */
    @Test
    void aLaterEntryWithNoCasteLeavesTheOneAlreadyRecorded() {
        Doorstep at = doorstep();
        sentimentService.record(at.agent(), at.voterId(), request(at, Caste.ST));

        sentimentService.record(at.agent(), at.voterId(), request(at, null));

        assertThat(voterService.require(at.voterId()).getCaste()).isEqualTo(Caste.ST);
    }

    /** Corrected at the door, the way the ward number already is. */
    @Test
    void answeringDifferentlyOverwritesIt() {
        Doorstep at = doorstep();
        sentimentService.record(at.agent(), at.voterId(), request(at, Caste.GENERAL));

        sentimentService.record(at.agent(), at.voterId(), request(at, Caste.SEBC));

        assertThat(voterService.require(at.voterId()).getCaste()).isEqualTo(Caste.SEBC);
    }

    /** Null is a real answer here -- "nobody has said yet" -- and must survive the round trip. */
    @Test
    void aVoterNobodyHasRecordedYetReadsBackWithNoCaste() {
        Voter fresh = voterRepository
                .search(null, doorstep().boothId(), null, VoterSearchBy.EPIC_NO, null, false, null, 0, 500)
                .content()
                .stream()
                .filter(voter -> voter.getCaste() == null)
                .findFirst()
                .orElseThrow(() -> new AssertionError("the seed records a caste for every voter in the booth"));

        assertThat(voterService.require(fresh.getId()).getCaste()).isNull();
    }

    /** A seeded agent, one booth their grant covers, and a voter on that roll. */
    private Doorstep doorstep() {
        for (User agent : userRepository.findByRoleOrderByNameAsc(Role.FIELD_AGENT)) {
            List<AgentBoothDto> booths = accessRequestService.accessibleBooths(agent.getId());
            if (booths.isEmpty()) {
                continue;
            }
            AgentBoothDto booth = booths.get(0);
            List<Voter> roll = voterRepository
                    .search(null, booth.boothId(), null, VoterSearchBy.EPIC_NO, null, false, null, 0, 1)
                    .content();
            if (!roll.isEmpty()) {
                return new Doorstep(agent, booth.boothId(), booth.candidateId(), roll.get(0).getId());
            }
        }
        throw new AssertionError("the seed has no agent holding a grant on a booth with voters");
    }

    private RecordSentimentRequest request(Doorstep at, Caste caste) {
        return new RecordSentimentRequest(
                at.candidateId(), SentimentValue.POSITIVE, ConfidenceLevel.HIGH, true, null, caste);
    }

    private record Doorstep(User agent, UUID boothId, UUID candidateId, UUID voterId) {}
}
