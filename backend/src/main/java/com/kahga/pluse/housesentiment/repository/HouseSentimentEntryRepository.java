package com.kahga.pluse.housesentiment.repository;

import static com.kahga.pluse.common.jdbc.JdbcSupport.enumValue;
import static com.kahga.pluse.common.jdbc.JdbcSupport.instant;
import static com.kahga.pluse.common.jdbc.JdbcSupport.integer;
import static com.kahga.pluse.common.jdbc.JdbcSupport.name;
import static com.kahga.pluse.common.jdbc.JdbcSupport.timestamp;
import static com.kahga.pluse.common.jdbc.JdbcSupport.uuid;

import com.kahga.pluse.candidate.repository.CandidateRepository;
import com.kahga.pluse.electioncycle.entity.ElectionCycle;
import com.kahga.pluse.housesentiment.entity.HouseSentimentEntry;
import com.kahga.pluse.housesentiment.dto.HouseInsightsDto;
import com.kahga.pluse.location.entity.Unit;
import com.kahga.pluse.sentiment.entity.ConfidenceLevel;
import com.kahga.pluse.report.dto.ConfidenceSummaryDto;
import com.kahga.pluse.user.repository.UserRepository;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * House tallies. Nothing here joins {@code voter} — the table has no column to
 * join it on, which is the point of keeping this separate from
 * {@code sentiment_entry}.
 */
@Repository
@RequiredArgsConstructor
public class HouseSentimentEntryRepository {

    private static final String SELECT = """
            SELECT h.id, h.booth_id, h.house_no, h.house_name, h.ward_no, h.headcount, h.residential_count,
                   h.positive_count, h.neutral_count, h.negative_count, h.confidence, h.recorded_at, h.updated_at,
                   h.election_cycle_id,
                   %s,
                   %s
              FROM house_sentiment_entry h
              JOIN candidate c ON c.id = h.candidate_id
              JOIN app_user rb ON rb.id = h.recorded_by_id
            """.formatted(CandidateRepository.columns("c", "candidate_"), UserRepository.columns("rb", "recorded_by_"));

    private static final String INSERT = """
            INSERT INTO house_sentiment_entry
                   (id, booth_id, candidate_id, house_no, house_name, ward_no, headcount, residential_count,
                    positive_count, neutral_count, negative_count, confidence, recorded_by_id, recorded_at, updated_at,
                    election_cycle_id)
            VALUES (:id, :boothId, :candidateId, :houseNo, :houseName, :wardNo, :headcount, :residentialCount,
                    :positiveCount, :neutralCount, :negativeCount, :confidence, :recordedById, :recordedAt, :updatedAt,
                    :electionCycleId)
            """;

    private static final String UPDATE = """
            UPDATE house_sentiment_entry
               SET booth_id = :boothId, candidate_id = :candidateId, house_no = :houseNo, house_name = :houseName,
                   ward_no = :wardNo, headcount = :headcount, residential_count = :residentialCount,
                   positive_count = :positiveCount, neutral_count = :neutralCount, negative_count = :negativeCount,
                   confidence = :confidence, recorded_by_id = :recordedById, recorded_at = :recordedAt,
                   updated_at = :updatedAt, election_cycle_id = :electionCycleId
             WHERE id = :id
            """;

    /** The booth carries only its id: callers already hold the unit they asked about. */
    private static final RowMapper<HouseSentimentEntry> MAPPER = (rs, rowNum) -> HouseSentimentEntry.builder()
            .id(uuid(rs, "id"))
            .booth(Unit.builder().id(uuid(rs, "booth_id")).build())
            .candidate(CandidateRepository.map(rs, "candidate_"))
            .houseNo(rs.getString("house_no"))
            .houseName(rs.getString("house_name"))
            .wardNo(integer(rs, "ward_no"))
            .headcount(rs.getInt("headcount"))
            .residentialCount(rs.getInt("residential_count"))
            .positiveCount(rs.getInt("positive_count"))
            .neutralCount(rs.getInt("neutral_count"))
            .negativeCount(rs.getInt("negative_count"))
            .confidence(enumValue(rs, "confidence", ConfidenceLevel.class))
            .electionCycle(cycleOf(uuid(rs, "election_cycle_id")))
            .recordedBy(UserRepository.map(rs, "recorded_by_"))
            .recordedAt(instant(rs, "recorded_at"))
            .updatedAt(instant(rs, "updated_at"))
            .build();

    /** Only the id is read back; callers that need the cycle's name load it themselves. */
    private static ElectionCycle cycleOf(UUID id) {
        return id == null ? null : ElectionCycle.builder().id(id).build();
    }

    private final NamedParameterJdbcTemplate jdbc;

    /** Every agent working this booth sees the same list, so a house is not visited twice. */
    public List<HouseSentimentEntry> findByBoothAndCandidate(UUID boothId, UUID candidateId) {
        return jdbc.query(
                SELECT + " WHERE h.booth_id = :boothId AND h.candidate_id = :candidateId ORDER BY h.updated_at DESC",
                new MapSqlParameterSource().addValue("boothId", boothId).addValue("candidateId", candidateId),
                MAPPER);
    }

