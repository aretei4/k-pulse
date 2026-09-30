package com.kahga.pluse.sentiment.repository;

import static com.kahga.pluse.common.jdbc.JdbcSupport.enumValue;
import static com.kahga.pluse.common.jdbc.JdbcSupport.instant;
import static com.kahga.pluse.common.jdbc.JdbcSupport.integer;
import static com.kahga.pluse.common.jdbc.JdbcSupport.name;
import static com.kahga.pluse.common.jdbc.JdbcSupport.timestamp;
import static com.kahga.pluse.common.jdbc.JdbcSupport.uuid;

import com.kahga.pluse.candidate.repository.CandidateRepository;
import com.kahga.pluse.sentiment.entity.ConfidenceLevel;
import com.kahga.pluse.sentiment.entity.SentimentEntry;
import com.kahga.pluse.sentiment.entity.SentimentValue;
import com.kahga.pluse.user.repository.UserRepository;
import com.kahga.pluse.voter.entity.Voter;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class SentimentEntryRepository {

    private static final String SELECT = """
            SELECT e.id, e.voter_id, e.sentiment, e.confidence, e.resident, e.ward_no, e.recorded_at, e.updated_at,
                   %s,
                   %s
              FROM sentiment_entry e
              JOIN candidate c ON c.id = e.candidate_id
              JOIN app_user rb ON rb.id = e.recorded_by_id
            """.formatted(CandidateRepository.columns("c", "candidate_"), UserRepository.columns("rb", "recorded_by_"));

    private static final String INSERT = """
            INSERT INTO sentiment_entry
                   (id, voter_id, candidate_id, sentiment, confidence, resident, ward_no, recorded_by_id, recorded_at, updated_at)
            VALUES (:id, :voterId, :candidateId, :sentiment, :confidence, :resident, :wardNo, :recordedById, :recordedAt, :updatedAt)
            """;

    private static final String UPDATE = """
            UPDATE sentiment_entry
               SET voter_id = :voterId, candidate_id = :candidateId, sentiment = :sentiment, confidence = :confidence,
                   resident = :resident, ward_no = :wardNo, recorded_by_id = :recordedById,
                   recorded_at = :recordedAt, updated_at = :updatedAt
             WHERE id = :id
            """;

    private static final RowMapper<SentimentEntry> MAPPER = (rs, rowNum) -> SentimentEntry.builder()
            .id(uuid(rs, "id"))
            .voter(Voter.builder().id(uuid(rs, "voter_id")).build())
            .candidate(CandidateRepository.map(rs, "candidate_"))
            .sentiment(enumValue(rs, "sentiment", SentimentValue.class))
            .confidence(enumValue(rs, "confidence", ConfidenceLevel.class))
            .resident(rs.getBoolean("resident"))
            .wardNo(integer(rs, "ward_no"))
            .recordedBy(UserRepository.map(rs, "recorded_by_"))
            .recordedAt(instant(rs, "recorded_at"))
            .updatedAt(instant(rs, "updated_at"))
            .build();

    private final NamedParameterJdbcTemplate jdbc;

    public Optional<SentimentEntry> findByVoterIdAndCandidateId(UUID voterId, UUID candidateId) {
        return jdbc.query(
                        SELECT + " WHERE e.voter_id = :voterId AND e.candidate_id = :candidateId",
                        new MapSqlParameterSource().addValue("voterId", voterId).addValue("candidateId", candidateId),
                        MAPPER)
                .stream()
                .findFirst();
    }

    public Optional<SentimentEntry> findFirstByVoterIdOrderByUpdatedAtDesc(UUID voterId) {
        return jdbc.query(
                        SELECT + " WHERE e.voter_id = :voterId ORDER BY e.updated_at DESC LIMIT 1",
                        new MapSqlParameterSource("voterId", voterId),
                        MAPPER)
                .stream()
                .findFirst();
    }

    public List<SentimentEntry> findByVoterIdIn(Collection<UUID> voterIds) {
        if (voterIds.isEmpty()) {
            return List.of();
        }
        return jdbc.query(
                SELECT + " WHERE e.voter_id IN (:voterIds)", new MapSqlParameterSource("voterIds", voterIds), MAPPER);
    }

    public long count() {
        Long value = jdbc.queryForObject("SELECT COUNT(*) FROM sentiment_entry", new MapSqlParameterSource(), Long.class);
        return value == null ? 0 : value;
    }

    public SentimentEntry save(SentimentEntry entry) {
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("id", entry.getId())
                .addValue("voterId", entry.getVoter().getId())
                .addValue("candidateId", entry.getCandidate().getId())
                .addValue("sentiment", name(entry.getSentiment()))
                .addValue("confidence", name(entry.getConfidence()))
                .addValue("resident", entry.isResident())
                .addValue("wardNo", entry.getWardNo())
                .addValue("recordedById", entry.getRecordedBy().getId())
                .addValue("recordedAt", timestamp(entry.getRecordedAt()))
                .addValue("updatedAt", timestamp(entry.getUpdatedAt()));
        if (jdbc.update(UPDATE, params) == 0) {
            jdbc.update(INSERT, params);
        }
        return entry;
    }

    public List<SentimentEntry> saveAll(List<SentimentEntry> entries) {
        entries.forEach(this::save);
        return entries;
    }

    public long countByBoothId(UUID boothId) {
        Long value = jdbc.queryForObject(
                "SELECT COUNT(*) FROM sentiment_entry e JOIN voter v ON v.id = e.voter_id WHERE v.booth_id = :boothId",
                new MapSqlParameterSource("boothId", boothId),
                Long.class);
        return value == null ? 0 : value;
    }

    public long countByCandidateId(UUID candidateId) {
        Long value = jdbc.queryForObject(
                "SELECT COUNT(*) FROM sentiment_entry WHERE candidate_id = :candidateId",
                new MapSqlParameterSource("candidateId", candidateId),
                Long.class);
        return value == null ? 0 : value;
    }

    /** Everything this agent recorded, erased when their account deletion is approved. */
    public int deleteByRecordedById(UUID userId) {
        return jdbc.update(
                "DELETE FROM sentiment_entry WHERE recorded_by_id = :userId",
                new MapSqlParameterSource("userId", userId));
    }
}
