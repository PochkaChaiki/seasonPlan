package com.seasonplan.lifecycle.infrastructure.jdbc;

import com.seasonplan.lifecycle.domain.MagicIncident;
import com.seasonplan.lifecycle.repository.MagicIncidentRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public class JdbcMagicIncidentRepository implements MagicIncidentRepository {
    private final JdbcTemplate jdbc;
    public JdbcMagicIncidentRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }
    @Override public Optional<MagicIncident> find(UUID id) {
        return jdbc.query("select * from magic_incidents where id = ?", this::map, id).stream().findFirst();
    }
    @Override public List<MagicIncident> forPlan(UUID planId) {
        ExecutionJdbcSupport.requireCanonical(jdbc, "magic_incidents", "season_plan_id", planId);
        return jdbc.query("select * from magic_incidents where season_plan_id = ? order by opened_at, id", this::map, planId);
    }
    @Override public void save(MagicIncident incident) {
        ExecutionJdbcSupport.requireCanonical(jdbc, "magic_incidents", "season_plan_id", incident.seasonPlanId());
        int count = jdbc.update("""
                insert into magic_incidents(id, season_plan_id, incident_type, severity, status, cause, consequences, opened_by, opened_at, canonical)
                values (?, ?, ?, ?, ?, ?, ?, ?, ?, true)
                on conflict (id) do update set severity = excluded.severity, status = excluded.status
                where magic_incidents.canonical and magic_incidents.season_plan_id = excluded.season_plan_id
                  and magic_incidents.incident_type = excluded.incident_type and magic_incidents.cause = excluded.cause
                  and magic_incidents.consequences = excluded.consequences and magic_incidents.opened_by = excluded.opened_by
                  and magic_incidents.opened_at = excluded.opened_at
                """, incident.id(), incident.seasonPlanId(), incident.type().name(), incident.severity().name(), incident.status().name(), incident.cause(), incident.consequences(), incident.openedBy(), ExecutionJdbcSupport.timestamp(incident.openedAt()));
        if (count == 0) throw new IllegalStateException("incident identity or original details conflict");
    }
    private MagicIncident map(ResultSet rs, int row) throws SQLException {
        if (!rs.getBoolean("canonical")) throw new com.seasonplan.lifecycle.domain.LegacyExecutionMetadataException("magic_incidents");
        return new MagicIncident(rs.getObject("id", UUID.class), rs.getObject("season_plan_id", UUID.class), MagicIncident.Type.valueOf(rs.getString("incident_type")), MagicIncident.Severity.valueOf(rs.getString("severity")), MagicIncident.Status.valueOf(rs.getString("status")), rs.getString("cause"), rs.getString("consequences"), rs.getString("opened_by"), ExecutionJdbcSupport.instant(rs.getTimestamp("opened_at")));
    }
}
