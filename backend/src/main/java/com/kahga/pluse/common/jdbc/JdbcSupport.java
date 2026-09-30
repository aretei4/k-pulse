package com.kahga.pluse.common.jdbc;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

/**
 * Row and parameter conversions shared by every repository, written against the
 * JDBC 4.2 object mappings so the same code runs on PostgreSQL and on H2 (the
 * test profile) without driver-specific branches.
 *
 * <p>Repositories expose {@code save()} as update-then-insert: ids are assigned
 * in Java before a row is written, so whether the row already exists is the only
 * thing that distinguishes a create from an edit. Nothing is written implicitly —
 * a setter on a loaded object persists nothing until it is saved.
 */
public final class JdbcSupport {

    private JdbcSupport() {}

    public static UUID uuid(ResultSet rs, String column) throws SQLException {
        return rs.getObject(column, UUID.class);
    }

    public static Instant instant(ResultSet rs, String column) throws SQLException {
        OffsetDateTime value = rs.getObject(column, OffsetDateTime.class);
        return value == null ? null : value.toInstant();
    }

    public static Integer integer(ResultSet rs, String column) throws SQLException {
        return rs.getObject(column, Integer.class);
    }

    public static <E extends Enum<E>> E enumValue(ResultSet rs, String column, Class<E> type) throws SQLException {
        String value = rs.getString(column);
        return value == null ? null : Enum.valueOf(type, value);
    }

    /**
     * Instants are bound to {@code timestamp with time zone} columns with an
     * explicit UTC offset, so the JVM's default zone can never shift a value.
     */
    public static OffsetDateTime timestamp(Instant instant) {
        return instant == null ? null : instant.atOffset(ZoneOffset.UTC);
    }

    /** Enums are stored by name, matching the varchar columns in V1__init.sql. */
    public static String name(Enum<?> value) {
        return value == null ? null : value.name();
    }

    /** {@code a.id AS agent_id, a.name AS agent_name, ...} for a joined table. */
    public static String columns(String alias, String prefix, String... names) {
        StringBuilder sql = new StringBuilder();
        for (int i = 0; i < names.length; i++) {
            if (i > 0) {
                sql.append(", ");
            }
            sql.append(alias).append('.').append(names[i]).append(" AS ").append(prefix).append(names[i]);
        }
        return sql.toString();
    }
}
