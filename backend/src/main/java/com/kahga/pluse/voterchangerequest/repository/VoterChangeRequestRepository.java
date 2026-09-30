package com.kahga.pluse.voterchangerequest.repository;

import static com.kahga.pluse.common.jdbc.JdbcSupport.enumValue;
import static com.kahga.pluse.common.jdbc.JdbcSupport.instant;
import static com.kahga.pluse.common.jdbc.JdbcSupport.integer;
import static com.kahga.pluse.common.jdbc.JdbcSupport.name;
import static com.kahga.pluse.common.jdbc.JdbcSupport.timestamp;
import static com.kahga.pluse.common.jdbc.JdbcSupport.uuid;

import com.kahga.pluse.location.repository.UnitRepository;
import com.kahga.pluse.user.repository.UserRepository;
import com.kahga.pluse.voter.entity.Gender;
import com.kahga.pluse.voter.entity.Voter;
import com.kahga.pluse.voterchangerequest.entity.VoterChangeRequest;
import com.kahga.pluse.voterchangerequest.entity.VoterChangeStatus;
import com.kahga.pluse.voterchangerequest.entity.VoterChangeType;
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
public class VoterChangeRequestRepository {

    private static final String SELECT = """
            SELECT x.id, x.change_type, x.status, x.voter_id, x.voter_name, x.epic_no, x.name, x.relation,
                   x.house_no, x.age, x.gender, x.ward_no, x.reason, x.proposed_at, x.decided_at, x.reviewer_note,
                   %s,
                   %s
              FROM voter_change_request x
              JOIN app_user a ON a.id = x.agent_id
              JOIN unit b ON b.id = x.booth_id
            """.formatted(UserRepository.columns("a", "agent_"), UnitRepository.columns("b", "booth_"));

    private static final String INSERT = """
            INSERT INTO voter_change_request
                   (id, change_type, status, agent_id, voter_id, voter_name, booth_id, epic_no, name, relation,
                    house_no, age, gender, ward_no, reason, proposed_at, decided_at, reviewer_note)
            VALUES (:id, :changeType, :status, :agentId, :voterId, :voterName, :boothId, :epicNo, :name, :relation,
                    :houseNo, :age, :gender, :wardNo, :reason, :proposedAt, :decidedAt, :reviewerNote)
            """;

    private static final String UPDATE = """
            UPDATE voter_change_request
               SET change_type = :changeType, status = :status, agent_id = :agentId, voter_id = :voterId,
                   voter_name = :voterName, booth_id = :boothId, epic_no = :epicNo, name = :name,
                   relation = :relation, house_no = :houseNo, age = :age, gender = :gender, ward_no = :wardNo,
                   reason = :reason, proposed_at = :proposedAt, decided_at = :decidedAt, reviewer_note = :reviewerNote
             WHERE id = :id
            """;

    private static final RowMapper<VoterChangeRequest> MAPPER = (rs, rowNum) -> {
        UUID voterId = uuid(rs, "voter_id");
        return VoterChangeRequest.builder()
                .id(uuid(rs, "id"))
                .changeType(enumValue(rs, "change_type", VoterChangeType.class))
                .status(enumValue(rs, "status", VoterChangeStatus.class))
                .agent(UserRepository.map(rs, "agent_"))
                .voter(voterId == null ? null : Voter.builder().id(voterId).build())
                .voterName(rs.getString("voter_name"))
                .booth(UnitRepository.map(rs, "booth_"))
                .epicNo(rs.getString("epic_no"))
                .name(rs.getString("name"))
                .relation(rs.getString("relation"))
                .houseNo(rs.getString("house_no"))
                .age(integer(rs, "age"))
                .gender(enumValue(rs, "gender", Gender.class))
                .wardNo(integer(rs, "ward_no"))
                .reason(rs.getString("reason"))
                .proposedAt(instant(rs, "proposed_at"))
                .decidedAt(instant(rs, "decided_at"))
                .reviewerNote(rs.getString("reviewer_note"))
                .build();
    };

    private final NamedParameterJdbcTemplate jdbc;

    public List<VoterChangeRequest> findByStatusOrderByProposedAtDesc(VoterChangeStatus status) {
        return jdbc.query(
                SELECT + " WHERE x.status = :status ORDER BY x.proposed_at DESC",
                new MapSqlParameterSource("status", status.name()),
                MAPPER);
    }

    public List<VoterChangeRequest> findAllByOrderByProposedAtDesc() {
        return jdbc.query(SELECT + " ORDER BY x.proposed_at DESC", MAPPER);
    }

    public List<VoterChangeRequest> findByAgentIdOrderByProposedAtDesc(UUID agentId) {
        return jdbc.query(
                SELECT + " WHERE x.agent_id = :agentId ORDER BY x.proposed_at DESC",
                new MapSqlParameterSource("agentId", agentId),
                MAPPER);
    }

    public Optional<VoterChangeRequest> findById(UUID id) {
        return jdbc.query(SELECT + " WHERE x.id = :id", new MapSqlParameterSource("id", id), MAPPER).stream()
                .findFirst();
    }

    public long countByStatus(VoterChangeStatus status) {
        Long value = jdbc.queryForObject(
                "SELECT COUNT(*) FROM voter_change_request WHERE status = :status",
                new MapSqlParameterSource("status", status.name()),
                Long.class);
        return value == null ? 0 : value;
    }

    public VoterChangeRequest save(VoterChangeRequest change) {
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("id", change.getId())
                .addValue("changeType", name(change.getChangeType()))
                .addValue("status", name(change.getStatus()))
                .addValue("agentId", change.getAgent().getId())
                .addValue("voterId", change.getVoter() == null ? null : change.getVoter().getId())
                .addValue("voterName", change.getVoterName())
                .addValue("boothId", change.getBooth().getId())
                .addValue("epicNo", change.getEpicNo())
                .addValue("name", change.getName())
                .addValue("relation", change.getRelation())
                .addValue("houseNo", change.getHouseNo())
                .addValue("age", change.getAge())
                .addValue("gender", name(change.getGender()))
                .addValue("wardNo", change.getWardNo())
                .addValue("reason", change.getReason())
                .addValue("proposedAt", timestamp(change.getProposedAt()))
                .addValue("decidedAt", timestamp(change.getDecidedAt()))
                .addValue("reviewerNote", change.getReviewerNote());
        if (jdbc.update(UPDATE, params) == 0) {
            jdbc.update(INSERT, params);
        }
        return change;
    }

    public List<VoterChangeRequest> saveAll(List<VoterChangeRequest> changes) {
        changes.forEach(this::save);
        return changes;
    }

    public long countByBoothId(UUID boothId) {
        Long value = jdbc.queryForObject(
                "SELECT COUNT(*) FROM voter_change_request WHERE booth_id = :boothId",
                new MapSqlParameterSource("boothId", boothId),
                Long.class);
        return value == null ? 0 : value;
    }

    /** This agent's proposals, erased with their account. Approved ones already moved the roll. */
    public int deleteByAgentId(UUID agentId) {
        return jdbc.update(
                "DELETE FROM voter_change_request WHERE agent_id = :agentId",
                new MapSqlParameterSource("agentId", agentId));
    }
}
