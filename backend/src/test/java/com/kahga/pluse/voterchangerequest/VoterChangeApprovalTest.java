package com.kahga.pluse.voterchangerequest;

import static org.assertj.core.api.Assertions.assertThat;

import com.kahga.pluse.voter.repository.VoterRepository;
import com.kahga.pluse.voterchangerequest.entity.VoterChangeRequest;
import com.kahga.pluse.voterchangerequest.entity.VoterChangeStatus;
import com.kahga.pluse.voterchangerequest.entity.VoterChangeType;
import com.kahga.pluse.voterchangerequest.repository.VoterChangeRequestRepository;
import com.kahga.pluse.voterchangerequest.service.VoterChangeRequestService;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * FR-A8 / FR-U11: a proposal is inert until an admin decides, and only then does
 * the roll move.
 */
@SpringBootTest
@ActiveProfiles("test")
class VoterChangeApprovalTest {

    @Autowired
    private VoterChangeRequestService service;

    @Autowired
    private VoterChangeRequestRepository repository;

    @Autowired
    private VoterRepository voterRepository;

    @Test
    void rejectingLeavesTheRollUntouched() {
        VoterChangeRequest edit = pending(VoterChangeType.EDIT);
        UUID voterId = edit.getVoter().getId();
        String before = voterRepository.findById(voterId).orElseThrow().getName();

        service.reject(edit.getId(), "not verified");

        assertThat(repository.findById(edit.getId()).orElseThrow().getStatus())
                .isEqualTo(VoterChangeStatus.REJECTED);
        assertThat(voterRepository.findById(voterId).orElseThrow().getName()).isEqualTo(before);
    }

    @Test
    void approvingADeleteRemovesTheVoter() {
        VoterChangeRequest delete = pending(VoterChangeType.DELETE);
        UUID voterId = delete.getVoter().getId();
        long before = voterRepository.count();

        service.approve(delete.getId(), null);

        assertThat(repository.findById(delete.getId()).orElseThrow().getStatus())
                .isEqualTo(VoterChangeStatus.APPROVED);
        assertThat(voterRepository.findById(voterId)).isEmpty();
        assertThat(voterRepository.count()).isEqualTo(before - 1);
    }

    @Test
    void approvingAnAddCreatesTheVoter() {
        VoterChangeRequest add = pending(VoterChangeType.ADD);
        long before = voterRepository.count();

        service.approve(add.getId(), null);

        assertThat(voterRepository.count()).isEqualTo(before + 1);
        assertThat(voterRepository.findByEpicNoIgnoreCase(add.getEpicNo())).isPresent();
    }

    private VoterChangeRequest pending(VoterChangeType type) {
        return repository.findByStatusOrderByProposedAtDesc(VoterChangeStatus.PENDING).stream()
                .filter(change -> change.getChangeType() == type)
                .findFirst()
                .orElseThrow(() -> new AssertionError("seed data has no pending " + type + " proposal"));
    }
}
