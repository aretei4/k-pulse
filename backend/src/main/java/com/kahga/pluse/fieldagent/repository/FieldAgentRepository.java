package com.kahga.pluse.fieldagent.repository;

import static com.kahga.pluse.common.jdbc.JdbcSupport.enumValue;
import static com.kahga.pluse.common.jdbc.JdbcSupport.instant;
import static com.kahga.pluse.common.jdbc.JdbcSupport.name;
import static com.kahga.pluse.common.jdbc.JdbcSupport.timestamp;
import static com.kahga.pluse.common.jdbc.JdbcSupport.uuid;

import com.kahga.pluse.fieldagent.dto.AgentAccessState;
import com.kahga.pluse.fieldagent.dto.FieldAgentDto;
import com.kahga.pluse.fieldagent.dto.FieldAgentFilter;
import com.kahga.pluse.fieldagent.dto.MarkPaymentRequest;
import com.kahga.pluse.fieldagent.dto.PaymentMode;
import java.sql.Date;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.RowCallbackHandler;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * The Field agents page, in SQL (FA-N2: every filter, sort and count is done
 * here, never in the browser).
 *
 * <p>Two things are worth knowing before reading the query. An agent may hold
 * several grants, so one is picked per agent to fill the Area, Access and
 * Candidate columns — the live one, else the pending one, else the most recent
 * — while the area <em>filter</em> matches any grant they hold: narrowing to a
 * block should not hide an agent because their newest grant is elsewhere.
 *
 * <p>And "Access" is a state of the agent, not of a request, so it is derived
 * in the select and filtered on in an outer query. A derived table rather than
 * repeating the CASE in the where clause: the expiry rule is written once.
 */
@Repository
@RequiredArgsConstructor
public class FieldAgentRepository {

    /**
     * Each agent's work, in one pass. Also answers "last active", which is the
     * same aggregate read a second way.
     */
    private static final String ACTIVITY =
            """
            LEFT JOIN (SELECT recorded_by_id AS agent_id, COUNT(*) AS n, MAX(updated_at) AS last_active
                         FROM sentiment_entry
                        GROUP BY recorded_by_id) act ON act.agent_id = u.id
            """;

    /**
     * The one grant that describes the agent today. Live first, then pending,
     * then whatever was decided last: an expired grant still says which area the
     * agent worked, which is what an admin looking for them needs.
     */
    private static final String CURRENT_GRANT =
            """
            LEFT JOIN (SELECT ar.agent_id, ar.unit_id, ar.candidate_id, ar.status, ar.expires_at,
                              ROW_NUMBER() OVER (
                                  PARTITION BY ar.agent_id
                                  ORDER BY CASE
                                               WHEN ar.status = 'APPROVED'
                                                    AND (ar.expires_at IS NULL OR ar.expires_at > :now) THEN 0
                                               WHEN ar.status = 'PENDING' THEN 1
                                               WHEN ar.status = 'APPROVED' THEN 2
                                               ELSE 3
                                           END,
                                           ar.requested_at DESC) AS rn
                         FROM access_request ar) g ON g.agent_id = u.id AND g.rn = 1
            """;

    private static final String ACCESS_STATE =
            """
            CASE WHEN g.agent_id IS NULL THEN 'NONE'
                 WHEN g.status = 'APPROVED' AND (g.expires_at IS NULL OR g.expires_at > :now) THEN 'APPROVED'
                 WHEN g.status = 'PENDING' THEN 'PENDING'
                 WHEN g.status = 'APPROVED' THEN 'EXPIRED'
                 ELSE 'NONE'
            END AS access_state
            """;

    /** The readable agent id, built in SQL so searching on it is one condition. */
    private static final String AGENT_CODE = "'A-' || LPAD(CAST(u.agent_no AS varchar), 5, '0')";

    private static final String BODY =
            """
            FROM app_user u
            """
                    + ACTIVITY
                    + CURRENT_GRANT
                    + """
            LEFT JOIN unit gu ON gu.id = g.unit_id
            LEFT JOIN candidate cand ON cand.id = g.candidate_id
            LEFT JOIN app_user pu ON pu.id = u.paid_updated_by_id
            """;

