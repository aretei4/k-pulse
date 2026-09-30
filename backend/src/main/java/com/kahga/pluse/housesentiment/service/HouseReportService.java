package com.kahga.pluse.housesentiment.service;

import com.kahga.pluse.common.jdbc.JdbcSupport;
import com.kahga.pluse.housesentiment.dto.HouseEntryRowDto;
import com.kahga.pluse.housesentiment.dto.HouseInsightsDto;
import com.kahga.pluse.housesentiment.dto.HouseReportFilter;
import com.kahga.pluse.housesentiment.dto.HouseReportResponse;
import com.kahga.pluse.housesentiment.dto.HouseUnitSummaryDto;
import com.kahga.pluse.location.entity.Unit;
import com.kahga.pluse.location.entity.UnitLevel;
import com.kahga.pluse.location.service.LocationService;
import com.kahga.pluse.user.service.AdminScopeService;
import com.kahga.pluse.report.dto.ConfidenceSummaryDto;
import com.kahga.pluse.sentiment.entity.ConfidenceLevel;
import org.springframework.jdbc.core.RowMapper;
import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumMap;
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
 * The admin's view of pre-election house tallies, rolled up from booths to
 * whichever level was asked for — the same shape as the named-voter report, but
 * counting households rather than roll entries.
 *
 * <p>There is no "not recorded" figure: nothing in the system says how many
 * houses a booth holds, so coverage can only be read as houses recorded.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class HouseReportService {

    /** Per-booth sums; every other level is these summed over the booths beneath it. */
    private record BoothTotals(long houses, long people, long residents, long positive, long neutral, long negative) {
        static final BoothTotals EMPTY = new BoothTotals(0, 0, 0, 0, 0, 0);
    }

    private final NamedParameterJdbcTemplate jdbc;
    private final LocationService locationService;
    private final AdminScopeService adminScopeService;

    public HouseReportResponse report(HouseReportFilter filter) {
        UnitLevel grouping = filter.levelOrDefault();
        Set<UUID> scope = narrowToCaller(filter.unitId() == null ? null : locationService.boothIdsUnder(filter.unitId()));

        Map<UUID, BoothTotals> byBooth = totalsByBooth(scope, filter);

        List<HouseUnitSummaryDto> rows = new ArrayList<>();
        long houses = 0;
        long people = 0;
        long residents = 0;
        long positive = 0;
        long neutral = 0;
        long negative = 0;

        for (Unit unit : locationService.find(grouping, null)) {
            Set<UUID> booths = locationService.boothIdsUnder(unit.getId());
            if (scope != null) {
                booths = booths.stream().filter(scope::contains).collect(Collectors.toSet());
            }
            if (booths.isEmpty()) {
                continue;
            }
            BoothTotals unitTotals = booths.stream()
                    .map(boothId -> byBooth.getOrDefault(boothId, BoothTotals.EMPTY))
                    .reduce(BoothTotals.EMPTY, HouseReportService::add);

            rows.add(new HouseUnitSummaryDto(
                    unit.getId(),
                    unit.getName(),
                    unit.getLevel(),
                    unitTotals.houses(),
                    unitTotals.people(),
                    unitTotals.residents(),
                    unitTotals.positive(),
                    unitTotals.neutral(),
                    unitTotals.negative()));

            // Totals are summed over the rows, not over every booth, so a unit
            // counted once here cannot be counted twice in the headline figures.
            houses += unitTotals.houses();
            people += unitTotals.people();
            residents += unitTotals.residents();
            positive += unitTotals.positive();
            neutral += unitTotals.neutral();
            negative += unitTotals.negative();
        }

        rows.sort((a, b) -> Long.compare(b.houses(), a.houses()));
        HouseInsightsDto totals = new HouseInsightsDto(
                houses, people, residents, positive, neutral, negative, confidenceCounts(scope, filter));
        return new HouseReportResponse(rows, totals);
    }

    /**
     * Every house recorded inside one unit, for the drill-down behind a row of
     * the report. Ordered as the roll is: house number numerically, so "4/A"
     * comes before "13".
     */
    public List<HouseEntryRowDto> entries(HouseReportFilter filter) {
        Set<UUID> scope = narrowToCaller(filter.unitId() == null ? null : locationService.boothIdsUnder(filter.unitId()));
        if (scope != null && scope.isEmpty()) {
            return List.of();
        }
        MapSqlParameterSource params = new MapSqlParameterSource();
        String sql = """
                SELECT h.id, h.house_no, h.house_name, h.ward_no, h.headcount, h.residential_count,
                       h.positive_count, h.neutral_count, h.negative_count, h.confidence,
                       h.recorded_at, h.updated_at,
                       b.id AS booth_id, b.name AS booth_name, b.path AS booth_path,
                       c.name AS candidate_name, rb.name AS recorded_by_name
                  FROM house_sentiment_entry h
                  JOIN unit b ON b.id = h.booth_id
                  JOIN candidate c ON c.id = h.candidate_id
                  JOIN app_user rb ON rb.id = h.recorded_by_id"""
                + where(scope, filter, params)
                + " ORDER BY CAST(NULLIF(REGEXP_REPLACE(h.house_no, '[^0-9].*$', ''), '') AS NUMERIC) NULLS LAST,"
                + " h.house_no, b.name";

        return jdbc.query(sql, params, ROW_MAPPER);
    }

    private static final RowMapper<HouseEntryRowDto> ROW_MAPPER = (rs, rowNum) -> new HouseEntryRowDto(
            JdbcSupport.uuid(rs, "id"),
            rs.getString("house_no"),
            rs.getString("house_name"),
            JdbcSupport.integer(rs, "ward_no"),
            rs.getInt("headcount"),
            rs.getInt("residential_count"),
            rs.getInt("positive_count"),
            rs.getInt("neutral_count"),
            rs.getInt("negative_count"),
            ConfidenceLevel.valueOf(rs.getString("confidence")),
            JdbcSupport.uuid(rs, "booth_id"),
            rs.getString("booth_name"),
            rs.getString("booth_path"),
            rs.getString("candidate_name"),
            rs.getString("recorded_by_name"),
            JdbcSupport.instant(rs, "recorded_at"),
            JdbcSupport.instant(rs, "updated_at"));

    /**
     * FR-A9: whatever unit was asked for, a scoped admin never sees past their
     * own booths. Intersecting here rather than trusting the request means a
     * tampered unitId can only ever narrow the answer.
     */
    private Set<UUID> narrowToCaller(Set<UUID> requested) {
        Set<UUID> allowed = adminScopeService.boothIds();
        if (allowed == null) {
            return requested;
        }
        if (requested == null) {
            return allowed;
        }
        return requested.stream().filter(allowed::contains).collect(Collectors.toSet());
    }

    private static BoothTotals add(BoothTotals a, BoothTotals b) {
        return new BoothTotals(
                a.houses() + b.houses(),
                a.people() + b.people(),
                a.residents() + b.residents(),
                a.positive() + b.positive(),
                a.neutral() + b.neutral(),
                a.negative() + b.negative());
    }

    private Map<UUID, BoothTotals> totalsByBooth(Collection<UUID> scope, HouseReportFilter filter) {
        Map<UUID, BoothTotals> result = new HashMap<>();
        if (scope != null && scope.isEmpty()) {
            return result;
        }
        MapSqlParameterSource params = new MapSqlParameterSource();
        String sql = """
                SELECT h.booth_id,
                       COUNT(*) AS houses,
                       COALESCE(SUM(h.headcount), 0) AS people,
                       COALESCE(SUM(h.residential_count), 0) AS residents,
                       COALESCE(SUM(h.positive_count), 0) AS positive,
                       COALESCE(SUM(h.neutral_count), 0) AS neutral,
                       COALESCE(SUM(h.negative_count), 0) AS negative
                  FROM house_sentiment_entry h"""
                + where(scope, filter, params)
                + " GROUP BY h.booth_id";

        jdbc.query(sql, params, (RowCallbackHandler) rs -> result.put(
                JdbcSupport.uuid(rs, "booth_id"),
                new BoothTotals(
                        rs.getLong("houses"),
                        rs.getLong("people"),
                        rs.getLong("residents"),
                        rs.getLong("positive"),
                        rs.getLong("neutral"),
                        rs.getLong("negative"))));
        return result;
    }

    private ConfidenceSummaryDto confidenceCounts(Collection<UUID> scope, HouseReportFilter filter) {
        Map<ConfidenceLevel, Long> counts = new EnumMap<>(ConfidenceLevel.class);
        if (scope == null || !scope.isEmpty()) {
            MapSqlParameterSource params = new MapSqlParameterSource();
            String sql = "SELECT h.confidence, COUNT(*) AS n FROM house_sentiment_entry h"
                    + where(scope, filter, params)
                    + " GROUP BY h.confidence";
            jdbc.query(sql, params, (RowCallbackHandler)
                    rs -> counts.put(ConfidenceLevel.valueOf(rs.getString("confidence")), rs.getLong("n")));
        }
        return new ConfidenceSummaryDto(
                counts.getOrDefault(ConfidenceLevel.HIGH, 0L),
                counts.getOrDefault(ConfidenceLevel.MEDIUM, 0L),
                counts.getOrDefault(ConfidenceLevel.LOW, 0L));
    }

    /** Callers handle an empty scope themselves, because {@code IN ()} is not valid SQL. */
    private String where(Collection<UUID> scope, HouseReportFilter filter, MapSqlParameterSource params) {
        List<String> clauses = new ArrayList<>();
        if (scope != null) {
            clauses.add("h.booth_id IN (:scope)");
            params.addValue("scope", scope);
        }
        if (filter.candidateId() != null) {
            clauses.add("h.candidate_id = :candidateId");
            params.addValue("candidateId", filter.candidateId());
        }
        if (filter.confidence() != null) {
            clauses.add("h.confidence = :confidence");
            params.addValue("confidence", filter.confidence().name());
        }
        // Dated by when the house was first recorded, as the named-voter report is.
        if (filter.from() != null) {
            clauses.add("h.recorded_at >= :from");
            params.addValue("from", JdbcSupport.timestamp(filter.from()));
        }
        if (filter.to() != null) {
            clauses.add("h.recorded_at <= :to");
            params.addValue("to", JdbcSupport.timestamp(filter.to()));
        }
        return clauses.isEmpty() ? "" : " WHERE " + String.join(" AND ", clauses);
    }
}
