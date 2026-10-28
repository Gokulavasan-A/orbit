package com.enterprise.api.util;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

/**
 * Null-safe column readers for stored procedure result sets. All DATETIME2
 * values in the database are UTC.
 */
public final class SqlRowUtils {

    private SqlRowUtils() {
    }

    public static Instant getInstant(ResultSet rs, String column) throws SQLException {
        LocalDateTime value = rs.getObject(column, LocalDateTime.class);
        return value == null ? null : value.toInstant(ZoneOffset.UTC);
    }

    public static Long getLong(ResultSet rs, String column) throws SQLException {
        long value = rs.getLong(column);
        return rs.wasNull() ? null : value;
    }

    public static Integer getInteger(ResultSet rs, String column) throws SQLException {
        int value = rs.getInt(column);
        return rs.wasNull() ? null : value;
    }

    public static Boolean getBoolean(ResultSet rs, String column) throws SQLException {
        boolean value = rs.getBoolean(column);
        return rs.wasNull() ? null : value;
    }

    /** UNIQUEIDENTIFIER column. */
    public static UUID getUuid(ResultSet rs, String column) throws SQLException {
        String value = rs.getString(column);
        return value == null ? null : UUID.fromString(value);
    }

    /** UNIQUEIDENTIFIER parameter value (declared as {@code Types.NVARCHAR}; SQL Server converts it). */
    public static String toSqlUuid(UUID value) {
        return value == null ? null : value.toString();
    }

    /** DATETIME2 parameter value (UTC, declared as {@code Types.TIMESTAMP}). */
    public static LocalDateTime toSqlUtc(Instant value) {
        return value == null ? null : LocalDateTime.ofInstant(value, ZoneOffset.UTC);
    }
}