    private static final String FIELDS =
            """
            SELECT u.id AS id, u.agent_no AS agent_no, u.name AS name, u.phone AS phone, u.email AS email,
                   u.address AS address, u.referral_no AS referral_no,
                   u.active AS active, u.download_enabled AS download_enabled,
                   u.created_at AS joined, u.reports_enabled AS reports_enabled,
                   CASE WHEN u.password_hash IS NULL THEN FALSE ELSE TRUE END AS password_set,
                   u.must_change_password AS must_change_password,
                   u.password_reset_requested_at AS password_reset_requested_at,
                   u.paid AS paid, u.paid_amount AS paid_amount, u.paid_on AS paid_on, u.paid_mode AS paid_mode,
                   u.paid_ref AS paid_ref, u.paid_updated_at AS paid_updated_at, pu.name AS paid_updated_by_name,
                   gu.id AS area_unit_id, gu.path AS area_path,
                   cand.name AS candidate_name, g.expires_at AS access_expires_at,
                   COALESCE(act.n, 0) AS voters_recorded, act.last_active AS last_active,
            """
                    + ACCESS_STATE;

    private static final RowMapper<FieldAgentDto> MAPPER = (rs, rowNum) -> new FieldAgentDto(
            uuid(rs, "id"),
            code(rs.getObject("agent_no") == null ? null : rs.getInt("agent_no")),
            rs.getString("name"),
            rs.getString("phone"),
            rs.getString("email"),
            rs.getString("address"),
            rs.getString("referral_no"),
            rs.getBoolean("active"),
            uuid(rs, "area_unit_id"),
            // Grants are asked for a panchayat at a time now, but older ones sit
            // on a booth or a block. The unit's own path names each level
            // whichever it is, and LocationService keeps it true through renames.
            segment(rs.getString("area_path"), 2),
            segment(rs.getString("area_path"), 1),
            segment(rs.getString("area_path"), 0),
            rs.getString("candidate_name"),
            enumValue(rs, "access_state", AgentAccessState.class),
            instant(rs, "access_expires_at"),
            rs.getLong("voters_recorded"),
            instant(rs, "last_active"),
            instant(rs, "joined"),
            rs.getBoolean("download_enabled"),
            rs.getBoolean("reports_enabled"),
            rs.getBoolean("password_set"),
            rs.getBoolean("must_change_password"),
            instant(rs, "password_reset_requested_at"),
            rs.getBoolean("paid"),
            rs.getBigDecimal("paid_amount"),
            rs.getDate("paid_on") == null ? null : rs.getDate("paid_on").toLocalDate(),
            enumValue(rs, "paid_mode", PaymentMode.class),
            rs.getString("paid_ref"),
            instant(rs, "paid_updated_at"),
            rs.getString("paid_updated_by_name"));

    private final NamedParameterJdbcTemplate jdbc;

    /** One level of "District > Block > Panchayat > Booth"; null if the path stops short. */
    private static String segment(String path, int index) {
        if (path == null) {
            return null;
        }
        String[] parts = path.split(" > ");
        return index < parts.length ? parts[index] : null;
    }

    public static String code(Integer agentNo) {
        return agentNo == null ? null : "A-%05d".formatted(agentNo);
    }

    /** One page, ordered by the column the admin clicked (FA-2, FA-8). */
    public List<FieldAgentDto> page(FieldAgentFilter filter) {
        if (filter.matchesNothing()) {
            return List.of();
        }
        MapSqlParameterSource params = new MapSqlParameterSource();
        String sql = "SELECT * FROM (" + FIELDS + " " + BODY + where(filter, params) + ") x"
                + access(filter, params)
                + " ORDER BY " + filter.sort().column() + (filter.descending() ? " DESC" : " ASC")
                // Name breaks a tie on the voter count so paging never repeats or
                // skips an agent, and id breaks a tie on the name.
                + ", name ASC, id ASC"
                + " LIMIT :limit OFFSET :offset";
        params.addValue("limit", filter.size()).addValue("offset", (long) filter.page() * filter.size());
        return jdbc.query(sql, params, MAPPER);
    }

