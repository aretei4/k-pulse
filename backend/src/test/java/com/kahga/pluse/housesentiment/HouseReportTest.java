package com.kahga.pluse.housesentiment;

import static org.assertj.core.api.Assertions.assertThat;

import com.kahga.pluse.accessrequest.dto.AgentBoothDto;
import com.kahga.pluse.accessrequest.service.AccessRequestService;
import com.kahga.pluse.housesentiment.dto.HouseEntryRowDto;
import com.kahga.pluse.housesentiment.dto.HouseReportFilter;
import com.kahga.pluse.housesentiment.dto.HouseReportResponse;
import com.kahga.pluse.housesentiment.dto.HouseUnitSummaryDto;
import com.kahga.pluse.housesentiment.dto.RecordHouseSentimentRequest;
import com.kahga.pluse.housesentiment.service.HouseExcelReportGenerator;
import com.kahga.pluse.housesentiment.service.HousePdfReportGenerator;
import com.kahga.pluse.housesentiment.service.HouseReportService;
import com.kahga.pluse.housesentiment.service.HouseSentimentService;
import com.kahga.pluse.location.entity.Unit;
import com.kahga.pluse.location.entity.UnitLevel;
import com.kahga.pluse.location.service.LocationService;
import com.kahga.pluse.sentiment.entity.ConfidenceLevel;
import com.kahga.pluse.user.entity.Role;
import com.kahga.pluse.user.entity.User;
import com.kahga.pluse.user.repository.UserRepository;
import java.util.List;
import java.util.UUID;
import com.kahga.pluse.security.UserPrincipal;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;

/** The admin dashboard's roll-up: booth counts summed up the hierarchy, nothing double-counted. */
@SpringBootTest
@ActiveProfiles("test")
class HouseReportTest {

    @Autowired
    private HouseReportService houseReportService;

    @Autowired
    private HouseSentimentService houseSentimentService;

    @Autowired
    private AccessRequestService accessRequestService;

    @Autowired
    private LocationService locationService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private HousePdfReportGenerator pdfReportGenerator;

    @Autowired
    private HouseExcelReportGenerator excelReportGenerator;

    private User agent;
    private AgentBoothDto booth;

    @AfterEach
    void signOut() {
        SecurityContextHolder.clearContext();
    }

    @BeforeEach
    void pickAnApprovedBooth() {
        // The admin report resolves the caller's scope, so these run as the
        // unrestricted super admin the way the admin screens do.
        User superAdmin = userRepository.findByRoleOrderByNameAsc(Role.SUPER_ADMIN).stream()
                .filter(User::isActive)
                .findFirst()
                .orElseThrow(() -> new AssertionError("the bootstrap super admin is missing"));
        var principal = UserPrincipal.from(superAdmin);
        SecurityContextHolder.getContext()
                .setAuthentication(
                        new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities()));

