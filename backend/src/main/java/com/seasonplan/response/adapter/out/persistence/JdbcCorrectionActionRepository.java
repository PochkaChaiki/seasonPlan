package com.seasonplan.response.adapter.out.persistence;

import com.seasonplan.shared.adapter.out.persistence.ExecutionJdbcSupport;

import com.seasonplan.response.domain.CorrectionAction;
import com.seasonplan.response.application.port.CorrectionActionRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

@Repository
public class JdbcCorrectionActionRepository implements CorrectionActionRepository {
    private final JdbcTemplate jdbc;
    public JdbcCorrectionActionRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }
    @Override public Optional<CorrectionAction> find(UUID id) {
        return jdbc.query("select * from correction_actions where id = ?", this::map, id).stream().findFirst();
    }
    @Override public Optional<CorrectionAction> byKey(String key) {
        ExecutionJdbcSupport.requireCanonical(jdbc, "correction_actions", "season_plan_id", null);
        return jdbc.query("select * from correction_actions where canonical and deduplication_key = ?", this::map, key).stream().findFirst();
    }
    @Override public List<CorrectionAction> forPlan(UUID planId) {
        // Legacy actions do not carry a plan ID; their owning incident must be examined too.
        Integer legacy = jdbc.queryForObject("select count(*) from correction_actions a join magic_incidents i on i.id = a.magic_incident_id where a.canonical is null and i.season_plan_id = ?", Integer.class, planId);
        if (legacy != null && legacy > 0) throw new com.seasonplan.shared.application.LegacyExecutionMetadataException("correction_actions");
        return jdbc.query("select * from correction_actions where canonical and season_plan_id = ? order by due_at, id", this::map, planId);
    }
    @Override public List<CorrectionAction> allOpen() {
        ExecutionJdbcSupport.requireCanonical(jdbc, "correction_actions", "season_plan_id", null);
        return jdbc.query("select * from correction_actions where canonical and status in ('OPEN','IN_PROGRESS') order by due_at, id", this::map);
    }
    @Override @Transactional public void save(CorrectionAction action) {
        ExecutionJdbcSupport.requireCanonical(jdbc, "correction_actions", "season_plan_id", null);
        if (action.incidentId() != null) {
            Integer legacyIncident = jdbc.queryForObject("select count(*) from magic_incidents where id = ? and canonical is null", Integer.class, action.incidentId());
            if (legacyIncident != null && legacyIncident > 0) throw new com.seasonplan.shared.application.LegacyExecutionMetadataException("magic_incidents");
        }
        int updated = jdbc.update("""
                update correction_actions set status = ?, cancellation_reason = ?, updated_at = now()
                where id = ? and canonical and season_plan_id = ? and magic_incident_id is not distinct from ?
                  and deduplication_key = ? and description = ? and assigned_to = ? and due_at = ?
                  and status not in ('DONE','CANCELLED')
                """, action.status().name(), action.cancellationReason(), action.id(), action.seasonPlanId(), action.incidentId(), action.deduplicationKey(), action.description(), action.owner(), ExecutionJdbcSupport.timestamp(action.deadline()));
        if (updated != 0) return;
        int inserted = jdbc.update("""
                insert into correction_actions(id, season_plan_id, magic_incident_id, deduplication_key, description, assigned_to, due_at, status, cancellation_reason, canonical)
                values (?, ?, ?, ?, ?, ?, ?, ?, ?, true) on conflict do nothing
                """, action.id(), action.seasonPlanId(), action.incidentId(), action.deduplicationKey(), action.description(), action.owner(), ExecutionJdbcSupport.timestamp(action.deadline()), action.status().name(), action.cancellationReason());
        if (inserted == 0) {
            CorrectionAction existing = byKey(action.deduplicationKey()).orElseThrow(() -> new IllegalStateException("correction action identity conflict"));
            Optional<CorrectionAction> byId = find(action.id());
            if ((byId.isPresent() && !byId.get().id().equals(existing.id())) || !same(existing, action)) throw new IllegalStateException("conflicting correction deduplication key or immutable action");
        }
    }
    private boolean same(CorrectionAction a, CorrectionAction b) {
        return a.seasonPlanId().equals(b.seasonPlanId()) && Objects.equals(a.incidentId(), b.incidentId())
                && a.description().equals(b.description()) && a.owner().equals(b.owner()) && ExecutionJdbcSupport.sameTime(a.deadline(), b.deadline())
                && a.status() == b.status() && Objects.equals(a.cancellationReason(), b.cancellationReason());
    }
    private CorrectionAction map(ResultSet rs, int row) throws SQLException {
        if (!rs.getBoolean("canonical")) throw new com.seasonplan.shared.application.LegacyExecutionMetadataException("correction_actions");
        return new CorrectionAction(rs.getObject("id", UUID.class), rs.getObject("season_plan_id", UUID.class), rs.getObject("magic_incident_id", UUID.class), rs.getString("deduplication_key"), rs.getString("description"), rs.getString("assigned_to"), ExecutionJdbcSupport.instant(rs.getTimestamp("due_at")), CorrectionAction.Status.valueOf(rs.getString("status")), rs.getString("cancellation_reason"));
    }
}
