package com.kahga.pluse.seed;

import org.springframework.core.annotation.Order;
import com.kahga.pluse.accessrequest.entity.AccessRequest;
import com.kahga.pluse.accessrequest.entity.AccessRequestStatus;
import com.kahga.pluse.accessrequest.repository.AccessRequestRepository;
import com.kahga.pluse.candidate.entity.Candidate;
import com.kahga.pluse.candidate.repository.CandidateRepository;
import com.kahga.pluse.config.KPulseProperties;
import com.kahga.pluse.location.entity.Unit;
import com.kahga.pluse.location.entity.UnitLevel;
import com.kahga.pluse.location.repository.UnitRepository;
import com.kahga.pluse.sentiment.entity.ConfidenceLevel;
import com.kahga.pluse.sentiment.entity.SentimentEntry;
import com.kahga.pluse.sentiment.entity.SentimentValue;
import com.kahga.pluse.sentiment.repository.SentimentEntryRepository;
import com.kahga.pluse.user.entity.Role;
import com.kahga.pluse.user.entity.User;
import com.kahga.pluse.user.repository.UserRepository;
import com.kahga.pluse.voter.entity.Gender;
import com.kahga.pluse.voter.entity.Voter;
import com.kahga.pluse.voter.repository.VoterRepository;
import com.kahga.pluse.voterchangerequest.entity.VoterChangeRequest;
import com.kahga.pluse.voterchangerequest.entity.VoterChangeStatus;
import com.kahga.pluse.voterchangerequest.entity.VoterChangeType;
import com.kahga.pluse.voterchangerequest.repository.VoterChangeRequestRepository;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.ApplicationArguments;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Loads the Bhadrak demo dataset the first time the app starts against a database
 * with no units in it — the same shape the SPA's mock mode serves, so the two
 * modes look alike. Turn it off with KPULSE_SEED=false.
 *
 * <p>The admin login is not created here. AdminAccountInitializer handles it from
 * kpulse.admin.* and runs first, which is why "already seeded" is judged by units
 * rather than users: the admin alone must not stop the demo data loading.
 */
@Slf4j
@Component
@Order(1)
@RequiredArgsConstructor
public class DataSeeder implements ApplicationRunner {

    private static final String[] MALE_FIRST = {
        "Bijay Kumar", "Ranjit Kumar", "Sanjay", "Prakash", "Dillip", "Sushant", "Kunja Bihari", "Ashok", "Debendra", "Niranjan"
    };
    private static final String[] FEMALE_FIRST = {
        "Sabitri", "Manju Rani", "Sasmita", "Pramila", "Anita", "Basanti", "Jyotsna", "Kabita", "Sunita", "Rashmita"
    };
    private static final String[] SURNAMES = {
        "Behera", "Nayak", "Swain", "Jena", "Sahoo", "Das", "Patra", "Mohanty", "Rout", "Sethi", "Pradhan", "Barik"
    };
    private static final String[] HEADS = {
        "Sridhar", "Ranjit", "Gopal", "Bhagaban", "Dasarathi", "Narayan", "Trilochan", "Padmalochan"
    };

    private final KPulseProperties properties;
    private final UserRepository userRepository;
    private final CandidateRepository candidateRepository;
    private final UnitRepository unitRepository;
    private final VoterRepository voterRepository;
    private final AccessRequestRepository accessRequestRepository;
    private final SentimentEntryRepository sentimentEntryRepository;
    private final VoterChangeRequestRepository voterChangeRequestRepository;

    private final Random random = new Random(20260909L);

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (!properties.getSeed().isEnabled() || unitRepository.count() > 0) {
            return;
        }
        log.info("Seeding the K-Pulse demo dataset");

        List<User> agents = List.of(
                agent("Prakash Sahoo", "prakash@example.com", "9861000011", true),
                agent("Anita Das", "anita@example.com", "9861000012", true),
                agent("Suresh Patra", "suresh@example.com", "9861000013", true),
                agent("Manoj Barik", "manoj@example.com", "9861000014", false));
        userRepository.saveAll(agents);

        Candidate mohanty = candidateRepository.save(candidate("R. Mohanty"));
        Candidate pradhan = candidateRepository.save(candidate("K. Pradhan"));