    /** One agent, read through the same query so the panel cannot disagree with the row. */
    public FieldAgentDto find(UUID id) {
        MapSqlParameterSource params = new MapSqlParameterSource().addValue("now", timestamp(Instant.now()));
        return jdbc
                .query("SELECT * FROM (" + FIELDS + " " + BODY + " WHERE u.role = 'FIELD_AGENT' AND u.id = :id) x",
                        params.addValue("id", id),
                        MAPPER)
                .stream()
                .findFirst()
                .orElse(null);
    }

    /**
     * All, Paid and Not paid for the current filters in one pass (FA-7). Taken
     * over every filter but the payment one, so the two always add up to All.
     */
    public Counts counts(FieldAgentFilter filter) {
        if (filter.matchesNothing()) {
            return new Counts(0, 0);
        }
        FieldAgentFilter across = filter.acrossPaymentStatuses();
        MapSqlParameterSource params = new MapSqlParameterSource();
        String sql = "SELECT COUNT(*) AS n, SUM(CASE WHEN paid THEN 1 ELSE 0 END) AS paid_n FROM ("
                + FIELDS + " " + BODY + where(across, params) + ") x" + access(across, params);
        return jdbc.query(sql, params, (rs, rowNum) -> new Counts(rs.getLong("n"), rs.getLong("paid_n")))
                .get(0);
    }

    /** How many agents match, for "Showing 1–25 of 8,432" and the bulk limit. */
    public long count(FieldAgentFilter filter) {
        if (filter.matchesNothing()) {
            return 0;
        }
        MapSqlParameterSource params = new MapSqlParameterSource();
        String sql = "SELECT COUNT(*) FROM (" + FIELDS + " " + BODY + where(filter, params) + ") x"
                + access(filter, params);
        Long value = jdbc.queryForObject(sql, params, Long.class);
        return value == null ? 0 : value;
    }

    /** Every agent the filters match, for a bulk action over all of them (FA-14). */
    public List<UUID> idsMatching(FieldAgentFilter filter, int limit) {
        if (filter.matchesNothing()) {
            return List.of();
        }
        MapSqlParameterSource params = new MapSqlParameterSource();
        String sql = "SELECT id FROM (" + FIELDS + " " + BODY + where(filter, params) + ") x"
                + access(filter, params) + " ORDER BY name, id LIMIT :limit";
        return jdbc.queryForList(sql, params.addValue("limit", limit), UUID.class);
    }

    /** Streams every matching agent for the CSV, in list order and without paging (FA-16). */
    public List<FieldAgentDto> allMatching(FieldAgentFilter filter, int limit) {
        if (filter.matchesNothing()) {
            return List.of();
        }
        MapSqlParameterSource params = new MapSqlParameterSource();
        String sql = "SELECT * FROM (" + FIELDS + " " + BODY + where(filter, params) + ") x"
                + access(filter, params)
                + " ORDER BY " + filter.sort().column() + (filter.descending() ? " DESC" : " ASC")
                + ", name ASC, id ASC LIMIT :limit";
        return jdbc.query(sql, params.addValue("limit", limit), MAPPER);
    }

    /**
     * Which agents hold a grant where, so the dropdowns can say "Bhadrak
     * (1,415)" (FA-3).
     *
     * <p>Ids rather than counts per unit: an agent with grants in two panchayats
     * of the same block is one agent in that block, and counts summed from below
     * would make them two.
     */
    public Map<UUID, Set<UUID>> agentIdsByGrantUnit(Collection<UUID> withinUnitIds) {
        Map<UUID, Set<UUID>> byUnit = new HashMap<>();
        if (withinUnitIds != null && withinUnitIds.isEmpty()) {
            return byUnit;
        }
        MapSqlParameterSource params = new MapSqlParameterSource();
        String sql = "SELECT ar.unit_id, ar.agent_id FROM access_request ar"
                + " JOIN app_user u ON u.id = ar.agent_id AND u.role = 'FIELD_AGENT'";
        if (withinUnitIds != null) {
            sql += " WHERE ar.unit_id IN (:withinUnitIds)";
            params.addValue("withinUnitIds", withinUnitIds);
        }
        jdbc.query(sql, params, (RowCallbackHandler) rs -> byUnit
                .computeIfAbsent(uuid(rs, "unit_id"), key -> new LinkedHashSet<>())
                .add(uuid(rs, "agent_id")));
        return byUnit;
    }

