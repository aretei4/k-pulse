package com.kahga.pluse.candidate.repository;

import static com.kahga.pluse.common.jdbc.JdbcSupport.uuid;

import com.kahga.pluse.candidate.entity.Candidate;
import com.kahga.pluse.common.jdbc.JdbcSupport;
import com.kahga.pluse.location.entity.Unit;
import com.kahga.pluse.location.repository.UnitRepository;
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
public class CandidateRepository {

    private static final String[] COLUMNS = {"id", "name", "party", "unit_id"};

    /**
     * The candidate's own reads join its panchayat so the name can be shown. The
     * join is left, because the mapping is optional.
     */
    private static final String SELECT = """
            SELECT %s,
                   %s
              FROM candidate c
              LEFT JOIN unit u ON u.id = c.unit_id
            """.formatted(columns("c", ""), UnitRepository.columns("u", "unit_"));

    private static final String INSERT =
            "INSERT INTO candidate (id, name, party, unit_id) VALUES (:id, :name, :party, :unitId)";

    private static final String UPDATE =
            "UPDATE candidate SET name = :name, party = :party, unit_id = :unitId WHERE id = :id";

    /** Fills in the joined panchayat, so only this repository's own queries can use it. */
    private static final RowMapper<Candidate> MAPPER = (rs, rowNum) -> {
        Candidate candidate = map(rs, "");
        if (candidate.getUnit() != null) {
            candidate.setUnit(UnitRepository.map(rs, "unit_"));
        }
        return candidate;
    };

    private final NamedParameterJdbcTemplate jdbc;

    /** Column list for selecting {@code candidate} joined into another query under {@code prefix}. */
    public static String columns(String alias, String prefix) {
        return JdbcSupport.columns(alias, prefix, COLUMNS);
    }

    /**
     * Maps a candidate wherever it appears. The panchayat carries only its id
     * here: the tables that join a candidate in do not also join its unit.
     */
    public static Candidate map(ResultSet rs, String prefix) throws SQLException {
        UUID unitId = uuid(rs, prefix + "unit_id");
        return Candidate.builder()
                .id(uuid(rs, prefix + "id"))
                .name(rs.getString(prefix + "name"))
                .party(rs.getString(prefix + "party"))
                .unit(unitId == null ? null : Unit.builder().id(unitId).build())
                .build();
    }

    public List<Candidate> findAllByOrderByNameAsc() {
        return jdbc.query(SELECT + " ORDER BY c.name", MAPPER);
    }

    public Optional<Candidate> findById(UUID id) {
        return jdbc.query(SELECT + " WHERE c.id = :id", new MapSqlParameterSource("id", id), MAPPER).stream()
                .findFirst();
    }

    public Candidate save(Candidate candidate) {
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("id", candidate.getId())
                .addValue("name", candidate.getName())
                .addValue("party", candidate.getParty())
                .addValue("unitId", candidate.getUnit() == null ? null : candidate.getUnit().getId());
        if (jdbc.update(UPDATE, params) == 0) {
            jdbc.update(INSERT, params);
        }
        return candidate;
    }

    public void delete(UUID id) {
        jdbc.update("DELETE FROM candidate WHERE id = :id", new MapSqlParameterSource("id", id));
    }
}
