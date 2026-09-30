package com.kahga.pluse.accountdeletion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.kahga.pluse.accessrequest.dto.AgentBoothDto;
import com.kahga.pluse.accessrequest.dto.CreateAccessRequestDto;
import com.kahga.pluse.accessrequest.service.AccessRequestService;
import com.kahga.pluse.accountdeletion.dto.CreateDeletionRequestDto;
import com.kahga.pluse.accountdeletion.entity.AccountDeletionRequest;
import com.kahga.pluse.accountdeletion.entity.DeletionStatus;
import com.kahga.pluse.accountdeletion.service.AccountDeletionService;
import com.kahga.pluse.common.exception.BusinessException;
import com.kahga.pluse.housesentiment.dto.RecordHouseSentimentRequest;
import com.kahga.pluse.housesentiment.service.HouseSentimentService;
import com.kahga.pluse.location.entity.UnitLevel;
import com.kahga.pluse.location.repository.UnitRepository;
import com.kahga.pluse.sentiment.entity.ConfidenceLevel;
import com.kahga.pluse.user.entity.Role;
import com.kahga.pluse.user.entity.User;
import com.kahga.pluse.user.repository.UserRepository;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import com.kahga.pluse.security.UserPrincipal;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;

/**
 * Account deletion: filing changes nothing, approving erases the account and
 * everything that agent recorded.
 */
@SpringBootTest
@ActiveProfiles("test")
class AccountDeletionTest {

    @Autowired
    private AccountDeletionService accountDeletionService;

    @Autowired
    private HouseSentimentService houseSentimentService;

    @Autowired
    private AccessRequestService accessRequestService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private UnitRepository unitRepository;

    /** These exercise admin operations, which now resolve the calling admin's scope. */
    @BeforeEach
    void signInAsSuperAdmin() {
        User superAdmin = userRepository.findByRoleOrderByNameAsc(Role.SUPER_ADMIN).stream()
                .filter(User::isActive)
                .findFirst()
                .orElseThrow(() -> new AssertionError("the bootstrap super admin is missing"));
        var principal = UserPrincipal.from(superAdmin);
        SecurityContextHolder.getContext()
                .setAuthentication(
                        new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities()));
    }

    @AfterEach
    void signOut() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void filingLeavesTheAccountAlone() {
        User agent = newAgent();
        accountDeletionService.request(new CreateDeletionRequestDto(agent.getPhone(), "leaving the campaign"));

        AccountDeletionRequest filed = pendingFor(agent);
        assertThat(filed.getReason()).isEqualTo("leaving the campaign");
        assertThat(filed.getAgentName()).isEqualTo(agent.getName());
        assertThat(userRepository.findById(agent.getId())).isPresent();
    }

    @Test
    void askingTwiceDoesNotQueueASecondRequest() {
        User agent = newAgent();
        accountDeletionService.request(new CreateDeletionRequestDto(agent.getPhone(), null));
        accountDeletionService.request(new CreateDeletionRequestDto(agent.getPhone(), "again"));

        assertThat(accountDeletionService.list(DeletionStatus.PENDING).stream()
                        .filter(r -> r.getAgentPhone().equals(agent.getPhone()))
                        .count())
                .isEqualTo(1);
    }

    @Test
    void anUnknownNumberIsAcceptedButFilesNothing() {
        long before = accountDeletionService.countPending();

        accountDeletionService.request(new CreateDeletionRequestDto("9000000001", null));

        assertThat(accountDeletionService.countPending()).isEqualTo(before);
    }

    @Test
    void approvingDeletesTheAccountAndEverythingTheyRecorded() {
        User agent = agentWithABooth();
        AgentBoothDto booth = accessRequestService.accessibleBooths(agent.getId()).get(0);
        houseSentimentService.record(
                agent,
                new RecordHouseSentimentRequest(
                        booth.boothId(),
                        booth.candidateId(),
                        "77/" + UUID.randomUUID().toString().substring(0, 4),
                        "Bijay Kumar Behera",
                        4,
                        4,
                        3,
                        2,
                        1,
                        0,
                        ConfidenceLevel.HIGH));

        accountDeletionService.request(new CreateDeletionRequestDto(agent.getPhone(), null));
        User admin = userRepository.findByRoleOrderByNameAsc(Role.SUPER_ADMIN).get(0);

        AccountDeletionRequest approved =
                accountDeletionService.approve(admin, pendingFor(agent).getId(), "confirmed by phone");

        assertThat(approved.getStatus()).isEqualTo(DeletionStatus.APPROVED);
        assertThat(approved.getDeletedEntries()).isGreaterThan(0);
        assertThat(approved.getUser()).isNull();
        // The request row survives as the record of what was granted.
        assertThat(approved.getAgentName()).isNotBlank();
        assertThat(approved.getReviewedBy().getId()).isEqualTo(admin.getId());
        assertThat(userRepository.findById(agent.getId())).isEmpty();
        assertThat(accessRequestService.accessibleBooths(agent.getId())).isEmpty();
    }

    @Test
    void aRequestCanOnlyBeDecidedOnce() {
        User agent = newAgent();
        accountDeletionService.request(new CreateDeletionRequestDto(agent.getPhone(), null));
        User admin = userRepository.findByRoleOrderByNameAsc(Role.SUPER_ADMIN).get(0);
        UUID id = pendingFor(agent).getId();
        accountDeletionService.reject(admin, id, "stays on the campaign");

        assertThatThrownBy(() -> accountDeletionService.approve(admin, id, null))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("already rejected");
        assertThat(userRepository.findById(agent.getId())).isPresent();
    }

    private AccountDeletionRequest pendingFor(User agent) {
        return accountDeletionService.list(DeletionStatus.PENDING).stream()
                .filter(r -> r.getAgentPhone().equals(agent.getPhone()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("no pending request for " + agent.getName()));
    }

    /**
     * A throwaway agent per test. Deletion is destructive and the tests share one
     * database, so none of them may touch the seeded agents other tests rely on.
     */
    private static final AtomicInteger SEQUENCE = new AtomicInteger();

    private User newAgent() {
        int seq = SEQUENCE.incrementAndGet();
        String suffix = String.format("%09d", seq);
        return userRepository.save(User.builder()
                .id(UUID.randomUUID())
                .name("Deletion test " + seq)
                .phone("7" + suffix)
                .email("deletion-" + suffix + "@k-pulse.test")
                .role(Role.FIELD_AGENT)
                .active(true)
                .createdAt(Instant.now())
                .build());
    }

    /** The same, with an approved booth so there is recorded work to erase. */
    private User agentWithABooth() {
        User agent = newAgent();
        UUID boothId = unitRepository.findByLevelOrderByNameAsc(UnitLevel.BOOTH).get(0).getId();
        UUID candidateId = accessRequestService.list(null).get(0).getCandidate().getId();
        var request = accessRequestService.create(agent, new CreateAccessRequestDto(boothId, candidateId));
        accessRequestService.approve(request.getId(), 6, null);
        return agent;
    }
}
