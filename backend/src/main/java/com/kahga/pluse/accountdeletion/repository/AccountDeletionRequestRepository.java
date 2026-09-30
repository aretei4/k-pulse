package com.kahga.pluse.accountdeletion.repository;

import static com.kahga.pluse.common.jdbc.JdbcSupport.enumValue;
import static com.kahga.pluse.common.jdbc.JdbcSupport.instant;
import static com.kahga.pluse.common.jdbc.JdbcSupport.integer;
import static com.kahga.pluse.common.jdbc.JdbcSupport.name;
import static com.kahga.pluse.common.jdbc.JdbcSupport.timestamp;
import static com.kahga.pluse.common.jdbc.JdbcSupport.uuid;

import com.kahga.pluse.accountdeletion.entity.AccountDeletionRequest;
import com.kahga.pluse.accountdeletion.entity.DeletionStatus;
import com.kahga.pluse.user.entity.User;
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
public class AccountDeletionRequestRepository {

    /**
     * The reviewing admin is joined in, but the requesting agent is not: once a
     * request is approved that account no longer exists, so the copied name,
     * phone and email on the row are what the admin screen shows.
     */
    private static final String SELECT = """
            SELECT d.id, d.user_id, d.agent_name, d.agent_phone, d.agent_email, d.reason, d.status,
                   d.requested_at, d.reviewed_at, d.reviewer_note, d.deleted_entries,
                   rb.id AS reviewer_id, rb.name AS reviewer_name
              FROM account_deletion_request d
              LEFT JOIN app_user rb ON rb.id = d.reviewed_by_id
            """;

    private static final String INSERT = """
            INSERT INTO account_deletion_request
                   (id, user_id, agent_name, agent_phone, agent_email, reason, status, requested_at,
                    reviewed_at, reviewed_by_id, reviewer_note, deleted_entries)
            VALUES (:id, :userId, :agentName, :agentPhone, :agentEmail, :reason, :status, :requestedAt,
                    :reviewedAt, :reviewedById, :reviewerNote, :deletedEntries)
            """;

    private static final String UPDATE = """
            UPDATE account_deletion_request
               SET user_id = :userId, agent_name = :agentName, agent_phone = :agentPhone,
                   agent_email = :agentEmail, reason = :reason, status = :status, requested_at = :requestedAt,
                   reviewed_at = :reviewedAt, reviewed_by_id = :reviewedById, reviewer_note = :reviewerNote,
                   deleted_entries = :deletedEntries
             WHERE id = :id
            """;

    private static final RowMapper<AccountDeletionRequest> MAPPER = (rs, rowNum) -> {
        UUID userId = uuid(rs, "user_id");
        UUID reviewerId = uuid(rs, "reviewer_id");
        return AccountDeletionRequest.builder()
                .id(uuid(rs, "id"))
                .user(userId == null ? null : User.builder().id(userId).build())
                .agentName(rs.getString("agent_name"))
                .agentPhone(rs.getString("agent_phone"))
                .agentEmail(rs.getString("agent_email"))
                .reason(rs.getString("reason"))
                .status(enumValue(rs, "status", DeletionStatus.class))
                .requestedAt(instant(rs, "requested_at"))
                .reviewedAt(instant(rs, "reviewed_at"))
                .reviewedBy(reviewerId == null
                        ? null
                        : User.builder()
                                .id(reviewerId)
                                .name(rs.getString("reviewer_name"))
                                .build())
                .reviewerNote(rs.getString("reviewer_note"))
                .deletedEntries(integer(rs, "deleted_entries"))
                .build();
    };

    private final NamedParameterJdbcTemplate jdbc;

    public List<AccountDeletionRequest> findAllByOrderByRequestedAtDesc() {
        return jdbc.query(SELECT + " ORDER BY d.requested_at DESC", MAPPER);
    }

    public List<AccountDeletionRequest> findByStatus(DeletionStatus status) {
        return jdbc.query(
                SELECT + " WHERE d.status = :status ORDER BY d.requested_at DESC",
                new MapSqlParameterSource("status", status.name()),
                MAPPER);
    }

    public Optional<AccountDeletionRequest> findById(UUID id) {
        return jdbc.query(SELECT + " WHERE d.id = :id", new MapSqlParameterSource("id", id), MAPPER).stream()
                .findFirst();
    }

    /** Used to keep a second pending request from piling up for the same account. */
    public Optional<AccountDeletionRequest> findPendingByPhone(String phone) {
        return jdbc
                .query(
                        SELECT + " WHERE d.agent_phone = :phone AND d.status = 'PENDING'",
                        new MapSqlParameterSource("phone", phone),
                        MAPPER)
                .stream()
                .findFirst();
    }

    public long countByStatus(DeletionStatus status) {
        Long count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM account_deletion_request WHERE status = :status",
                new MapSqlParameterSource("status", status.name()),
                Long.class);
        return count == null ? 0 : count;
    }

    public AccountDeletionRequest save(AccountDeletionRequest request) {
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("id", request.getId())
                .addValue("userId", request.getUser() == null ? null : request.getUser().getId())
                .addValue("agentName", request.getAgentName())
                .addValue("agentPhone", request.getAgentPhone())
                .addValue("agentEmail", request.getAgentEmail())
                .addValue("reason", request.getReason())
                .addValue("status", name(request.getStatus()))
                .addValue("requestedAt", timestamp(request.getRequestedAt()))
                .addValue("reviewedAt", timestamp(request.getReviewedAt()))
                .addValue("reviewedById", request.getReviewedBy() == null ? null : request.getReviewedBy().getId())
                .addValue("reviewerNote", request.getReviewerNote())
                .addValue("deletedEntries", request.getDeletedEntries());
        if (jdbc.update(UPDATE, params) == 0) {
            jdbc.update(INSERT, params);
        }
        return request;
    }
}
