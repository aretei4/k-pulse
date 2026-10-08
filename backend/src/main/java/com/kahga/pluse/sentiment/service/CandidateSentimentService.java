package com.kahga.pluse.sentiment.service;

import com.kahga.pluse.candidate.entity.Candidate;
import com.kahga.pluse.candidate.service.CandidateService;
import com.kahga.pluse.common.jdbc.JdbcSupport;
import com.kahga.pluse.location.entity.Unit;
import com.kahga.pluse.location.entity.UnitLevel;
import com.kahga.pluse.location.service.LocationService;
import com.kahga.pluse.sentiment.dto.CandidateSentimentDto;
import com.kahga.pluse.electioncycle.service.ElectionCycleService;
import com.kahga.pluse.sentiment.dto.CandidateSentimentFilter;
import com.kahga.pluse.sentiment.dto.CycleComparisonDto;
import com.kahga.pluse.sentiment.dto.SentimentSource;
import com.kahga.pluse.user.service.AdminScopeService;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.RowCallbackHandler;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * FR-A15: one candidate, seen from the admin's own area.
 *
 * <p>Two datasets feed it — named-voter entries, where one entry is one voter,
 * and house tallies, where the counts are already people. Adding them means a
 * house of six weighs six times a single voter, which is what "weighted by
 * number of people" asks for.
 *
 * <p>Every query is intersected with the caller's scope, so a drill-down into a
 * unit outside it returns nothing rather than someone else's numbers.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CandidateSentimentService {

    /** FR-A15 defaults; a super admin will be able to tune these when that setting lands. */
    private static final double SAFE_FROM_POINTS = 20;
    private static final double WATCH_FROM_POINTS = 5;

    /** Mutable tally used while rolling booths up; the DTO's Split is the immutable result. */
    private static final class Tally {
        long positive;
        long neutral;
        long negative;

        void add(long positive, long neutral, long negative) {
            this.positive += positive;
            this.neutral += neutral;
            this.negative += negative;
        }

        CandidateSentimentDto.Split toSplit() {
            return CandidateSentimentDto.Split.of(positive, neutral, negative);
        }
    }

    private final NamedParameterJdbcTemplate jdbc;
    private final LocationService locationService;
    private final CandidateService candidateService;
    private final AdminScopeService adminScopeService;
    private final ElectionCycleService electionCycleService;

    /**
     * Candidates with sentiment recorded inside the caller's area — the dropdown
     * on the screen. A panchayat admin should not be offered a candidate they
     * can see nothing about.
     */
    public List<Candidate> candidatesInScope() {
        Set<UUID> scope = adminScopeService.boothIds();
        if (scope != null && scope.isEmpty()) {
            return List.of();
        }
        MapSqlParameterSource params = new MapSqlParameterSource();
        String boothFilter = "";
        if (scope != null) {
            boothFilter = " WHERE booth_id IN (:scope)";
            params.addValue("scope", scope);
        }
        String sql = "SELECT DISTINCT candidate_id FROM ("
                + "SELECT v.booth_id, e.candidate_id FROM sentiment_entry e JOIN voter v ON v.id = e.voter_id"
                + " UNION ALL SELECT h.booth_id, h.candidate_id FROM house_sentiment_entry h) recorded"
                + boothFilter;

        Set<UUID> ids = Set.copyOf(jdbc.queryForList(sql, params, UUID.class));
        return candidateService.findAll().stream()
                .filter(candidate -> ids.contains(candidate.getId()))
                .toList();
    }

    public CandidateSentimentDto report(UUID candidateId, CandidateSentimentFilter filter) {
        Candidate candidate = candidateService.require(candidateId);

        // Where the drill-down currently is. With nothing asked for, start at the
        // panchayat this candidate contests: they stand in one place, so opening
        // on a district the admin then has to drill through twice is busywork.
        UUID requested = filter.parentUnitId();
        boolean startedAtCandidateUnit = false;
        if (requested == null) {
            UUID candidateUnitId = candidate.getUnit() == null ? null : candidate.getUnit().getId();
            if (candidateUnitId != null && withinCallerScope(candidateUnitId)) {
                requested = candidateUnitId;
                startedAtCandidateUnit = true;
            }
        }

        UUID parentUnitId = adminScopeService.resolveRequestedUnit(requested);
        Unit parentUnit = parentUnitId == null ? null : locationService.require(parentUnitId);
        // Landing on the candidate's own panchayat means booths, not one more level down.
        UnitLevel level = filter.level() != null
                ? filter.level()
                : startedAtCandidateUnit ? UnitLevel.BOOTH : nextLevelUnder(parentUnit);

        Set<UUID> scope = scopeBooths(parentUnitId);
        Map<UUID, Tally> byBooth = tallyByBooth(candidateId, scope, filter);

        List<CandidateSentimentDto.Row> rows = new ArrayList<>();
        Tally overall = new Tally();
        for (Unit unit : unitsAt(level, parentUnitId)) {
            Set<UUID> booths = locationService.boothIdsUnder(unit.getId()).stream()
                    .filter(scope::contains)
                    .collect(Collectors.toSet());
            if (booths.isEmpty()) {
                continue;
            }
            Tally unitTally = new Tally();
            for (UUID boothId : booths) {
                Tally booth = byBooth.get(boothId);
                if (booth != null) {
                    unitTally.add(booth.positive, booth.neutral, booth.negative);
                }
            }
            overall.add(unitTally.positive, unitTally.neutral, unitTally.negative);

            CandidateSentimentDto.Split split = unitTally.toSplit();
            rows.add(new CandidateSentimentDto.Row(
                    unit.getId(),
                    unit.getName(),
                    unit.getLevel(),
                    split,
                    split.netLead(),
                    split.status(SAFE_FROM_POINTS, WATCH_FROM_POINTS),
                    unit.getLevel() != UnitLevel.BOOTH));
        }

        Unit candidateUnit = candidate.getUnit() == null
                ? null
                : locationService.require(candidate.getUnit().getId());
        return new CandidateSentimentDto(
                candidate.getId(),
                candidate.getName(),
                candidateUnit == null ? null : candidateUnit.getId(),
                candidateUnit == null ? null : candidateUnit.getName(),
                level,
                parentUnitId,
                parentUnit == null ? null : parentUnit.getPath(),
                overall.toSplit(),
                rows);
    }

    /**
     * FR-A11: the same unit's standing in each cycle, newest first — "is this
     * booth better or worse than last time?" answered side by side rather than
     * by remembering what the old report said.
     */
    public List<CycleComparisonDto> acrossCycles(UUID candidateId, CandidateSentimentFilter filter) {
        List<CycleComparisonDto> out = new ArrayList<>();
        for (var cycle : electionCycleService.list()) {
            CandidateSentimentFilter forCycle = new CandidateSentimentFilter(
                    filter.level(), filter.parentUnitId(), filter.source(), cycle.getId(), filter.from(), filter.to());
            UUID parentUnitId = adminScopeService.resolveRequestedUnit(forCycle.parentUnitId());
            Set<UUID> scope = scopeBooths(parentUnitId);
            Tally tally = new Tally();
            tallyByBooth(candidateId, scope, forCycle).values().forEach(booth -> tally.add(booth.positive, booth.neutral, booth.negative));
            out.add(new CycleComparisonDto(
                    cycle.getId(), cycle.getName(), cycle.getYear(), cycle.getStatus(), tally.toSplit()));
        }
        return out;
    }

    /** A unit the caller may look at: their own scope, or anything inside it. */
    private boolean withinCallerScope(UUID unitId) {
        Set<UUID> allowed = adminScopeService.unitIds();
        return allowed == null || allowed.contains(unitId);
    }

    /** One level below where the admin is standing; booths stay at booths. */
    private UnitLevel nextLevelUnder(Unit parentUnit) {
        if (parentUnit == null) {
            return UnitLevel.DISTRICT;
        }
        return switch (parentUnit.getLevel()) {
            case DISTRICT -> UnitLevel.BLOCK;
            case BLOCK -> UnitLevel.PANCHAYAT;
            case PANCHAYAT, BOOTH -> UnitLevel.BOOTH;
        };
    }

    private List<Unit> unitsAt(UnitLevel level, UUID parentUnitId) {
        List<Unit> all = locationService.find(level, null);
        if (parentUnitId == null) {
            return all;
        }
        Set<UUID> under = locationService.idsAtOrUnder(parentUnitId);
        return all.stream().filter(unit -> under.contains(unit.getId())).toList();
    }

    /** Booths under the drill-down point, already intersected with the caller's scope. */
    private Set<UUID> scopeBooths(UUID parentUnitId) {
        Set<UUID> allowed = adminScopeService.boothIds();
        Set<UUID> here = parentUnitId == null ? null : locationService.boothIdsUnder(parentUnitId);
        if (here == null) {
            return allowed == null ? allBooths() : allowed;
        }
        return allowed == null ? here : here.stream().filter(allowed::contains).collect(Collectors.toSet());
    }

    private Set<UUID> allBooths() {
        return locationService.find(UnitLevel.BOOTH, null).stream().map(Unit::getId).collect(Collectors.toSet());
    }

    private Map<UUID, Tally> tallyByBooth(UUID candidateId, Collection<UUID> scope, CandidateSentimentFilter filter) {
        Map<UUID, Tally> result = new HashMap<>();
        if (scope.isEmpty()) {
            return result;
        }
        SentimentSource source = filter.sourceOrAll();
        if (source != SentimentSource.HOUSE) {
            addVoterEntries(result, candidateId, scope, filter);
        }
        if (source != SentimentSource.VOTER) {
            addHouseEntries(result, candidateId, scope, filter);
        }
        return result;
    }

    private void addVoterEntries(
            Map<UUID, Tally> into, UUID candidateId, Collection<UUID> scope, CandidateSentimentFilter filter) {

        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("candidateId", candidateId)
                .addValue("scope", scope);
        StringBuilder sql = new StringBuilder("SELECT v.booth_id, e.sentiment, COUNT(*) AS n"
                + " FROM sentiment_entry e JOIN voter v ON v.id = e.voter_id"
                + " WHERE e.candidate_id = :candidateId AND v.booth_id IN (:scope)");
        appendCycle(sql, "e.election_cycle_id", filter, params);
        appendDates(sql, "e.recorded_at", filter, params);
        sql.append(" GROUP BY v.booth_id, e.sentiment");

        jdbc.query(sql.toString(), params, (RowCallbackHandler) rs -> {
            Tally tally = into.computeIfAbsent(JdbcSupport.uuid(rs, "booth_id"), key -> new Tally());
            long n = rs.getLong("n");
            switch (rs.getString("sentiment")) {
                case "POSITIVE" -> tally.add(n, 0, 0);
                case "NEUTRAL" -> tally.add(0, n, 0);
                default -> tally.add(0, 0, n);
            }
        });
    }

    private void addHouseEntries(
            Map<UUID, Tally> into, UUID candidateId, Collection<UUID> scope, CandidateSentimentFilter filter) {

        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("candidateId", candidateId)
                .addValue("scope", scope);
        StringBuilder sql = new StringBuilder("SELECT h.booth_id,"
                + " COALESCE(SUM(h.positive_count), 0) AS positive,"
                + " COALESCE(SUM(h.neutral_count), 0) AS neutral,"
                + " COALESCE(SUM(h.negative_count), 0) AS negative"
                + " FROM house_sentiment_entry h"
                + " WHERE h.candidate_id = :candidateId AND h.booth_id IN (:scope)");
        appendCycle(sql, "h.election_cycle_id", filter, params);
        appendDates(sql, "h.recorded_at", filter, params);
        sql.append(" GROUP BY h.booth_id");

        jdbc.query(sql.toString(), params, (RowCallbackHandler) rs -> into.computeIfAbsent(
                        JdbcSupport.uuid(rs, "booth_id"), key -> new Tally())
                .add(rs.getLong("positive"), rs.getLong("neutral"), rs.getLong("negative")));
    }

    /** No cycle asked for means every cycle, which is what the old single-cycle view showed. */
    private void appendCycle(
            StringBuilder sql, String column, CandidateSentimentFilter filter, MapSqlParameterSource params) {
        if (filter.cycleId() != null) {
            sql.append(" AND ").append(column).append(" = :cycleId");
            params.addValue("cycleId", filter.cycleId());
        }
    }

    private void appendDates(
            StringBuilder sql, String column, CandidateSentimentFilter filter, MapSqlParameterSource params) {

        if (filter.from() != null) {
            sql.append(" AND ").append(column).append(" >= :from");
            params.addValue("from", JdbcSupport.timestamp(filter.from()));
        }
        if (filter.to() != null) {
            sql.append(" AND ").append(column).append(" <= :to");
            params.addValue("to", JdbcSupport.timestamp(filter.to()));
        }
    }
}
