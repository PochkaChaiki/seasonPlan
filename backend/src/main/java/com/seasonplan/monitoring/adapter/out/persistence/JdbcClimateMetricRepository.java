package com.seasonplan.monitoring.adapter.out.persistence;

import com.seasonplan.shared.adapter.out.persistence.ExecutionJdbcSupport;

import com.seasonplan.monitoring.domain.ClimateMetric;
import com.seasonplan.reference.api.MetricDefinition;
import com.seasonplan.monitoring.application.port.ClimateMetricRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public class JdbcClimateMetricRepository implements ClimateMetricRepository {
    private final JdbcTemplate jdbc;
    public JdbcClimateMetricRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    @Override public Optional<ClimateMetric> byImportKey(String source, String eventId) {
        ExecutionJdbcSupport.requireCanonical(jdbc, "climate_metrics", "season_plan_id", null);
        return jdbc.query("select * from climate_metrics where canonical and source = ? and source_event_id = ?", this::map, source, eventId).stream().findFirst();
    }
    @Override public List<ClimateMetric> forPlan(UUID planId) {
        ExecutionJdbcSupport.requireCanonical(jdbc, "climate_metrics", "season_plan_id", planId);
        return jdbc.query("select * from climate_metrics where canonical and season_plan_id = ? order by measured_at, id", this::map, planId);
    }
    @Override @Transactional public void save(ClimateMetric metric) {
        ExecutionJdbcSupport.requireCanonical(jdbc, "climate_metrics", "season_plan_id", null);
        int inserted = jdbc.update("""
                insert into climate_metrics(id, season_plan_id, metric_key, metric_value, unit, source, source_event_id, target_value, tolerance, measured_at, canonical)
                values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, true) on conflict do nothing
                """, metric.id(), metric.seasonPlanId(), metric.definition().key(), metric.value(), metric.definition().unit(), metric.source(), metric.sourceEventId(), metric.target(), metric.tolerance(), ExecutionJdbcSupport.timestamp(metric.observedAt()));
        if (inserted == 0) {
            ClimateMetric existing = byImportKey(metric.source(), metric.sourceEventId()).orElseThrow(() -> new IllegalStateException("metric identity already exists with a different import key"));
            Integer idConflict = jdbc.queryForObject("select count(*) from climate_metrics where id = ? and id <> ?", Integer.class, metric.id(), existing.id());
            if ((idConflict != null && idConflict > 0) || !existing.seasonPlanId().equals(metric.seasonPlanId()) || existing.definition() != metric.definition()
                    || existing.value().compareTo(metric.value()) != 0 || existing.target().compareTo(metric.target()) != 0
                    || existing.tolerance().compareTo(metric.tolerance()) != 0 || !ExecutionJdbcSupport.sameTime(existing.observedAt(), metric.observedAt())) {
                throw new IllegalStateException("conflicting metric import key");
            }
        }
    }
    private ClimateMetric map(ResultSet rs, int row) throws SQLException {
        MetricDefinition definition = MetricDefinition.forKey(rs.getString("metric_key"));
        if (!definition.unit().equals(rs.getString("unit"))) throw new IllegalStateException("invalid persisted metric unit");
        return new ClimateMetric(rs.getObject("id", UUID.class), rs.getObject("season_plan_id", UUID.class), rs.getString("source"), rs.getString("source_event_id"), definition, rs.getBigDecimal("metric_value"), rs.getBigDecimal("target_value"), rs.getBigDecimal("tolerance"), null, ExecutionJdbcSupport.instant(rs.getTimestamp("measured_at")));
    }
}
