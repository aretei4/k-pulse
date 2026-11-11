package com.kahga.pluse.agentreport.repository;

import static com.kahga.pluse.common.jdbc.JdbcSupport.instant;
import static com.kahga.pluse.common.jdbc.JdbcSupport.timestamp;
import static com.kahga.pluse.common.jdbc.JdbcSupport.uuid;

import com.kahga.pluse.location.entity.Unit;
import com.kahga.pluse.sentiment.entity.ConfidenceLevel;
import com.kahga.pluse.sentiment.entity.SentimentValue;
import com.kahga.pluse.voter.entity.Caste;
import com.kahga.pluse.voter.entity.Gender;
import com.kahga.pluse.voter.repository.VoterRepository;
import com.kahga.pluse.voterexport.dto.ExportVoterRow;
import com.kahga.pluse.voterexport.dto.SentimentFilter;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.RowCallbackHandler;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * The figures behind an agent's report.
 *
 * <p>Every query here takes the booth ids the service has already worked out
 * from the agent's approved access requests. Nothing in this class can widen
 * that: there is no path from a panchayat id to a booth it does not own, and no
 * query that reads a booth not in the list it was handed (AR-5).
 *
 * <p>Ward is read from the voter, not from the entry. The two agree — recording
 * a ward at the door writes it through to the voter — and reading one column
 * keeps the roll count and the recorded count filtered the same way.
 */
@Repository
@RequiredArgsConstructor
public class AgentReportRepository {

    private final NamedParameterJdbcTemplate jdbc;

    /** Recorded entries per booth and sentiment, under the agent's choices. */
    public Map<UUID, Map<SentimentValue, Long>> sentimentByBooth(
            Collection<UUID> boothIds,
            UUID candidateId,
            Collection<ConfidenceLevel> confidence,
            Collection<Integer> wards,
            CasteChoice castes,
            Instant recordedSince) {

        Map<UUID, Map<SentimentValue, Long>> result = new HashMap<>();
        if (boothIds.isEmpty()) {
            return result;
        }
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("boothIds", boothIds)
                .addValue("candidateId", candidateId);

        StringBuilder sql = new StringBuilder("SELECT v.booth_id, e.sentiment, COUNT(*) AS n"
                + " FROM sentiment_entry e JOIN voter v ON v.id = e.voter_id"
                + " WHERE v.booth_id IN (:boothIds) AND e.candidate_id = :candidateId");
        appendChoices(sql, params, confidence, wards, castes, recordedSince);
        sql.append(" GROUP BY v.booth_id, e.sentiment");

        jdbc.query(sql.toString(), params, (RowCallbackHandler) rs -> result.computeIfAbsent(
                        uuid(rs, "booth_id"), key -> new EnumMap<>(SentimentValue.class))
                .put(SentimentValue.valueOf(rs.getString("sentiment")), rs.getLong("n")));
        return result;
    }

    /**
     * The roll of each booth and how much of it has been visited at all.
     *
     * <p>Deliberately not filtered by confidence or date: see AgentReportDto.
     */
    public Map<UUID, Roll> rollByBooth(
            Collection<UUID> boothIds, UUID candidateId, Collection<Integer> wards, CasteChoice castes) {
        Map<UUID, Roll> result = new HashMap<>();
        if (boothIds.isEmpty()) {
            return result;
        }
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("boothIds", boothIds)
                .addValue("candidateId", candidateId);

        StringBuilder sql = new StringBuilder("SELECT v.booth_id, COUNT(*) AS voters,"
                + " SUM(CASE WHEN EXISTS (SELECT 1 FROM sentiment_entry e"
                + "                        WHERE e.voter_id = v.id AND e.candidate_id = :candidateId)"
                + "          THEN 1 ELSE 0 END) AS recorded"
                + " FROM voter v WHERE v.booth_id IN (:boothIds)");
        if (wards != null && !wards.isEmpty()) {
            sql.append(" AND v.ward_no IN (:wards)");
            params.addValue("wards", wards);
        }
        appendCaste(sql, params, castes);
        sql.append(" GROUP BY v.booth_id");

        jdbc.query(sql.toString(), params, (RowCallbackHandler) rs ->
                result.put(uuid(rs, "booth_id"), new Roll(rs.getLong("voters"), rs.getLong("recorded"))));
        return result;
    }

