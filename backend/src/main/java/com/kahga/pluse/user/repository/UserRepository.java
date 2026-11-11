package com.kahga.pluse.user.repository;

import static com.kahga.pluse.common.jdbc.JdbcSupport.enumValue;
import static com.kahga.pluse.common.jdbc.JdbcSupport.instant;
import static com.kahga.pluse.common.jdbc.JdbcSupport.integer;
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
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class UserRepository {

    private static final String[] COLUMNS = {
        "id", "name", "email", "phone", "address", "referral_no", "password_hash", "role", "active", "created_at",
        "scope_level", "scope_unit_id", "download_enabled", "agent_no",
        "must_change_password", "password_reset_requested_at", "login_updated_by_id", "login_updated_at",
        "reports_enabled", "reports_updated_by_id", "reports_updated_at"
    };

    private static final String SELECT = "SELECT " + columns("u", "") + " FROM app_user u";

    private static final String INSERT = """
            INSERT INTO app_user (id, name, email, phone, address, referral_no, password_hash, role, active,
                                  created_at,
                                  scope_level, scope_unit_id, download_enabled, agent_no,
                                  must_change_password, password_reset_requested_at,
                                  login_updated_by_id, login_updated_at,
                                  reports_enabled, reports_updated_by_id, reports_updated_at)
            VALUES (:id, :name, :email, :phone, :address, :referralNo, :passwordHash, :role, :active, :createdAt,
                    :scopeLevel, :scopeUnitId, :downloadEnabled, :agentNo,
                    :mustChangePassword, :passwordResetRequestedAt, :loginUpdatedBy, :loginUpdatedAt,
                    :reportsEnabled, :reportsUpdatedBy, :reportsUpdatedAt)
            """;

    private static final String UPDATE = """
            UPDATE app_user
               SET name = :name, email = :email, phone = :phone, address = :address, referral_no = :referralNo,
                   password_hash = :passwordHash, role = :role, active = :active, created_at = :createdAt,
                   scope_level = :scopeLevel, scope_unit_id = :scopeUnitId, download_enabled = :downloadEnabled,
                   agent_no = :agentNo, must_change_password = :mustChangePassword,
                   password_reset_requested_at = :passwordResetRequestedAt,
                   login_updated_by_id = :loginUpdatedBy, login_updated_at = :loginUpdatedAt,
                   reports_enabled = :reportsEnabled, reports_updated_by_id = :reportsUpdatedBy,
                   reports_updated_at = :reportsUpdatedAt
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
                .referralNo(rs.getString(prefix + "referral_no"))
                .passwordHash(rs.getString(prefix + "password_hash"))
                .role(enumValue(rs, prefix + "role", Role.class))
                .active(rs.getBoolean(prefix + "active"))
                .downloadEnabled(rs.getBoolean(prefix + "download_enabled"))
                .agentNo(integer(rs, prefix + "agent_no"))
                .mustChangePassword(rs.getBoolean(prefix + "must_change_password"))
                .passwordResetRequestedAt(instant(rs, prefix + "password_reset_requested_at"))
                .loginUpdatedBy(uuid(rs, prefix + "login_updated_by_id"))
                .loginUpdatedAt(instant(rs, prefix + "login_updated_at"))
                .reportsEnabled(rs.getBoolean(prefix + "reports_enabled"))
                .reportsUpdatedBy(uuid(rs, prefix + "reports_updated_by_id"))
                .reportsUpdatedAt(instant(rs, prefix + "reports_updated_at"))
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
        if (jdbc.update(UPDATE, params(user)) > 0) {
            return user;
        }
        return insertNumbering(user, 0);
    }

    /**
     * Inserts, giving a new field agent the next agent number (FR-A20).
     *
     * <p>Numbered here rather than at each of the three places an agent is
     * created -- admin, self sign-up and the demo seed -- because one of those
     * would eventually be added without it, and an agent with no id is one the
     * page cannot show or search.
     *
     * <p>Two sign-ups landing together can read the same maximum, and the
     * unique index then rejects the second. That is rare enough to answer by
     * taking the next number and trying again rather than by a sequence, which
     * would have to be created in both PostgreSQL and the H2 the tests use.
     */
    private User insertNumbering(User user, int attempt) {
        if (user.getRole() == Role.FIELD_AGENT && user.getAgentNo() == null) {
            user.setAgentNo(nextAgentNo());
        }
        try {
            jdbc.update(INSERT, params(user));
            return user;
        } catch (DuplicateKeyException clash) {
            if (attempt >= 3 || user.getRole() != Role.FIELD_AGENT) {
                throw clash;
            }
            user.setAgentNo(null);
            return insertNumbering(user, attempt + 1);
        }
    }

    private int nextAgentNo() {
        Integer highest = jdbc.queryForObject(
                "SELECT COALESCE(MAX(agent_no), 0) FROM app_user", new MapSqlParameterSource(), Integer.class);
        return (highest == null ? 0 : highest) + 1;
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
                .addValue("referralNo", user.getReferralNo())
                .addValue("passwordHash", user.getPasswordHash())
                .addValue("role", name(user.getRole()))
                .addValue("active", user.isActive())
                .addValue("createdAt", timestamp(user.getCreatedAt()))
                .addValue("downloadEnabled", user.isDownloadEnabled())
                .addValue("agentNo", user.getAgentNo())
                .addValue("mustChangePassword", user.isMustChangePassword())
                .addValue("passwordResetRequestedAt", timestamp(user.getPasswordResetRequestedAt()))
                .addValue("loginUpdatedBy", user.getLoginUpdatedBy())
                .addValue("loginUpdatedAt", timestamp(user.getLoginUpdatedAt()))
                .addValue("reportsEnabled", user.isReportsEnabled())
                .addValue("reportsUpdatedBy", user.getReportsUpdatedBy())
                .addValue("reportsUpdatedAt", timestamp(user.getReportsUpdatedAt()))
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
