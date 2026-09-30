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

        Map<UUID, Map<SentimentValue, Long>> sentimentByBooth = sentimentCountsByBooth(filter, scope);
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

            long positive = 0;
            long neutral = 0;
            long negative = 0;
            long voters = 0;
            for (UUID boothId : booths) {
                Map<SentimentValue, Long> counts = sentimentByBooth.getOrDefault(boothId, Map.of());
                positive += counts.getOrDefault(SentimentValue.POSITIVE, 0L);
                neutral += counts.getOrDefault(SentimentValue.NEUTRAL, 0L);
                negative += counts.getOrDefault(SentimentValue.NEGATIVE, 0L);
                voters += votersByBooth.getOrDefault(boothId, 0L);
            }
            long recorded = positive + neutral + negative;
            rows.add(new SentimentSummaryDto(
                    unit.getId(),
                    unit.getName(),
                    unit.getLevel(),
                    positive,
                    neutral,
                    negative,
                    Math.max(0, voters - recorded),
                    voters));
        }

        return new ReportResponse(rows, confidenceCounts(filter, scope));
    }

    public long countEntries(ReportFilterRequest filter, Set<UUID> restrictToBooths) {
        if (restrictToBooths != null && restrictToBooths.isEmpty()) {
            return 0;
        }
        return sentimentCountsByBooth(filter, restrictToBooths).values().stream()
                .flatMap(counts -> counts.values().stream())
                .mapToLong(Long::longValue)
                .sum();
    }

    private Map<UUID, Map<SentimentValue, Long>> sentimentCountsByBooth(
            ReportFilterRequest filter, Collection<UUID> scope) {

        Map<UUID, Map<SentimentValue, Long>> result = new HashMap<>();
        if (scope != null && scope.isEmpty()) {
            return result;
        }
        MapSqlParameterSource params = new MapSqlParameterSource();
        String sql = "SELECT v.booth_id, e.sentiment, COUNT(*) AS n"
                + " FROM sentiment_entry e JOIN voter v ON v.id = e.voter_id"
                + where(filter, scope, params)
                + " GROUP BY v.booth_id, e.sentiment";

        jdbc.query(sql, params, (RowCallbackHandler) rs -> {
            result.computeIfAbsent(JdbcSupport.uuid(rs, "booth_id"), key -> new EnumMap<>(SentimentValue.class))
                    .put(SentimentValue.valueOf(rs.getString("sentiment")), rs.getLong("n"));
        });
        return result;
    }

    private ConfidenceSummaryDto confidenceCounts(ReportFilterRequest filter, Collection<UUID> scope) {
        Map<ConfidenceLevel, Long> counts = new EnumMap<>(ConfidenceLevel.class);
        if (scope == null || !scope.isEmpty()) {
            MapSqlParameterSource params = new MapSqlParameterSource();
            String sql = "SELECT e.confidence, COUNT(*) AS n"
                    + " FROM sentiment_entry e JOIN voter v ON v.id = e.voter_id"
                    + where(filter, scope, params)
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
     * scope themselves, because {@code IN ()} is not valid SQL.
     */
    private String where(ReportFilterRequest filter, Collection<UUID> scope, MapSqlParameterSource params) {
        List<String> clauses = new ArrayList<>();
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