        Unit district = unitRepository.save(unit(UnitLevel.DISTRICT, "Bhadrak", "Bhadrak", null));

        List<Unit> booths = new ArrayList<>();
        String[][] structure = {
            {"Tihidi", "Kansabansa", "Booth 12", "Booth 13"},
            {"Tihidi", "Erein", "Booth 27"},
            {"Chandbali", "Gadiali", "Booth 42"},
            {"Chandbali", "Barikpur", "Booth 58"},
            {"Basudevpur", "Eram", "Booth 71"},
        };

        List<Unit> blocks = new ArrayList<>();
        for (String[] row : structure) {
            Unit block = blocks.stream()
                    .filter(b -> b.getName().equals(row[0]))
                    .findFirst()
                    .orElseGet(() -> {
                        Unit created = unitRepository.save(
                                unit(UnitLevel.BLOCK, row[0], district.getPath() + " > " + row[0], district));
                        blocks.add(created);
                        return created;
                    });

            Unit panchayat = unitRepository.save(
                    unit(UnitLevel.PANCHAYAT, row[1], block.getPath() + " > " + row[1], block));

            for (int i = 2; i < row.length; i++) {
                booths.add(unitRepository.save(
                        unit(UnitLevel.BOOTH, row[i], panchayat.getPath() + " > " + row[i], panchayat)));
            }
        }

        int epicSeq = 0;
        List<Voter> voters = new ArrayList<>();
        for (Unit booth : booths) {
            for (int i = 0; i < 40; i++) {
                epicSeq++;
                boolean female = random.nextBoolean();
                String surname = pick(SURNAMES);
                String prefix = female ? (random.nextBoolean() ? "W/O" : "D/O") : "S/O";
                voters.add(Voter.builder()
                        .id(UUID.randomUUID())
                        .epicNo("ODA" + String.format("%06d", epicSeq))
                        .name((female ? pick(FEMALE_FIRST) : pick(MALE_FIRST)) + " " + surname)
                        .relation(prefix + " " + pick(HEADS) + " " + surname)
                        .houseNo(String.valueOf(1 + random.nextInt(220)))
                        .age(18 + random.nextInt(65))
                        .gender(female ? Gender.F : Gender.M)
                        .booth(booth)
                        .wardNo(1 + random.nextInt(20))
                        .createdAt(Instant.now())
                        .build());
            }
        }
        voterRepository.insertAll(voters);

        User prakash = agents.get(0);
        User anita = agents.get(1);
        User suresh = agents.get(2);

        accessRequestRepository.save(accessRequest(prakash, booths.get(0), mohanty, AccessRequestStatus.APPROVED, 12));
        accessRequestRepository.save(accessRequest(anita, booths.get(1), pradhan, AccessRequestStatus.APPROVED, 20));
        accessRequestRepository.save(accessRequest(prakash, booths.get(2), mohanty, AccessRequestStatus.PENDING, 5));
        accessRequestRepository.save(accessRequest(
                anita, unitRepository.findByLevelOrderByNameAsc(UnitLevel.PANCHAYAT).get(0), mohanty,
                AccessRequestStatus.PENDING, 4));
        accessRequestRepository.save(accessRequest(
                suresh, unitRepository.findByLevelOrderByNameAsc(UnitLevel.BLOCK).get(0), pradhan,
                AccessRequestStatus.PENDING, 3));

        // Pre-record part of the first booth so the charts have something to show.
        List<Voter> firstBooth = voters.stream()
                .filter(v -> v.getBooth().getId().equals(booths.get(0).getId()))
                .toList();
        List<SentimentEntry> entries = new ArrayList<>();
        for (int i = 0; i < firstBooth.size() * 3 / 4; i++) {
            Voter voter = firstBooth.get(i);
            double roll = random.nextDouble();
            SentimentValue value = roll < 0.52 ? SentimentValue.POSITIVE
                    : roll < 0.64 ? SentimentValue.NEUTRAL
                    : SentimentValue.NEGATIVE;
            Instant recordedAt = Instant.now().minus(1 + random.nextInt(20), ChronoUnit.DAYS);
            entries.add(SentimentEntry.builder()
                    .id(UUID.randomUUID())
                    .voter(voter)
                    .candidate(mohanty)
                    .sentiment(value)
                    .confidence(ConfidenceLevel.values()[random.nextInt(3)])
                    .resident(random.nextDouble() > 0.12)
                    .wardNo(voter.getWardNo())
                    .recordedBy(prakash)
                    .recordedAt(recordedAt)
                    .updatedAt(recordedAt)
                    .build());
        }
        sentimentEntryRepository.saveAll(entries);

