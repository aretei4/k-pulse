package com.kahga.pluse.housesentiment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.kahga.pluse.accessrequest.dto.AgentBoothDto;
import com.kahga.pluse.accessrequest.service.AccessRequestService;
import com.kahga.pluse.candidate.repository.CandidateRepository;
import com.kahga.pluse.common.exception.BusinessException;
import com.kahga.pluse.housesentiment.dto.HouseInsightsDto;
import com.kahga.pluse.housesentiment.dto.RecordHouseSentimentRequest;
import com.kahga.pluse.housesentiment.entity.HouseSentimentEntry;
import com.kahga.pluse.housesentiment.service.HouseSentimentService;
import com.kahga.pluse.sentiment.entity.ConfidenceLevel;
import com.kahga.pluse.user.entity.Role;
import com.kahga.pluse.user.entity.User;
import com.kahga.pluse.user.repository.UserRepository;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * FR-U12: a house tally rides on the agent's existing booth grant, has to add up
 * to the resident count, and never becomes a second row for the same house.
 */
@SpringBootTest
@ActiveProfiles("test")
class HouseSentimentTest {

    @Autowired
    private HouseSentimentService houseSentimentService;

    @Autowired
    private AccessRequestService accessRequestService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private CandidateRepository candidateRepository;

    private User agent;
    private AgentBoothDto booth;

    @BeforeEach
    void pickAnApprovedBooth() {
        agent = userRepository.findByRoleOrderByNameAsc(Role.FIELD_AGENT).stream()
                .filter(user -> !accessRequestService.accessibleBooths(user.getId()).isEmpty())
                .findFirst()
                .orElseThrow(() -> new AssertionError("seed data has no agent with an approved booth"));
        booth = accessRequestService.accessibleBooths(agent.getId()).get(0);
    }

    @Test
    void recordingAHouseTwiceOverwritesTheSameRow() {
        String houseNo = "42/" + UUID.randomUUID().toString().substring(0, 4);
        houseSentimentService.record(agent, request(houseNo, "Bijay Kumar Behera", 4, 3, 1, 0));

        HouseSentimentEntry updated =
                houseSentimentService.record(agent, request(houseNo.toLowerCase(), "Bijay Behera", 5, 1, 2, 2));

        List<HouseSentimentEntry> forHouse = houseSentimentService
                .list(agent, booth.boothId(), booth.candidateId())
                .stream()
                .filter(e -> e.getHouseNo().equalsIgnoreCase(houseNo))
                .toList();
        assertThat(forHouse).hasSize(1);
        assertThat(forHouse.get(0).getId()).isEqualTo(updated.getId());
        assertThat(forHouse.get(0).getHeadcount()).isEqualTo(5);
        assertThat(forHouse.get(0).getHouseName()).isEqualTo("Bijay Behera");
    }

    @Test
    void breakdownHasToAddUpToTheResidents() {
        // 6 people, 4 of them resident, but only 3 accounted for in the split.
        RecordHouseSentimentRequest payload = new RecordHouseSentimentRequest(
                booth.boothId(), booth.candidateId(), "91", "Gopal Swain", 4, 6, 4, 2, 1, 0, ConfidenceLevel.HIGH);

        assertThatThrownBy(() -> houseSentimentService.record(agent, payload))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("add up to 3, but the house has 4 resident(s)");
    }

    @Test
    void nonResidentsAreNotPartOfTheBreakdown() {
        // 6 people, 4 resident: a split covering exactly the 4 residents is fine.
        HouseSentimentEntry entry = houseSentimentService.record(
                agent,
                new RecordHouseSentimentRequest(
                        booth.boothId(),
                        booth.candidateId(),
                        "92/" + UUID.randomUUID().toString().substring(0, 4),
                        "Sarojini Jena",
                        4,
                        6,
                        4,
                        2,
                        1,
                        1,
                        ConfidenceLevel.HIGH));

        assertThat(entry.getHeadcount()).isEqualTo(6);
        assertThat(entry.getResidentialCount()).isEqualTo(4);
    }

    @Test
    void insightsSumTheBoothsHouses() {
        HouseInsightsDto before = houseSentimentService.insights(agent, booth.boothId(), booth.candidateId());

        String houseNo = "55/" + UUID.randomUUID().toString().substring(0, 4);
        houseSentimentService.record(
                agent,
                new RecordHouseSentimentRequest(
                        booth.boothId(),
                        booth.candidateId(),
                        houseNo,
                        "Prafulla Mohanty",
                        4,
                        7,
                        5,
                        3,
                        1,
                        1,
                        ConfidenceLevel.HIGH));

        HouseInsightsDto after = houseSentimentService.insights(agent, booth.boothId(), booth.candidateId());

        assertThat(after.houses()).isEqualTo(before.houses() + 1);
        assertThat(after.people()).isEqualTo(before.people() + 7);
        assertThat(after.residents()).isEqualTo(before.residents() + 5);
        assertThat(after.positive()).isEqualTo(before.positive() + 3);
        assertThat(after.neutral()).isEqualTo(before.neutral() + 1);
        assertThat(after.negative()).isEqualTo(before.negative() + 1);
        assertThat(after.confidence().high()).isEqualTo(before.confidence().high() + 1);
        // The split always covers the residents, never the full headcount.
        assertThat(after.positive() + after.neutral() + after.negative()).isEqualTo(after.residents());
    }

    @Test
    void insightsRefuseABoothTheAgentCannotSee() {
        User stranger = userRepository.findByRoleOrderByNameAsc(Role.FIELD_AGENT).stream()
                .filter(user -> accessRequestService.accessibleBooths(user.getId()).stream()
                        .noneMatch(b -> b.boothId().equals(booth.boothId())))
                .findFirst()
                .orElseThrow(() -> new AssertionError("seed data has no agent without this booth"));

        assertThatThrownBy(() -> houseSentimentService.insights(stranger, booth.boothId(), booth.candidateId()))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void anotherCandidateOnTheSameBoothIsRefused() {
        UUID other = candidateRepository.findAllByOrderByNameAsc().stream()
                .map(candidate -> candidate.getId())
                .filter(id -> !id.equals(booth.candidateId()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("seed data has only one candidate"));

        RecordHouseSentimentRequest payload = new RecordHouseSentimentRequest(
                booth.boothId(), other, "7", "Ranjit Nayak", 4, 3, 3, 3, 0, 0, ConfidenceLevel.HIGH);

        assertThatThrownBy(() -> houseSentimentService.record(agent, payload))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("records sentiment for");
    }

    @Test
    void aBoothTheAgentHasNoGrantOnIsRefused() {
        User stranger = userRepository.findByRoleOrderByNameAsc(Role.FIELD_AGENT).stream()
                .filter(user -> accessRequestService.accessibleBooths(user.getId()).stream()
                        .noneMatch(b -> b.boothId().equals(booth.boothId())))
                .findFirst()
                .orElseThrow(() -> new AssertionError("seed data has no agent without this booth"));

        assertThatThrownBy(() -> houseSentimentService.list(stranger, booth.boothId(), booth.candidateId()))
                .isInstanceOf(BusinessException.class);
    }

    private RecordHouseSentimentRequest request(
            String houseNo, String houseName, int headcount, int positive, int neutral, int negative) {
        return new RecordHouseSentimentRequest(
                booth.boothId(),
                booth.candidateId(),
                houseNo,
                houseName,
                4,
                headcount,
                headcount,
                positive,
                neutral,
                negative,
                ConfidenceLevel.MEDIUM);
    }
}