    public Optional<HouseSentimentEntry> findById(UUID id) {
        return jdbc.query(SELECT + " WHERE h.id = :id", new MapSqlParameterSource("id", id), MAPPER).stream()
                .findFirst();
    }

    public Optional<HouseSentimentEntry> findByHouse(UUID boothId, UUID candidateId, String houseNo, UUID cycleId) {
        return jdbc
                .query(
                        SELECT + " WHERE h.booth_id = :boothId AND h.candidate_id = :candidateId"
                                + " AND h.house_no = :houseNo AND h.election_cycle_id = :cycleId",
                        new MapSqlParameterSource()
                                .addValue("boothId", boothId)
                                .addValue("candidateId", candidateId)
                                .addValue("houseNo", houseNo)
                                .addValue("cycleId", cycleId),
                        MAPPER)
                .stream()
                .findFirst();
    }

    public long countByBoothId(UUID boothId) {
        Long count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM house_sentiment_entry WHERE booth_id = :boothId",
                new MapSqlParameterSource("boothId", boothId),
                Long.class);
        return count == null ? 0 : count;
    }

    public HouseSentimentEntry save(HouseSentimentEntry entry) {
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("id", entry.getId())
                .addValue("boothId", entry.getBooth().getId())
                .addValue("candidateId", entry.getCandidate().getId())
                .addValue("houseNo", entry.getHouseNo())
                .addValue("houseName", entry.getHouseName())
                .addValue("wardNo", entry.getWardNo())
                .addValue("headcount", entry.getHeadcount())
                .addValue("residentialCount", entry.getResidentialCount())
                .addValue("positiveCount", entry.getPositiveCount())
                .addValue("neutralCount", entry.getNeutralCount())
                .addValue("negativeCount", entry.getNegativeCount())
                .addValue("confidence", name(entry.getConfidence()))
                .addValue("recordedById", entry.getRecordedBy().getId())
                .addValue("recordedAt", timestamp(entry.getRecordedAt()))
                .addValue("updatedAt", timestamp(entry.getUpdatedAt()))
                .addValue(
                        "electionCycleId",
                        entry.getElectionCycle() == null ? null : entry.getElectionCycle().getId());
        if (jdbc.update(UPDATE, params) == 0) {
            jdbc.update(INSERT, params);
        }
        return entry;
    }

    /**
     * One row of totals across the given booths. Summed in SQL rather than in
     * Java so a campaign-long list of houses never has to be loaded to draw a
     * chart.
     */
    public HouseInsightsDto insights(Collection<UUID> boothIds, UUID candidateId) {
        if (boothIds.isEmpty()) {
            return HouseInsightsDto.empty();
        }
        StringBuilder sql = new StringBuilder("""
                SELECT COUNT(*) AS houses,
                       COALESCE(SUM(h.headcount), 0) AS people,
                       COALESCE(SUM(h.residential_count), 0) AS residents,
                       COALESCE(SUM(h.positive_count), 0) AS positive,
                       COALESCE(SUM(h.neutral_count), 0) AS neutral,
                       COALESCE(SUM(h.negative_count), 0) AS negative,
                       SUM(CASE WHEN h.confidence = 'HIGH' THEN 1 ELSE 0 END) AS high,
                       SUM(CASE WHEN h.confidence = 'MEDIUM' THEN 1 ELSE 0 END) AS medium,
                       SUM(CASE WHEN h.confidence = 'LOW' THEN 1 ELSE 0 END) AS low
                  FROM house_sentiment_entry h
                 WHERE h.booth_id IN (:boothIds)""");
        MapSqlParameterSource params = new MapSqlParameterSource("boothIds", boothIds);
        if (candidateId != null) {
            sql.append(" AND h.candidate_id = :candidateId");
            params.addValue("candidateId", candidateId);
        }
        return jdbc.queryForObject(sql.toString(), params, (rs, rowNum) -> new HouseInsightsDto(
                rs.getLong("houses"),
                rs.getLong("people"),
                rs.getLong("residents"),
                rs.getLong("positive"),
                rs.getLong("neutral"),
                rs.getLong("negative"),
                new ConfidenceSummaryDto(rs.getLong("high"), rs.getLong("medium"), rs.getLong("low"))));
    }

    public void delete(UUID id) {
        jdbc.update("DELETE FROM house_sentiment_entry WHERE id = :id", new MapSqlParameterSource("id", id));
    }

    /** Every house this agent tallied, erased when their account deletion is approved. */
    public int deleteByRecordedById(UUID userId) {
        return jdbc.update(
                "DELETE FROM house_sentiment_entry WHERE recorded_by_id = :userId",
                new MapSqlParameterSource("userId", userId));
    }
}
