package com.kahga.pluse.voterexport.repository;

import static com.kahga.pluse.common.jdbc.JdbcSupport.enumValue;
import static com.kahga.pluse.common.jdbc.JdbcSupport.instant;

import com.kahga.pluse.sentiment.entity.SentimentValue;
import com.kahga.pluse.voter.entity.Gender;
import com.kahga.pluse.voter.repository.VoterRepository;
import com.kahga.pluse.voterexport.dto.ExportVoterRow;
import com.kahga.pluse.voterexport.dto.SentimentFilter;
import com.kahga.pluse.voterexport.dto.VoterExportSummaryDto;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * DL-N3: the one way voter rows reach a PDF. Every query here is clamped to a
 * single booth and a single candidate, both resolved from the agent's own live
 * grant before this is called — there is no parameter a phone could set to
 * widen it, and nothing here reads house-level sentiment (DL-11).
 */
@Repository
@RequiredArgsConstructor
public class VoterExportRepository {

    /**
     * The sentiment entry is joined, not sub-queried: the walk list prints the
     * recorded value and its date beside each name, and "not recorded" is then
     * simply the row that found no match.
     */
    private static final String FROM = """
             FROM voter v
             LEFT JOIN sentiment_entry e ON e.voter_id = v.id AND e.candidate_id = :candidateId
            WHERE v.booth_id = :boothId
            """;

    private static final RowMapper<ExportVoterRow> MAPPER = (rs, rowNum) -> {
        SentimentValue value = enumValue(rs, "sentiment", SentimentValue.class);
        return new ExportVoterRow(
                null,
                rs.getString("name"),
                rs.getString("relation"),
                rs.getString("house_no"),
                rs.getInt("age"),
                enumValue(rs, "gender", Gender.class),
                value == null ? null : SentimentFilter.valueOf(value.name()),
                instant(rs, "recorded_at"));
    };

    private final NamedParameterJdbcTemplate jdbc;

    /** Every chip's total for this booth, whatever the agent has currently chosen. */
    public VoterExportSummaryDto.Counts counts(UUID boothId, UUID candidateId) {
        String sql = """
                SELECT SUM(CASE WHEN e.sentiment = 'POSITIVE' THEN 1 ELSE 0 END) AS positive,
                       SUM(CASE WHEN e.sentiment = 'NEUTRAL' THEN 1 ELSE 0 END) AS neutral,
                       SUM(CASE WHEN e.sentiment = 'NEGATIVE' THEN 1 ELSE 0 END) AS negative,
                       SUM(CASE WHEN e.sentiment IS NULL THEN 1 ELSE 0 END) AS not_recorded
                """
                + FROM;
        return jdbc.queryForObject(
                sql,
                params(boothId, candidateId),
                (rs, rowNum) -> new VoterExportSummaryDto.Counts(
                        rs.getLong("positive"),
                        rs.getLong("neutral"),
                        rs.getLong("negative"),
                        rs.getLong("not_recorded")));
    }

    public long countAll(UUID boothId, UUID candidateId) {
        Long total = jdbc.queryForObject("SELECT COUNT(*)" + FROM, params(boothId, candidateId), Long.class);
        return total == null ? 0 : total;
    }

    /** How many voters the chosen chips hold — asked before a file is built (DL-10). */
    public long countMatching(UUID boothId, UUID candidateId, Set<SentimentFilter> chosen) {
        MapSqlParameterSource params = params(boothId, candidateId);
        Long total = jdbc.queryForObject(
                "SELECT COUNT(*)" + FROM + predicate(chosen, params), params, Long.class);
        return total == null ? 0 : total;
    }

    /**
     * The matching rows in roll order (DL-9), which is the voter list's own
     * order rather than a second one invented here.
     *
     * @param limit a hard ceiling, so a mistaken caller cannot pull a booth into memory unbounded
     */
    public List<ExportVoterRow> findMatching(
            UUID boothId, UUID candidateId, Set<SentimentFilter> chosen, int limit) {
        MapSqlParameterSource params = params(boothId, candidateId).addValue("limit", limit);
        String sql = "SELECT v.name, v.relation, v.house_no, v.age, v.gender, e.sentiment, e.recorded_at"
                + FROM
                + predicate(chosen, params)
                + VoterRepository.ORDER_BY
                + " LIMIT :limit";
        return jdbc.query(sql, params, MAPPER);
    }

    /**
     * The chips as SQL. Recorded values are one IN test and "not recorded" is the
     * missing join, OR-ed together: choosing all four is every voter in the booth.
     */
    private String predicate(Set<SentimentFilter> chosen, MapSqlParameterSource params) {
        List<String> parts = new ArrayList<>();
        List<String> values = chosen.stream()
                .filter(filter -> filter.value() != null)
                .map(filter -> filter.value().name())
                .toList();
        if (!values.isEmpty()) {
            parts.add("e.sentiment IN (:values)");
            params.addValue("values", values);
        }
        if (chosen.contains(SentimentFilter.NOT_RECORDED)) {
            parts.add("e.id IS NULL");
        }
        return parts.isEmpty() ? " AND 1 = 0" : " AND (" + String.join(" OR ", parts) + ")";
    }

    private MapSqlParameterSource params(UUID boothId, UUID candidateId) {
        return new MapSqlParameterSource().addValue("boothId", boothId).addValue("candidateId", candidateId);
    }
}
