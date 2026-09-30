package com.kahga.pluse.location.repository;

import static com.kahga.pluse.common.jdbc.JdbcSupport.enumValue;
import static com.kahga.pluse.common.jdbc.JdbcSupport.name;
import static com.kahga.pluse.common.jdbc.JdbcSupport.uuid;

import com.kahga.pluse.common.jdbc.JdbcSupport;
import com.kahga.pluse.location.entity.Unit;
import com.kahga.pluse.location.entity.UnitLevel;
import java.sql.ResultSet;
import java.sql.SQLException;
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
public class UnitRepository {

    private static final String[] COLUMNS = {"id", "level", "name", "path", "parent_id"};

    private static final String SELECT = "SELECT " + columns("u", "") + " FROM unit u";

    private static final String INSERT =
            "INSERT INTO unit (id, level, name, path, parent_id) VALUES (:id, :level, :name, :path, :parentId)";

    private static final String UPDATE =
            "UPDATE unit SET level = :level, name = :name, path = :path, parent_id = :parentId WHERE id = :id";

    private static final RowMapper<Unit> MAPPER = (rs, rowNum) -> map(rs, "");

    private final NamedParameterJdbcTemplate jdbc;

    /** Column list for selecting {@code unit} joined into another query under {@code prefix}. */
    public static String columns(String alias, String prefix) {
        return JdbcSupport.columns(alias, prefix, COLUMNS);
    }

    public static Unit map(ResultSet rs, String prefix) throws SQLException {
        UUID parentId = uuid(rs, prefix + "parent_id");
        return Unit.builder()
                .id(uuid(rs, prefix + "id"))
                .level(enumValue(rs, prefix + "level", UnitLevel.class))
                .name(rs.getString(prefix + "name"))
                .path(rs.getString(prefix + "path"))
                .parent(parentId == null ? null : Unit.builder().id(parentId).build())
                .build();
    }

    public Optional<Unit> findById(UUID id) {
        return jdbc.query(SELECT + " WHERE u.id = :id", new MapSqlParameterSource("id", id), MAPPER).stream()
                .findFirst();
    }

    public List<Unit> findByLevelOrderByNameAsc(UnitLevel level) {
        return jdbc.query(
                SELECT + " WHERE u.level = :level ORDER BY u.name", new MapSqlParameterSource("level", level.name()), MAPPER);
    }

    public List<Unit> findByParentIdOrderByNameAsc(UUID parentId) {
        return jdbc.query(
                SELECT + " WHERE u.parent_id = :parentId ORDER BY u.name",
                new MapSqlParameterSource("parentId", parentId),
                MAPPER);
    }

    public List<Unit> findAllByOrderByPathAsc() {
        return jdbc.query(SELECT + " ORDER BY u.path", MAPPER);
    }

    public long count() {
        Long value = jdbc.queryForObject("SELECT COUNT(*) FROM unit", new MapSqlParameterSource(), Long.class);
        return value == null ? 0 : value;
    }

    public List<Unit> findByParentIds(Collection<UUID> parentIds) {
        if (parentIds.isEmpty()) {
            return List.of();
        }
        return jdbc.query(
                SELECT + " WHERE u.parent_id IN (:parentIds)", new MapSqlParameterSource("parentIds", parentIds), MAPPER);
    }

    public Unit save(Unit unit) {
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("id", unit.getId())
                .addValue("level", name(unit.getLevel()))
                .addValue("name", unit.getName())
                .addValue("path", unit.getPath())
                .addValue("parentId", unit.getParent() == null ? null : unit.getParent().getId());
        if (jdbc.update(UPDATE, params) == 0) {
            jdbc.update(INSERT, params);
        }
        return unit;
    }

    public long countByParentId(UUID parentId) {
        Long value = jdbc.queryForObject(
                "SELECT COUNT(*) FROM unit WHERE parent_id = :parentId",
                new MapSqlParameterSource("parentId", parentId),
                Long.class);
        return value == null ? 0 : value;
    }

    public void delete(UUID id) {
        jdbc.update("DELETE FROM unit WHERE id = :id", new MapSqlParameterSource("id", id));
    }
}
