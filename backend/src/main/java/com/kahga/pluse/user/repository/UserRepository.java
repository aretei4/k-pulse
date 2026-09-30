package com.kahga.pluse.user.repository;

import static com.kahga.pluse.common.jdbc.JdbcSupport.enumValue;
import static com.kahga.pluse.common.jdbc.JdbcSupport.instant;
import static com.kahga.pluse.common.jdbc.JdbcSupport.name;
import static com.kahga.pluse.common.jdbc.JdbcSupport.timestamp;
import static com.kahga.pluse.common.jdbc.JdbcSupport.uuid;

import com.kahga.pluse.common.jdbc.JdbcSupport;
import com.kahga.pluse.location.entity.Unit;
import com.kahga.pluse.location.entity.UnitLevel;
import com.kahga.pluse.user.entity.AccessScope;
import com.kahga.pluse.user.entity.Role;
import com.kahga.pluse.user.entity.User;
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
public class UserRepository {

    private static final String[] COLUMNS = {
        "id", "name", "email", "phone", "address", "password_hash", "role", "active", "created_at",
        "scope_level", "scope_unit_id"
    };

    private static final String SELECT = "SELECT " + columns("u", "") + " FROM app_user u";

    private static final String INSERT = """
            INSERT INTO app_user (id, name, email, phone, address, password_hash, role, active, created_at,
                                  scope_level, scope_unit_id)
            VALUES (:id, :name, :email, :phone, :address, :passwordHash, :role, :active, :createdAt,
                    :scopeLevel, :scopeUnitId)
            """;

    private static final String UPDATE = """
            UPDATE app_user
               SET name = :name, email = :email, phone = :phone, address = :address,
                   password_hash = :passwordHash, role = :role, active = :active, created_at = :createdAt,
                   scope_level = :scopeLevel, scope_unit_id = :scopeUnitId
             WHERE id = :id
            """;

    private static final RowMapper<User> MAPPER = (rs, rowNum) -> map(rs, "");

    private final NamedParameterJdbcTemplate jdbc;

    /** Column list for selecting {@code app_user} joined into another query under {@code prefix}. */
    public static String columns(String alias, String prefix) {
        return JdbcSupport.columns(alias, prefix, COLUMNS);
    }

    public static User map(ResultSet rs, String prefix) throws SQLException {
        UnitLevel scopeLevel = enumValue(rs, prefix + "scope_level", UnitLevel.class);
        UUID scopeUnitId = uuid(rs, prefix + "scope_unit_id");
        return User.builder()
                .id(uuid(rs, prefix + "id"))
                .name(rs.getString(prefix + "name"))
                .email(rs.getString(prefix + "email"))
                .phone(rs.getString(prefix + "phone"))
                .address(rs.getString(prefix + "address"))
                .passwordHash(rs.getString(prefix + "password_hash"))
                .role(enumValue(rs, prefix + "role", Role.class))
                .active(rs.getBoolean(prefix + "active"))
                .createdAt(instant(rs, prefix + "created_at"))
                .scope(scopeLevel == null && scopeUnitId == null
                        ? null
                        : AccessScope.builder()
                                .level(scopeLevel)
                                .unit(scopeUnitId == null ? null : Unit.builder().id(scopeUnitId).build())
                                .build())
                .build();
    }

    public Optional<User> findById(UUID id) {
        return first(SELECT + " WHERE u.id = :id", new MapSqlParameterSource("id", id));
    }

    public Optional<User> findByEmailIgnoreCase(String email) {
        return first(SELECT + " WHERE LOWER(u.email) = LOWER(:email)", new MapSqlParameterSource("email", email));
    }

    public Optional<User> findByPhone(String phone) {
        return first(SELECT + " WHERE u.phone = :phone", new MapSqlParameterSource("phone", phone));
    }

    public List<User> findByRoleOrderByNameAsc(Role role) {
        return jdbc.query(
                SELECT + " WHERE u.role = :role ORDER BY u.name", new MapSqlParameterSource("role", role.name()), MAPPER);
    }

    public List<User> findAllByOrderByNameAsc() {
        return jdbc.query(SELECT + " ORDER BY u.name", MAPPER);
    }

    public long countByRoleAndActiveTrue(Role role) {
        return count(
                "SELECT COUNT(*) FROM app_user WHERE role = :role AND active = true",
                new MapSqlParameterSource("role", role.name()));
    }

    public boolean existsByPhone(String phone) {
        return count("SELECT COUNT(*) FROM app_user WHERE phone = :phone", new MapSqlParameterSource("phone", phone)) > 0;
    }

    public boolean existsByEmailIgnoreCase(String email) {
        return count(
                        "SELECT COUNT(*) FROM app_user WHERE LOWER(email) = LOWER(:email)",
                        new MapSqlParameterSource("email", email))
                > 0;
    }

    public long count() {
        return count("SELECT COUNT(*) FROM app_user", new MapSqlParameterSource());
    }

    public User save(User user) {
        MapSqlParameterSource params = params(user);
        if (jdbc.update(UPDATE, params) == 0) {
            jdbc.update(INSERT, params);
        }
        return user;
    }

    public List<User> saveAll(List<User> users) {
        users.forEach(this::save);
        return users;
    }

    private Optional<User> first(String sql, MapSqlParameterSource params) {
        return jdbc.query(sql, params, MAPPER).stream().findFirst();
    }

    private long count(String sql, MapSqlParameterSource params) {
        Long value = jdbc.queryForObject(sql, params, Long.class);
        return value == null ? 0 : value;
    }

    private static MapSqlParameterSource params(User user) {
        return new MapSqlParameterSource()
                .addValue("id", user.getId())
                .addValue("name", user.getName())
                .addValue("email", user.getEmail())
                .addValue("phone", user.getPhone())
                .addValue("address", user.getAddress())
                .addValue("passwordHash", user.getPasswordHash())
                .addValue("role", name(user.getRole()))
                .addValue("active", user.isActive())
                .addValue("createdAt", timestamp(user.getCreatedAt()))
                .addValue("scopeLevel", user.getScope() == null ? null : name(user.getScope().getLevel()))
                .addValue(
                        "scopeUnitId",
                        user.getScope() == null || user.getScope().getUnit() == null
                                ? null
                                : user.getScope().getUnit().getId());
    }

    public void delete(UUID id) {
        jdbc.update("DELETE FROM app_user WHERE id = :id", new MapSqlParameterSource("id", id));
    }
}
