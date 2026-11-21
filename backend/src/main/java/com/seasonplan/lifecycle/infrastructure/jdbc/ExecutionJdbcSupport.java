package com.seasonplan.lifecycle.infrastructure.jdbc;

import org.springframework.jdbc.core.JdbcTemplate;
import com.seasonplan.lifecycle.domain.ExecutionTime;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

final class ExecutionJdbcSupport {
    private ExecutionJdbcSupport() { }

    static Instant time(Instant value) { return ExecutionTime.canonical(value); }
    static Timestamp timestamp(Instant value) { return value == null ? null : Timestamp.from(time(value)); }
    static Instant instant(Timestamp value) { return value == null ? null : value.toInstant(); }
    static void requireCanonical(JdbcTemplate jdbc, String table, String planColumn, UUID planId) {
        String sql = "select count(*) from " + table + " where canonical is null" + (planId == null ? "" : " and " + planColumn + " = ?");
        Integer count = planId == null ? jdbc.queryForObject(sql, Integer.class) : jdbc.queryForObject(sql, Integer.class, planId);
        if (count != null && count > 0) throw new com.seasonplan.lifecycle.domain.LegacyExecutionMetadataException(table);
    }
    static boolean sameTime(Instant first, Instant second) { return Objects.equals(time(first), time(second)); }
}
