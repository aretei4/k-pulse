package com.kahga.pluse.accessrequest.repository;

import static com.kahga.pluse.common.jdbc.JdbcSupport.enumValue;
import static com.kahga.pluse.common.jdbc.JdbcSupport.instant;
import static com.kahga.pluse.common.jdbc.JdbcSupport.name;
import static com.kahga.pluse.common.jdbc.JdbcSupport.timestamp;
import static com.kahga.pluse.common.jdbc.JdbcSupport.uuid;

import com.kahga.pluse.accessrequest.entity.AccessRequest;
import com.kahga.pluse.accessrequest.entity.AccessRequestStatus;
import com.kahga.pluse.candidate.repository.CandidateRepository;
import com.kahga.pluse.location.repository.UnitRepository;
import com.kahga.pluse.user.repository.UserRepository;
import java.time.Instant;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

/** Agent, unit and candidate are joined into every read: each DTO built from a request shows all three. */
@Repository
@RequiredArgsConstructor
public class AccessRequestRepository {

    private static final String SELECT = """
            SELECT r.id, r.status, r.requested_at, r.decided_at, r.expires_at, r.reviewer_note,
                   %s,
                   %s,
                   %s
              FROM access_request r
              JOIN app_user a ON a.id = r.agent_id
              JOIN unit u ON u.id = r.unit_id
              JOIN candidate c ON c.id = r.candidate_id
            """.formatted(
                    UserRepository.columns("a", "agent_"),
                    UnitRepository.columns("u", "unit_"),
                    CandidateRepository.columns("c", "candidate_"));

    private static final String INSERT = """
            INSERT INTO access_request
                   (id, agent_id, unit_id, candidate_id, status, requested_at, decided_at, expires_at, reviewer_note)
            VALUES (:id, :agentId, :unitId, :candidateId, :status, :requestedAt, :decidedAt, :expiresAt, :reviewerNote)
            """;

    private static final String UPDATE = """
            UPDATE access_request
               SET agent_id = :agentId, unit_id = :unitId, candidate_id = :candidateId, status = :status,
                   requested_at = :requestedAt, decided_at = :decidedAt, expires_at = :expiresAt,
                   reviewer_note = :reviewerNote
             WHERE id = :id
            """;

    private static final RowMapper<AccessRequest> MAPPER = (rs, rowNum) -> AccessRequest.builder()
            .id(uuid(rs, "id"))
            .agent(UserRepository.map(rs, "agent_"))
            .unit(UnitRepository.map(rs, "unit_"))
            .candidate(CandidateRepository.map(rs, "candidate_"))
            .status(enumValue(rs, "status", AccessRequestStatus.class))
            .requestedAt(instant(rs, "requested_at"))
            .decidedAt(instant(rs, "decided_at"))
            .expiresAt(instant(rs, "expires_at"))
            .reviewerNote(rs.getString("reviewer_note"))
            .build();

    private final NamedParameterJdbcTemplate jdbc;

    public List<AccessRequest> findAll() {
        return jdbc.query(SELECT, MAPPER);
    }

    public List<AccessRequest> findByStatusOrderByRequestedAtDesc(AccessRequestStatus status) {
        return jdbc.query(
                SELECT + " WHERE r.status = :status ORDER BY r.requested_at DESC",
                new MapSqlParameterSource("status", status.name()),
                MAPPER);
    }

    public List<AccessRequest> findAllByOrderByRequestedAtDesc() {
        return jdbc.query(SELECT + " ORDER BY r.requested_at DESC", MAPPER);
    }

    public List<AccessRequest> findByAgentIdOrderByRequestedAtDesc(UUID agentId) {
        return jdbc.query(
                SELECT + " WHERE r.agent_id = :agentId ORDER BY r.requested_at DESC",
                new MapSqlParameterSource("agentId", agentId),
                MAPPER);
    }

