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

/**
 * The voter list's search box matches the name plus one identifier, and which
 * one depends on who is looking: an admin works from the roll's EPIC no., an
 * agent from the house number on the doorstep.
 */
@SpringBootTest
@ActiveProfiles("test")
class VoterSearchTest {

    @Autowired
    private LocationService locationService;

    @Autowired
    private UnitRepository unitRepository;

    @Autowired
    private VoterRepository voterRepository;

    @Test
    void agentsSearchTheHouseNumberAndAdminsTheEpic() {
        Unit panchayat = unitRepository.findByLevelOrderByNameAsc(UnitLevel.PANCHAYAT).get(0);
        Unit booth = locationService.create(
                new UnitRequest(UnitLevel.BOOTH, "Search test " + UUID.randomUUID(), panchayat.getId()));
        String marker = UUID.randomUUID().toString().substring(0, 6).toUpperCase();

        // One voter whose house number carries the marker, another whose EPIC does.
        voterRepository.insertAll(List.of(
                voter(booth, "H" + marker, "EPICA" + UUID.randomUUID().toString().substring(0, 6), "Anil"),
                voter(booth, "99", "EPIC" + marker, "Bina")));

        List<String> byHouse = names(booth, marker, VoterSearchBy.HOUSE_NO);
        List<String> byEpic = names(booth, marker, VoterSearchBy.EPIC_NO);

        assertThat(byHouse).containsExactly("Anil");
        assertThat(byEpic).containsExactly("Bina");
    }

    @Test
    void bothModesStillMatchTheName() {
        Unit panchayat = unitRepository.findByLevelOrderByNameAsc(UnitLevel.PANCHAYAT).get(0);
        Unit booth = locationService.create(
                new UnitRequest(UnitLevel.BOOTH, "Search test " + UUID.randomUUID(), panchayat.getId()));
        String name = "Chitra" + UUID.randomUUID().toString().substring(0, 6);
        voterRepository.insertAll(List.of(voter(booth, "7", "EPIC" + UUID.randomUUID(), name)));

        assertThat(names(booth, name, VoterSearchBy.HOUSE_NO)).containsExactly(name);
        assertThat(names(booth, name, VoterSearchBy.EPIC_NO)).containsExactly(name);
    }

    private List<String> names(Unit booth, String term, VoterSearchBy searchBy) {
        return voterRepository.search(null, booth.getId(), term, searchBy, null, false, null, 0, 50).content().stream()
                .map(Voter::getName)
                .toList();
    }

    private static Voter voter(Unit booth, String houseNo, String epicNo, String name) {
        return Voter.builder()
                .id(UUID.randomUUID())
                .epicNo(epicNo)
                .name(name)
                .houseNo(houseNo)
                .age(30)
                .gender(Gender.F)
                .booth(booth)
                .createdAt(Instant.now())
                .build();
    }
}
