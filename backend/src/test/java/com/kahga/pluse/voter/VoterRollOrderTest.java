package com.kahga.pluse.voter;

import static org.assertj.core.api.Assertions.assertThat;

import com.kahga.pluse.location.dto.UnitRequest;
import com.kahga.pluse.location.entity.Unit;
import com.kahga.pluse.location.entity.UnitLevel;
import com.kahga.pluse.location.repository.UnitRepository;
import com.kahga.pluse.location.service.LocationService;
import com.kahga.pluse.voter.entity.Gender;
import com.kahga.pluse.voter.entity.Voter;
import com.kahga.pluse.voter.repository.VoterRepository;
import com.kahga.pluse.voter.repository.VoterSearchBy;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/** The voter list follows the roll: house numbers in numeric order, not text order. */
@SpringBootTest
@ActiveProfiles("test")
class VoterRollOrderTest {

    @Autowired
    private LocationService locationService;

    @Autowired
    private UnitRepository unitRepository;

    @Autowired
    private VoterRepository voterRepository;

    @Test
    void listIsSortedByHouseNumberNumerically() {
        Unit panchayat = unitRepository.findByLevelOrderByNameAsc(UnitLevel.PANCHAYAT).get(0);
        Unit booth = locationService.create(
                new UnitRequest(UnitLevel.BOOTH, "Order test " + UUID.randomUUID(), panchayat.getId()));

        // Inserted out of order, with the cases a text sort gets wrong.
        voterRepository.insertAll(List.of(
                voter(booth, "13", "Anil"),
                voter(booth, null, "Blank"),
                voter(booth, "4/A", "Bina"),
                voter(booth, "2", "Chitra"),
                voter(booth, "4", "Asha"),
                voter(booth, "100", "Dev"),
                voter(booth, "B-7", "Lettered")));

        List<String> order = voterRepository.search(null, booth.getId(), null, VoterSearchBy.EPIC_NO, null, false, null, 0, 50)
                .content().stream()
                .map(v -> v.getHouseNo() + " " + v.getName())
                .toList();

        assertThat(order).containsExactly(
                "2 Chitra", "4 Asha", "4/A Bina", "13 Anil", "100 Dev", "B-7 Lettered", "null Blank");
    }

    private static Voter voter(Unit booth, String houseNo, String name) {
        return Voter.builder()
                .id(UUID.randomUUID())
                .epicNo("ORD" + UUID.randomUUID().toString().substring(0, 8))
                .name(name)
                .houseNo(houseNo)
                .age(30)
                .gender(Gender.F)
                .booth(booth)
                .createdAt(Instant.now())
                .build();
    }
}
