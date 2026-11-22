package com.seasonplan.shared.adapter.out.persistence;

import org.springframework.jdbc.core.JdbcTemplate;
import com.seasonplan.shared.domain.ExecutionTime;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public final class ExecutionJdbcSupport {
    private ExecutionJdbcSupport() { }

    public static Instant time(Instant value) { return ExecutionTime.canonical(value); }
    public static Timestamp timestamp(Instant value) { return value == null ? null : Timestamp.from(time(value)); }
    public static Instant instant(Timestamp value) { return value == null ? null : value.toInstant(); }
    public static void requireCanonical(JdbcTemplate jdbc, String table, String planColumn, UUID planId) {
        String sql = "select count(*) from " + table + " where canonical is null" + (planId == null ? "" : " and " + planColumn + " = ?");
        Integer count = planId == null ? jdbc.queryForObject(sql, Integer.class) : jdbc.queryForObject(sql, Integer.class, planId);
        if (count != null && count > 0) throw new com.seasonplan.shared.application.LegacyExecutionMetadataException(table);
    }
    public static boolean sameTime(Instant first, Instant second) { return Objects.equals(time(first), time(second)); }
}
