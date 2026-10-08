package com.kahga.pluse.electioncycle.repository;

import static com.kahga.pluse.common.jdbc.JdbcSupport.enumValue;
import static com.kahga.pluse.common.jdbc.JdbcSupport.instant;
import static com.kahga.pluse.common.jdbc.JdbcSupport.name;
import static com.kahga.pluse.common.jdbc.JdbcSupport.timestamp;
import static com.kahga.pluse.common.jdbc.JdbcSupport.uuid;

import com.kahga.pluse.common.jdbc.JdbcSupport;
import com.kahga.pluse.electioncycle.entity.CycleStatus;
import com.kahga.pluse.electioncycle.entity.ElectionCycle;
import com.kahga.pluse.electioncycle.entity.ElectionType;
import java.sql.ResultSet;
import java.sql.SQLException;
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
public class ElectionCycleRepository {

    private static final String[] COLUMNS = {
        "id", "name", "election_type", "election_year", "status", "created_at", "closed_at"
    };

    private static final String SELECT = "SELECT " + columns("c", "") + " FROM election_cycle c";

    private static final String INSERT = """
            INSERT INTO election_cycle (id, name, election_type, election_year, status, created_at, closed_at)
            VALUES (:id, :name, :electionType, :electionYear, :status, :createdAt, :closedAt)
            """;

    private static final String UPDATE = """
            UPDATE election_cycle
               SET name = :name, election_type = :electionType, election_year = :electionYear, status = :status,
                   created_at = :createdAt, closed_at = :closedAt
             WHERE id = :id
            """;

    private static final RowMapper<ElectionCycle> MAPPER = (rs, rowNum) -> map(rs, "");

    private final NamedParameterJdbcTemplate jdbc;

    public static String columns(String alias, String prefix) {
        return JdbcSupport.columns(alias, prefix, COLUMNS);
    }

    public static ElectionCycle map(ResultSet rs, String prefix) throws SQLException {
        return ElectionCycle.builder()
                .id(uuid(rs, prefix + "id"))
                .name(rs.getString(prefix + "name"))
                .electionType(enumValue(rs, prefix + "election_type", ElectionType.class))
                .year(rs.getInt(prefix + "election_year"))
                .status(enumValue(rs, prefix + "status", CycleStatus.class))
                .createdAt(instant(rs, prefix + "created_at"))
                .closedAt(instant(rs, prefix + "closed_at"))
                .build();
    }

    /** Newest first: the cycle an admin most likely wants is the one at the top. */
    public List<ElectionCycle> findAll() {
        return jdbc.query(SELECT + " ORDER BY c.election_year DESC, c.created_at DESC", MAPPER);
    }

    public Optional<ElectionCycle> findById(UUID id) {
        return jdbc.query(SELECT + " WHERE c.id = :id", new MapSqlParameterSource("id", id), MAPPER).stream()
                .findFirst();
    }

    /** The cycle new sentiment goes into. Only one is open at a time. */
    public Optional<ElectionCycle> findOpen() {
        return jdbc.query(SELECT + " WHERE c.status = :open ORDER BY c.created_at DESC",
                        new MapSqlParameterSource("open", CycleStatus.OPEN.name()), MAPPER)
                .stream()
                .findFirst();
    }

    public ElectionCycle save(ElectionCycle cycle) {
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("id", cycle.getId())
                .addValue("name", cycle.getName())
                .addValue("electionType", name(cycle.getElectionType()))
                .addValue("electionYear", cycle.getYear())
                .addValue("status", name(cycle.getStatus()))
                .addValue("createdAt", timestamp(cycle.getCreatedAt()))
                .addValue("closedAt", timestamp(cycle.getClosedAt()));
        if (jdbc.update(UPDATE, params) == 0) {
            jdbc.update(INSERT, params);
        }
        return cycle;
    }
}
