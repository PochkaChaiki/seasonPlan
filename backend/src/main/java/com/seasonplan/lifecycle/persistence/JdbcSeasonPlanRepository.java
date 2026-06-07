package com.seasonplan.lifecycle.persistence;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.seasonplan.lifecycle.domain.ChecklistItem;
import com.seasonplan.lifecycle.domain.SeasonPlan;
import com.seasonplan.lifecycle.domain.SeasonPlanStatus;
import com.seasonplan.lifecycle.domain.TimeWindow;
import com.seasonplan.lifecycle.service.SeasonPlanRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public class JdbcSeasonPlanRepository implements SeasonPlanRepository {
    private final JdbcTemplate jdbc;
    private final ObjectMapper objectMapper;

    public JdbcSeasonPlanRepository(JdbcTemplate jdbc, ObjectMapper objectMapper) {
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
    }

    @Override
    @Transactional
    public SeasonPlan save(SeasonPlan plan) {
        int updated = jdbc.update("""
                update season_plans
                   set season_type = ?,
                       status = ?,
                       approval_comment = ?,
                       natural_phenomenon_parameters = cast(? as jsonb),
                       target_climate_metrics = cast(? as jsonb),
                       updated_by = ?,
                       updated_at = ?
                 where id = ?
                """,
                plan.seasonType(),
                plan.status().name(),
                plan.approvalComment(),
                JdbcJson.write(objectMapper, plan.naturalPhenomenonParameters()),
                JdbcJson.write(objectMapper, plan.targetClimateMetrics()),
                plan.updatedBy(),
                Timestamp.from(plan.updatedAt()),
                plan.id()
        );
        if (updated == 0) {
            jdbc.update("""
                    insert into season_plans (
                        id, season_type, status, approval_comment, natural_phenomenon_parameters,
                        target_climate_metrics, created_by, updated_by, created_at, updated_at
                    ) values (?, ?, ?, ?, cast(? as jsonb), cast(? as jsonb), ?, ?, ?, ?)
                    """,
                    plan.id(),
                    plan.seasonType(),
                    plan.status().name(),
                    plan.approvalComment(),
                    JdbcJson.write(objectMapper, plan.naturalPhenomenonParameters()),
                    JdbcJson.write(objectMapper, plan.targetClimateMetrics()),
                    plan.createdBy(),
                    plan.updatedBy(),
                    Timestamp.from(plan.createdAt()),
                    Timestamp.from(plan.updatedAt())
            );
        }

        jdbc.update("delete from season_plan_target_territories where season_plan_id = ?", plan.id());
        for (String territory : plan.targetTerritories()) {
            jdbc.update("insert into season_plan_target_territories (id, season_plan_id, territory) values (?, ?, ?)",
                    UUID.randomUUID(), plan.id(), territory);
        }

        jdbc.update("delete from season_plan_time_windows where season_plan_id = ?", plan.id());
        for (TimeWindow window : plan.timeWindows()) {
            jdbc.update("insert into season_plan_time_windows (id, season_plan_id, starts_at, ends_at) values (?, ?, ?, ?)",
                    UUID.randomUUID(), plan.id(), Timestamp.valueOf(window.start()), Timestamp.valueOf(window.end()));
        }

        jdbc.update("delete from readiness_checklist_items where season_plan_id = ?", plan.id());
        for (ChecklistItem item : plan.readinessChecklist()) {
            jdbc.update("""
                    insert into readiness_checklist_items (id, season_plan_id, item_key, complete, completed_by, completed_at)
                    values (?, ?, ?, ?, ?, ?)
                    """,
                    UUID.randomUUID(), plan.id(), item.key(), item.complete(),
                    item.complete() ? plan.updatedBy() : null,
                    item.complete() ? Timestamp.from(plan.updatedAt()) : null);
        }

        return plan;
    }

    @Override
    public Optional<SeasonPlan> findById(UUID id) {
        List<SeasonPlan> plans = jdbc.query("select * from season_plans where id = ?", this::mapPlan, id);
        if (plans.isEmpty()) {
            return Optional.empty();
        }
        SeasonPlan plan = plans.get(0);
        plan.setTargetTerritories(jdbc.queryForList(
                "select territory from season_plan_target_territories where season_plan_id = ? order by territory",
                String.class,
                id
        ));
        plan.setTimeWindows(jdbc.query(
                "select starts_at, ends_at from season_plan_time_windows where season_plan_id = ? order by starts_at",
                (rs, rowNum) -> new TimeWindow(toLocalDateTime(rs, "starts_at"), toLocalDateTime(rs, "ends_at")),
                id
        ));
        plan.setReadinessChecklist(jdbc.query(
                "select item_key, complete from readiness_checklist_items where season_plan_id = ? order by item_key",
                (rs, rowNum) -> new ChecklistItem(rs.getString("item_key"), rs.getBoolean("complete")),
                id
        ));
        return Optional.of(plan);
    }

    private SeasonPlan mapPlan(ResultSet rs, int rowNum) throws SQLException {
        return SeasonPlan.rehydrate(
                rs.getObject("id", UUID.class),
                rs.getString("season_type"),
                List.of(),
                List.of(),
                JdbcJson.readStringMap(objectMapper, rs.getString("natural_phenomenon_parameters")),
                JdbcJson.readDoubleMap(objectMapper, rs.getString("target_climate_metrics")),
                SeasonPlanStatus.valueOf(rs.getString("status")),
                rs.getString("approval_comment"),
                List.of(),
                rs.getString("created_by"),
                rs.getString("updated_by"),
                rs.getTimestamp("created_at").toInstant(),
                rs.getTimestamp("updated_at").toInstant()
        );
    }

    private LocalDateTime toLocalDateTime(ResultSet rs, String column) throws SQLException {
        return rs.getTimestamp(column).toLocalDateTime();
    }
}