        agent = userRepository.findByRoleOrderByNameAsc(Role.FIELD_AGENT).stream()
                .filter(user -> !accessRequestService.accessibleBooths(user.getId()).isEmpty())
                .findFirst()
                .orElseThrow(() -> new AssertionError("seed data has no agent with an approved booth"));
        booth = accessRequestService.accessibleBooths(agent.getId()).get(0);
    }

    @Test
    void aRecordedHouseShowsUpAtEveryLevelAboveItsBooth() {
        HouseReportResponse boothBefore = houseReportService.report(filter(UnitLevel.BOOTH, null, null));
        HouseReportResponse districtBefore = houseReportService.report(filter(UnitLevel.DISTRICT, null, null));

        record();

        HouseReportResponse boothAfter = houseReportService.report(filter(UnitLevel.BOOTH, null, null));
        HouseReportResponse districtAfter = houseReportService.report(filter(UnitLevel.DISTRICT, null, null));

        assertThat(boothAfter.totals().houses()).isEqualTo(boothBefore.totals().houses() + 1);
        assertThat(boothAfter.totals().people()).isEqualTo(boothBefore.totals().people() + 6);
        assertThat(boothAfter.totals().residents()).isEqualTo(boothBefore.totals().residents() + 4);

        // Rolling up must not change the totals, only how they are grouped.
        assertThat(districtAfter.totals().houses()).isEqualTo(boothAfter.totals().houses());
        assertThat(districtAfter.totals().residents()).isEqualTo(boothAfter.totals().residents());
        assertThat(districtAfter.totals().houses()).isEqualTo(districtBefore.totals().houses() + 1);
        assertThat(districtAfter.rows()).hasSizeLessThan(boothAfter.rows().size());
    }

    @Test
    void rowsSumToTheTotals() {
        record();
        HouseReportResponse report = houseReportService.report(filter(UnitLevel.PANCHAYAT, null, null));

        assertThat(report.rows().stream().mapToLong(HouseUnitSummaryDto::houses).sum())
                .isEqualTo(report.totals().houses());
        assertThat(report.rows().stream().mapToLong(HouseUnitSummaryDto::residents).sum())
                .isEqualTo(report.totals().residents());
        long split = report.totals().positive() + report.totals().neutral() + report.totals().negative();
        assertThat(split).isEqualTo(report.totals().residents());
    }

    @Test
    void filteringByUnitKeepsOnlyWhatIsUnderIt() {
        record();
        Unit boothUnit = locationService.require(booth.boothId());
        UUID parentId = boothUnit.getParent().getId();

        HouseReportResponse everything = houseReportService.report(filter(UnitLevel.BOOTH, null, null));
        HouseReportResponse underParent = houseReportService.report(filter(UnitLevel.BOOTH, parentId, null));

        assertThat(underParent.totals().houses()).isPositive();
        assertThat(underParent.totals().houses()).isLessThanOrEqualTo(everything.totals().houses());
        assertThat(underParent.rows()).allSatisfy(row -> assertThat(locationService
                        .require(row.unitId())
                        .getParent()
                        .getId())
                .isEqualTo(parentId));
    }

    @Test
    void anotherCandidateSeesNoneOfTheseHouses() {
        record();
        UUID otherCandidate = UUID.randomUUID();

        HouseReportResponse report = houseReportService.report(filter(UnitLevel.BOOTH, null, otherCandidate));

        assertThat(report.totals().houses()).isZero();
        assertThat(report.rows()).allSatisfy(row -> assertThat(row.houses()).isZero());
    }

    @Test
    void theHousesBehindARowAddUpToIt() {
        record();
        Unit boothUnit = locationService.require(booth.boothId());
        UUID panchayatId = boothUnit.getParent().getId();

        HouseReportResponse report = houseReportService.report(filter(UnitLevel.PANCHAYAT, panchayatId, null));
        HouseUnitSummaryDto row = report.rows().stream()
                .filter(r -> r.unitId().equals(panchayatId))
                .findFirst()
                .orElseThrow(() -> new AssertionError("the panchayat is missing from its own report"));

        List<HouseEntryRowDto> houses = houseReportService.entries(filter(null, panchayatId, null));

        assertThat(houses).hasSize((int) row.houses());
        assertThat(houses.stream().mapToLong(HouseEntryRowDto::headcount).sum()).isEqualTo(row.people());
        assertThat(houses.stream().mapToLong(HouseEntryRowDto::residentialCount).sum())
                .isEqualTo(row.residents());
        assertThat(houses.stream().mapToLong(HouseEntryRowDto::positiveCount).sum()).isEqualTo(row.positive());
        // Each row carries what the drill-down screen shows.
        assertThat(houses).allSatisfy(house -> {
            assertThat(house.houseNo()).isNotBlank();
            assertThat(house.boothName()).isNotBlank();
            assertThat(house.recordedByName()).isNotBlank();
        });
    }

    @Test
    void theUnitHouseListExportsAsRealFiles() {
        record();
        UUID panchayatId = locationService.require(booth.boothId()).getParent().getId();
        HouseReportFilter filter = filter(null, panchayatId, null);
        List<HouseEntryRowDto> houses = houseReportService.entries(filter);
        assertThat(houses).isNotEmpty();

        byte[] pdf = pdfReportGenerator.generateEntries(houses, "Kansabansa", filter);
        byte[] excel = excelReportGenerator.generateEntries(houses, "Kansabansa", filter);

        assertThat(new String(pdf, 0, 4, java.nio.charset.StandardCharsets.ISO_8859_1)).isEqualTo("%PDF");
        assertThat(pdf.length).isGreaterThan(1000);
        assertThat(excel[0]).isEqualTo((byte) 'P');
        assertThat(excel[1]).isEqualTo((byte) 'K');
        assertThat(excel.length).isGreaterThan(1000);
    }

    @Test
    void bothExportsProduceRealFiles() {
        record();
        HouseReportFilter filter = filter(UnitLevel.PANCHAYAT, null, null);
        HouseReportResponse report = houseReportService.report(filter);

        byte[] pdf = pdfReportGenerator.generate(report, filter);
        byte[] excel = excelReportGenerator.generate(report, filter);

        assertThat(new String(pdf, 0, 4, java.nio.charset.StandardCharsets.ISO_8859_1)).isEqualTo("%PDF");
        assertThat(pdf.length).isGreaterThan(1000);
        // .xlsx is a zip: "PK" then two control bytes.
        assertThat(excel[0]).isEqualTo((byte) 'P');
        assertThat(excel[1]).isEqualTo((byte) 'K');
        assertThat(excel.length).isGreaterThan(1000);
    }

    private HouseReportFilter filter(UnitLevel level, UUID unitId, UUID candidateId) {
        return new HouseReportFilter(level, unitId, candidateId, null, null, null);
    }

    private void record() {
        houseSentimentService.record(
                agent,
                new RecordHouseSentimentRequest(
                        booth.boothId(),
                        booth.candidateId(),
                        "88/" + UUID.randomUUID().toString().substring(0, 4),
                        "Prafulla Mohanty",
                        4,
                        6,
                        4,
                        2,
                        1,
                        1,
                        ConfidenceLevel.HIGH));
    }
}