    /**
     * The voters behind a drawn report, for the raw-data download (AG-9).
     *
     * <p>The same narrowing as the figures, so the file and the tiles can never
     * disagree (RA-N2), with one difference worth stating: a voter is in the
     * file when their sentiment is among the ticked ones, and "not recorded"
     * means no entry for this candidate at all.
     *
     * <p>No EPIC number, no caste and no "recorded by" column (AR-7) — the
     * query does not read them, so no later edit to a shared DTO can leak them
     * into an agent's file.
     */
    public List<ExportVoterRow> voterRows(
            Collection<UUID> boothIds,
            Map<UUID, Unit> booths,
            UUID candidateId,
            Collection<SentimentFilter> sentiments,
            Collection<ConfidenceLevel> confidence,
            Collection<Integer> wards,
            CasteChoice castes,
            Instant recordedSince,
            int limit) {

        if (boothIds.isEmpty() || sentiments.isEmpty()) {
            return List.of();
        }
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("boothIds", boothIds)
                .addValue("candidateId", candidateId)
                .addValue("limit", limit);

        StringBuilder sql = new StringBuilder("SELECT v.booth_id, v.name, v.relation, v.house_no, v.age, v.gender,"
                + " e.sentiment, e.recorded_at"
                + " FROM voter v"
                + " LEFT JOIN sentiment_entry e ON e.voter_id = v.id AND e.candidate_id = :candidateId");
        if (confidence != null && !confidence.isEmpty()) {
            sql.append(" AND e.confidence IN (:confidence)");
            params.addValue("confidence", confidence.stream().map(Enum::name).toList());
        }
        if (recordedSince != null) {
            sql.append(" AND e.updated_at >= :recordedSince");
            params.addValue("recordedSince", timestamp(recordedSince));
        }
        sql.append(" WHERE v.booth_id IN (:boothIds)");
        if (wards != null && !wards.isEmpty()) {
            sql.append(" AND v.ward_no IN (:wards)");
            params.addValue("wards", wards);
        }
        appendCaste(sql, params, castes);

        List<String> parts = new ArrayList<>();
        List<String> values = sentiments.stream()
                .filter(filter -> filter.value() != null)
                .map(filter -> filter.value().name())
                .toList();
        if (!values.isEmpty()) {
            sql.append("");
            parts.add("e.sentiment IN (:sentiments)");
            params.addValue("sentiments", values);
        }
        if (sentiments.contains(SentimentFilter.NOT_RECORDED)) {
            // No entry at all for this candidate, whatever the date or
            // confidence choice says — the same rule the tiles use.
            parts.add("NOT EXISTS (SELECT 1 FROM sentiment_entry x"
                    + " WHERE x.voter_id = v.id AND x.candidate_id = :candidateId)");
        }
        sql.append(" AND (").append(String.join(" OR ", parts)).append(")");
        sql.append(VoterRepository.ORDER_BY).append(" LIMIT :limit");

        return jdbc.query(sql.toString(), params, (rs, rowNum) -> {
            var value = rs.getString("sentiment");
            Unit booth = booths.get(uuid(rs, "booth_id"));
            return new ExportVoterRow(
                    booth == null ? null : booth.getName(),
                    rs.getString("name"),
                    rs.getString("relation"),
                    rs.getString("house_no"),
                    rs.getInt("age"),
                    rs.getString("gender") == null ? null : Gender.valueOf(rs.getString("gender")),
                    value == null ? null : SentimentFilter.valueOf(value),
                    instant(rs, "recorded_at"));
        });
    }

    /** The wards that occur in these booths, for the ward checkboxes (AG-3). */
    public List<Integer> wardsIn(Collection<UUID> boothIds) {
        if (boothIds.isEmpty()) {
            return List.of();
        }
        return jdbc.queryForList(
                "SELECT DISTINCT ward_no FROM voter WHERE booth_id IN (:boothIds) AND ward_no IS NOT NULL"
                        + " ORDER BY ward_no",
                new MapSqlParameterSource("boothIds", boothIds),
                Integer.class);
    }

    private void appendChoices(
            StringBuilder sql,
            MapSqlParameterSource params,
            Collection<ConfidenceLevel> confidence,
            Collection<Integer> wards,
            CasteChoice castes,
            Instant recordedSince) {

        if (confidence != null && !confidence.isEmpty()) {
            sql.append(" AND e.confidence IN (:confidence)");
            params.addValue("confidence", confidence.stream().map(Enum::name).toList());
        }
        if (wards != null && !wards.isEmpty()) {
            sql.append(" AND v.ward_no IN (:wards)");
            params.addValue("wards", wards);
        }
        if (recordedSince != null) {
            sql.append(" AND e.updated_at >= :recordedSince");
            params.addValue("recordedSince", timestamp(recordedSince));
        }
        appendCaste(sql, params, castes);
    }

    /**
     * The caste choice, applied to the voter rather than the entry: caste is
     * held on the person, so the roll and the recorded counts narrow together
     * and "not recorded" keeps meaning the same thing.
     */
    private void appendCaste(StringBuilder sql, MapSqlParameterSource params, CasteChoice castes) {
        if (castes == null || castes.coversEverything()) {
            return;
        }
        if (castes.castes().isEmpty()) {
            // Only "not recorded" is ticked.
            sql.append(" AND v.caste IS NULL");
            return;
        }
        sql.append(castes.includeUnrecorded()
                ? " AND (v.caste IN (:castes) OR v.caste IS NULL)"
                : " AND v.caste IN (:castes)");
        params.addValue("castes", castes.castes().stream().map(Enum::name).toList());
    }

    /**
     * What the agent ticked under Caste: some of the categories, and whether
     * voters with none recorded are among them.
     *
     * @param coversEverything true when every box is ticked, which is no filter
     *     at all and is left out of the SQL
     */
    public record CasteChoice(List<Caste> castes, boolean includeUnrecorded, boolean coversEverything) {

        public static CasteChoice of(Collection<String> chosen) {
            if (chosen == null || chosen.isEmpty()) {
                return new CasteChoice(List.of(), true, true);
            }
            boolean unrecorded = chosen.stream().anyMatch(value -> value.equalsIgnoreCase("NONE"));
            List<Caste> named = chosen.stream()
                    .filter(value -> !value.equalsIgnoreCase("NONE"))
                    .map(value -> Caste.valueOf(value.toUpperCase()))
                    .distinct()
                    .toList();
            boolean everything = unrecorded && named.size() == Caste.values().length;
            return new CasteChoice(named, unrecorded, everything);
        }
    }

    /** One booth's roll under the ward choice, and how many of those were visited. */
    public record Roll(long voters, long recorded) {

        public static final Roll EMPTY = new Roll(0, 0);
    }

    /** Convenience for callers that want the booths in a stable order. */
    public static List<UUID> ordered(Collection<UUID> boothIds) {
        return new ArrayList<>(boothIds);
    }
}