    /** Gives or removes the Reports page for a whole selection at once (FA-26). */
    public int setReportsEnabled(Collection<UUID> agentIds, boolean enabled, UUID byAdminId) {
        if (agentIds.isEmpty()) {
            return 0;
        }
        return jdbc.update(
                """
                UPDATE app_user
                   SET reports_enabled = :enabled, reports_updated_by_id = :by, reports_updated_at = :at
                 WHERE id IN (:ids) AND role = 'FIELD_AGENT'
                """,
                new MapSqlParameterSource()
                        .addValue("ids", agentIds)
                        .addValue("enabled", enabled)
                        .addValue("by", byAdminId)
                        .addValue("at", timestamp(Instant.now())));
    }

    /** Marks one agent, or a whole selection, in a single statement (FA-15). */
    public int mark(Collection<UUID> agentIds, MarkPaymentRequest payment, UUID byAdminId) {
        if (agentIds.isEmpty()) {
            return 0;
        }
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("ids", agentIds)
                .addValue("paid", payment.paid())
                // Marking not paid clears the payment rather than describing one.
                .addValue("amount", payment.paid() ? payment.amount() : null)
                .addValue("paidOn", payment.paid() && payment.paidOn() != null ? Date.valueOf(payment.paidOn()) : null)
                .addValue("mode", payment.paid() ? name(payment.mode()) : null)
                .addValue("reference", payment.paid() ? blankToNull(payment.reference()) : null)
                .addValue("by", byAdminId)
                .addValue("at", timestamp(Instant.now()));
        return jdbc.update(
                """
                UPDATE app_user
                   SET paid = :paid, paid_amount = :amount, paid_on = :paidOn, paid_mode = :mode,
                       paid_ref = :reference, paid_updated_by_id = :by, paid_updated_at = :at
                 WHERE id IN (:ids) AND role = 'FIELD_AGENT'
                """,
                params);
    }

    private String where(FieldAgentFilter filter, MapSqlParameterSource params) {
        List<String> clauses = new ArrayList<>();
        clauses.add("u.role = 'FIELD_AGENT'");
        params.addValue("now", timestamp(Instant.now()));

        if (filter.areaUnitIds() != null) {
            // Any grant, not the one shown: narrowing to a block must not hide an
            // agent whose newest grant happens to sit somewhere else.
            clauses.add("EXISTS (SELECT 1 FROM access_request f"
                    + " WHERE f.agent_id = u.id AND f.unit_id IN (:areaUnitIds))");
            params.addValue("areaUnitIds", filter.areaUnitIds());
        }
        if (filter.paid() != null) {
            clauses.add("u.paid = :paid");
            params.addValue("paid", filter.paid());
        }
        if (filter.reportsEnabled() != null) {
            clauses.add("u.reports_enabled = :reportsEnabled");
            params.addValue("reportsEnabled", filter.reportsEnabled());
        }
        String search = filter.search() == null ? "" : filter.search().trim();
        if (!search.isBlank()) {
            // Referral too: an admin given one worker's number wants everyone
            // that number sent, which is a question no other column answers.
            clauses.add("(LOWER(u.name) LIKE :q OR LOWER(COALESCE(u.phone, '')) LIKE :q"
                    + " OR LOWER(COALESCE(u.referral_no, '')) LIKE :q"
                    + " OR LOWER(COALESCE(" + AGENT_CODE + ", '')) LIKE :q)");
            params.addValue("q", "%" + search.toLowerCase() + "%");
        }
        return " WHERE " + String.join(" AND ", clauses);
    }

    /**
     * The access filter, applied outside the derived table because it reads the
     * state the select works out rather than any one column.
     */
    private String access(FieldAgentFilter filter, MapSqlParameterSource params) {
        if (filter.access() == null) {
            return "";
        }
        params.addValue("access", filter.access().name());
        return " WHERE x.access_state = :access";
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    /** All and Paid; Not paid is the difference, so the two cannot disagree. */
    public record Counts(long all, long paid) {
        public long unpaid() {
            return all - paid;
        }
    }
}
