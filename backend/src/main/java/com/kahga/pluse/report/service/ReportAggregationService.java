package com.kahga.pluse.report.service;

import com.kahga.pluse.common.jdbc.JdbcSupport;
import com.kahga.pluse.location.entity.Unit;
import com.kahga.pluse.location.entity.UnitLevel;
import com.kahga.pluse.location.service.LocationService;
import com.kahga.pluse.report.dto.ConfidenceSummaryDto;
import com.kahga.pluse.report.dto.ReportFilterRequest;
import com.kahga.pluse.report.dto.ReportResponse;
import com.kahga.pluse.report.dto.SentimentSummaryDto;
import com.kahga.pluse.sentiment.entity.ConfidenceLevel;
import com.kahga.pluse.sentiment.entity.SentimentValue;
import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.RowCallbackHandler;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Booth counts are the only thing the database aggregates; every other level is
 * those counts summed over the booths beneath it. One query per report, no
 * per-unit round trips.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ReportAggregationService {

    private final NamedParameterJdbcTemplate jdbc;
    private final LocationService locationService;

    public ReportResponse report(ReportFilterRequest filter) {
        return report(filter, null);
    }

    public ReportResponse report(ReportFilterRequest filter, Set<UUID> restrictToBooths) {
        UnitLevel level = filter.levelOrDefault();

        Set<UUID> scope = restrictToBooths;
        if (filter.unitId() != null) {
            Set<UUID> requested = locationService.boothIdsUnder(filter.unitId());
            if (scope == null) {
                scope = requested;
            } else {
                scope = scope.stream().filter(requested::contains).collect(java.util.stream.Collectors.toSet());
            }
        }

        Map<UUID, BoothTally> sentimentByBooth = sentimentCountsByBooth(filter, scope);
        Map<UUID, Long> votersByBooth = voterCountsByBooth(scope);

        List<SentimentSummaryDto> rows = new ArrayList<>();
        for (Unit unit : locationService.find(level, null)) {
            Set<UUID> booths = locationService.boothIdsUnder(unit.getId());
            if (scope != null) {
                booths = booths.stream().filter(scope::contains).collect(java.util.stream.Collectors.toSet());
            }
            if (booths.isEmpty()) {
                continue;
            }

            boolean residentsOnly = filter.residentsOnly();
            long positive = 0;
            long neutral = 0;
            long negative = 0;
            long voters = 0;
            long nonResident = 0;
            long everyEntry = 0;
            for (UUID boothId : booths) {
                BoothTally tally = sentimentByBooth.getOrDefault(boothId, BoothTally.EMPTY);
                positive += tally.count(SentimentValue.POSITIVE, residentsOnly);
                neutral += tally.count(SentimentValue.NEUTRAL, residentsOnly);
                negative += tally.count(SentimentValue.NEGATIVE, residentsOnly);
                nonResident += tally.awayCount();
                everyEntry += tally.recorded(false);
                voters += votersByBooth.getOrDefault(boothId, 0L);
            }
            // Left to do stays the roll minus everyone already visited, whether
            // or not they turned out to live there: leaving them out would put
            // voters back on the list the agent has finished with. The roll
            // itself loses them instead, so the four figures still add up.
            rows.add(new SentimentSummaryDto(
                    unit.getId(),
                    unit.getName(),
                    unit.getLevel(),
                    positive,
                    neutral,
                    negative,
                    Math.max(0, voters - everyEntry),
                    Math.max(0, voters - (residentsOnly ? nonResident : 0)),
                    nonResident));
        }

        return new ReportResponse(rows, confidenceCounts(filter, scope));
    }

    public long countEntries(ReportFilterRequest filter, Set<UUID> restrictToBooths) {
        if (restrictToBooths != null && restrictToBooths.isEmpty()) {
            return 0;
        }
        return sentimentCountsByBooth(filter, restrictToBooths).values().stream()
                .mapToLong(tally -> tally.recorded(filter.residentsOnly()))
                .sum();
    }

    /**
     * Both halves of every booth in one pass. Residency is grouped on rather
     * than filtered by, so one query answers the screen whichever way its
     * residence choice is set and still knows how many entries that choice
     * leaves out.
     */
    private Map<UUID, BoothTally> sentimentCountsByBooth(ReportFilterRequest filter, Collection<UUID> scope) {
        Map<UUID, BoothTally> result = new HashMap<>();
        if (scope != null && scope.isEmpty()) {
            return result;
        }
        MapSqlParameterSource params = new MapSqlParameterSource();
        String sql = "SELECT v.booth_id, e.sentiment, e.resident, COUNT(*) AS n"
                + " FROM sentiment_entry e JOIN voter v ON v.id = e.voter_id"
                + where(filter, scope, params, false)
                + " GROUP BY v.booth_id, e.sentiment, e.resident";

        jdbc.query(sql, params, (RowCallbackHandler) rs -> {
            result.computeIfAbsent(JdbcSupport.uuid(rs, "booth_id"), key -> BoothTally.fresh())
                    .side(rs.getBoolean("resident"))
                    .put(SentimentValue.valueOf(rs.getString("sentiment")), rs.getLong("n"));
        });
        return result;
    }

    /**
     * One booth's recorded entries, kept apart by whether the voter was found
     * living there.
     */
    private record BoothTally(Map<SentimentValue, Long> living, Map<SentimentValue, Long> away) {

        private static final BoothTally EMPTY = new BoothTally(Map.of(), Map.of());

        private static BoothTally fresh() {
            return new BoothTally(new EnumMap<>(SentimentValue.class), new EnumMap<>(SentimentValue.class));
        }

        private Map<SentimentValue, Long> side(boolean resident) {
            return resident ? living : away;
        }

        private long count(SentimentValue value, boolean residentsOnly) {
            return living.getOrDefault(value, 0L) + (residentsOnly ? 0 : away.getOrDefault(value, 0L));
        }

        private long recorded(boolean residentsOnly) {
            return sum(living) + (residentsOnly ? 0 : sum(away));
        }

        private long awayCount() {
            return sum(away);
        }

        private static long sum(Map<SentimentValue, Long> counts) {
            return counts.values().stream().mapToLong(Long::longValue).sum();
        }
    }

    private ConfidenceSummaryDto confidenceCounts(ReportFilterRequest filter, Collection<UUID> scope) {
        Map<ConfidenceLevel, Long> counts = new EnumMap<>(ConfidenceLevel.class);
        if (scope == null || !scope.isEmpty()) {
            MapSqlParameterSource params = new MapSqlParameterSource();
            String sql = "SELECT e.confidence, COUNT(*) AS n"
                    + " FROM sentiment_entry e JOIN voter v ON v.id = e.voter_id"
                    + where(filter, scope, params, filter.residentsOnly())
                    + " GROUP BY e.confidence";

            jdbc.query(sql, params, (RowCallbackHandler) rs -> {
                counts.put(ConfidenceLevel.valueOf(rs.getString("confidence")), rs.getLong("n"));
            });
        }
        return new ConfidenceSummaryDto(
                counts.getOrDefault(ConfidenceLevel.HIGH, 0L),
                counts.getOrDefault(ConfidenceLevel.MEDIUM, 0L),
                counts.getOrDefault(ConfidenceLevel.LOW, 0L));
    }

    private Map<UUID, Long> voterCountsByBooth(Collection<UUID> scope) {
        Map<UUID, Long> counts = new HashMap<>();
        if (scope != null && scope.isEmpty()) {
            return counts;
        }
        MapSqlParameterSource params = new MapSqlParameterSource();
        String sql = "SELECT v.booth_id, COUNT(*) AS n FROM voter v";
        if (scope != null) {
            sql += " WHERE v.booth_id IN (:scope)";
            params.addValue("scope", scope);
        }
        sql += " GROUP BY v.booth_id";

        jdbc.query(sql, params, (RowCallbackHandler) rs -> {
            counts.put(JdbcSupport.uuid(rs, "booth_id"), rs.getLong("n"));
        });
        return counts;
    }

    /**
     * The shared filter for both sentiment aggregates. Callers handle an empty
     * scope themselves, because {@code IN ()} is not valid SQL, and say whether
     * the residence choice belongs in the SQL at all: the counts query groups on
     * it instead, so only the confidence query narrows by it here.
     */
    private String where(
            ReportFilterRequest filter,
            Collection<UUID> scope,
            MapSqlParameterSource params,
            boolean residentsOnly) {

        List<String> clauses = new ArrayList<>();
        if (residentsOnly) {
            clauses.add("e.resident = TRUE");
        }
        if (filter.candidateId() != null) {
            clauses.add("e.candidate_id = :candidateId");
            params.addValue("candidateId", filter.candidateId());
        }
        if (filter.sentiment() != null) {
            clauses.add("e.sentiment = :sentiment");
            params.addValue("sentiment", filter.sentiment().name());
        }
        if (filter.confidence() != null) {
            clauses.add("e.confidence = :confidence");
            params.addValue("confidence", filter.confidence().name());
        }
        if (filter.from() != null) {
            clauses.add("e.recorded_at >= :from");
            params.addValue("from", JdbcSupport.timestamp(filter.from()));
        }
        if (filter.to() != null) {
            clauses.add("e.recorded_at <= :to");
            params.addValue("to", JdbcSupport.timestamp(filter.to()));
        }
        if (scope != null) {
            clauses.add("v.booth_id IN (:scope)");
            params.addValue("scope", scope);
        }
        return clauses.isEmpty() ? "" : " WHERE " + String.join(" AND ", clauses);
    }
}