    public List<AccessRequest> findByAgentIdAndStatus(UUID agentId, AccessRequestStatus status) {
        return jdbc.query(
                SELECT + " WHERE r.agent_id = :agentId AND r.status = :status",
                new MapSqlParameterSource().addValue("agentId", agentId).addValue("status", status.name()),
                MAPPER);
    }

    public List<AccessRequest> findByStatusAndExpiresAtBefore(AccessRequestStatus status, Instant cutoff) {
        return jdbc.query(
                SELECT + " WHERE r.status = :status AND r.expires_at < :cutoff",
                new MapSqlParameterSource().addValue("status", status.name()).addValue("cutoff", timestamp(cutoff)),
                MAPPER);
    }

    public Optional<AccessRequest> findById(UUID id) {
        return jdbc.query(SELECT + " WHERE r.id = :id", new MapSqlParameterSource("id", id), MAPPER).stream()
                .findFirst();
    }

    public long countByStatus(AccessRequestStatus status) {
        Long value = jdbc.queryForObject(
                "SELECT COUNT(*) FROM access_request WHERE status = :status",
                new MapSqlParameterSource("status", status.name()),
                Long.class);
        return value == null ? 0 : value;
    }

    public boolean existsByAgentIdAndUnitIdAndCandidateIdAndStatus(
            UUID agentId, UUID unitId, UUID candidateId, AccessRequestStatus status) {
        Long value = jdbc.queryForObject(
                """
                SELECT COUNT(*) FROM access_request
                 WHERE agent_id = :agentId AND unit_id = :unitId AND candidate_id = :candidateId AND status = :status
                """,
                new MapSqlParameterSource()
                        .addValue("agentId", agentId)
                        .addValue("unitId", unitId)
                        .addValue("candidateId", candidateId)
                        .addValue("status", status.name()),
                Long.class);
        return value != null && value > 0;
    }

    public AccessRequest save(AccessRequest request) {
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("id", request.getId())
                .addValue("agentId", request.getAgent().getId())
                .addValue("unitId", request.getUnit().getId())
                .addValue("candidateId", request.getCandidate().getId())
                .addValue("status", name(request.getStatus()))
                .addValue("requestedAt", timestamp(request.getRequestedAt()))
                .addValue("decidedAt", timestamp(request.getDecidedAt()))
                .addValue("expiresAt", timestamp(request.getExpiresAt()))
                .addValue("reviewerNote", request.getReviewerNote());
        if (jdbc.update(UPDATE, params) == 0) {
            jdbc.update(INSERT, params);
        }
        return request;
    }

    public List<AccessRequest> saveAll(List<AccessRequest> requests) {
        requests.forEach(this::save);
        return requests;
    }

    public long countByUnitId(UUID unitId) {
        Long value = jdbc.queryForObject(
                "SELECT COUNT(*) FROM access_request WHERE unit_id = :unitId",
                new MapSqlParameterSource("unitId", unitId),
                Long.class);
        return value == null ? 0 : value;
    }

    public long countByCandidateId(UUID candidateId) {
        Long value = jdbc.queryForObject(
                "SELECT COUNT(*) FROM access_request WHERE candidate_id = :candidateId",
                new MapSqlParameterSource("candidateId", candidateId),
                Long.class);
        return value == null ? 0 : value;
    }

    /** This agent's access requests and grants, erased with their account. */
    public int deleteByAgentId(UUID agentId) {
        return jdbc.update(
                "DELETE FROM access_request WHERE agent_id = :agentId",
                new MapSqlParameterSource("agentId", agentId));
    }

    /**
     * Agents who have asked for access somewhere inside these units — which is
     * what makes a field agent "one of ours" to a scoped admin (FR-A7), since an
     * agent account itself carries no unit.
     */
    public Set<UUID> agentIdsForUnits(Collection<UUID> unitIds) {
        if (unitIds.isEmpty()) {
            return Set.of();
        }
        return new LinkedHashSet<>(jdbc.queryForList(
                "SELECT DISTINCT agent_id FROM access_request WHERE unit_id IN (:unitIds)",
                new MapSqlParameterSource("unitIds", unitIds),
                UUID.class));
    }
}
