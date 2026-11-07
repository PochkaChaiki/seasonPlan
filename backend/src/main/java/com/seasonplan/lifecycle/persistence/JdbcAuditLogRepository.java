package com.seasonplan.lifecycle.persistence;

import com.seasonplan.lifecycle.domain.AuditAction;
import com.seasonplan.lifecycle.domain.AuditLogEntry;
import com.seasonplan.lifecycle.application.port.AuditLogRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.List;
import java.util.UUID;

@Repository
public class JdbcAuditLogRepository implements AuditLogRepository {
    private final JdbcTemplate jdbc;

    public JdbcAuditLogRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public AuditLogEntry append(AuditLogEntry entry) {
        jdbc.update("""
                insert into audit_log_entries (id, season_plan_id, action, actor_id, created_at, comment)
                values (?, ?, ?, ?, ?, ?)
                """,
                entry.id(),
                entry.seasonPlanId(),
                entry.action().name(),
                entry.actorId(),
                Timestamp.from(entry.createdAt()),
                entry.comment());
        return entry;
    }

    @Override
    public List<AuditLogEntry> entriesFor(UUID seasonPlanId) {
        return jdbc.query("""
                        select * from audit_log_entries
                         where season_plan_id = ?
                         order by created_at, id
                        """,
                this::mapEntry,
                seasonPlanId);
    }

    private AuditLogEntry mapEntry(ResultSet rs, int rowNum) throws SQLException {
        return new AuditLogEntry(
                rs.getObject("id", UUID.class),
                rs.getObject("season_plan_id", UUID.class),
                AuditAction.valueOf(rs.getString("action")),
                rs.getString("actor_id"),
                rs.getTimestamp("created_at").toInstant(),
                rs.getString("comment")
        );
    }
}