        voterChangeRequestRepository.saveAll(List.of(
                change(prakash, firstBooth.get(1), booths.get(0), VoterChangeType.EDIT,
                        "Age corrected against the EPIC card"),
                change(prakash, firstBooth.get(2), booths.get(0), VoterChangeType.DELETE,
                        "Voter deceased — confirmed with the family"),
                addChange(anita, booths.get(1))));

        log.info("Seeded {} voters across {} booths", voters.size(), booths.size());
        log.info("Demo agent numbers: 9861000011, 9861000012 — the OTP is printed in this log on request");
    }

    private User agent(String name, String email, String phone, boolean active) {
        return User.builder()
                .id(UUID.randomUUID())
                .name(name)
                .email(email)
                .phone(phone)
                .role(Role.FIELD_AGENT)
                .active(active)
                .createdAt(Instant.now())
                .build();
    }

    private Candidate candidate(String name) {
        return Candidate.builder().id(UUID.randomUUID()).name(name).party("Independent").build();
    }

    private Unit unit(UnitLevel level, String name, String path, Unit parent) {
        return Unit.builder().id(UUID.randomUUID()).level(level).name(name).path(path).parent(parent).build();
    }

    private AccessRequest accessRequest(
            User agent, Unit unit, Candidate candidate, AccessRequestStatus status, int daysAgo) {
        Instant requestedAt = Instant.now().minus(daysAgo, ChronoUnit.DAYS);
        return AccessRequest.builder()
                .id(UUID.randomUUID())
                .agent(agent)
                .unit(unit)
                .candidate(candidate)
                .status(status)
                .requestedAt(requestedAt)
                .decidedAt(status == AccessRequestStatus.PENDING ? null : requestedAt.plus(1, ChronoUnit.DAYS))
                .expiresAt(status == AccessRequestStatus.APPROVED ? Instant.now().plus(180, ChronoUnit.DAYS) : null)
                .build();
    }

    private VoterChangeRequest change(User agent, Voter voter, Unit booth, VoterChangeType type, String reason) {
        return VoterChangeRequest.builder()
                .id(UUID.randomUUID())
                .changeType(type)
                .status(VoterChangeStatus.PENDING)
                .agent(agent)
                .voter(voter)
                .voterName(voter.getName())
                .booth(booth)
                .epicNo(type == VoterChangeType.EDIT ? voter.getEpicNo() : null)
                .name(type == VoterChangeType.EDIT ? voter.getName() : null)
                .relation(type == VoterChangeType.EDIT ? voter.getRelation() : null)
                .age(type == VoterChangeType.EDIT ? voter.getAge() + 1 : null)
                .gender(type == VoterChangeType.EDIT ? voter.getGender() : null)
                .wardNo(type == VoterChangeType.EDIT ? voter.getWardNo() : null)
                .reason(reason)
                .proposedAt(Instant.now().minus(2, ChronoUnit.DAYS))
                .build();
    }

    private VoterChangeRequest addChange(User agent, Unit booth) {
        return VoterChangeRequest.builder()
                .id(UUID.randomUUID())
                .changeType(VoterChangeType.ADD)
                .status(VoterChangeStatus.PENDING)
                .agent(agent)
                .booth(booth)
                .voterName("Kunja Bihari Sethi")
                .epicNo("ODA9911234")
                .name("Kunja Bihari Sethi")
                .relation("S/O Dasarathi Sethi")
                .houseNo("87")
                .age(34)
                .gender(Gender.M)
                .wardNo(9)
                .reason("New voter shifted into ward 9")
                .proposedAt(Instant.now().minus(2, ChronoUnit.DAYS))
                .build();
    }

    private String pick(String[] values) {
        return values[random.nextInt(values.length)];
    }
}
