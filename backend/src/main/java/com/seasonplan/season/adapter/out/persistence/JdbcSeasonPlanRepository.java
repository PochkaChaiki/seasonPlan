package com.seasonplan.season.adapter.out.persistence;

import com.seasonplan.shared.adapter.out.persistence.JdbcJson;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.seasonplan.season.domain.ChecklistItem;
import com.seasonplan.season.domain.SeasonPlan;
import com.seasonplan.season.domain.MaterialRevision;
import com.seasonplan.season.domain.MaterialRevisionStamp;
import com.seasonplan.season.domain.SeasonPlanStatus;
import com.seasonplan.shared.domain.TimeWindow;
import com.seasonplan.season.application.port.SeasonPlanRepository;
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
    public void lockSeasonWindows() {
        if (!org.springframework.transaction.support.TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new IllegalStateException("window locks require a command transaction");
        }
        jdbc.queryForList("select pg_advisory_xact_lock(hashtextextended(?, 0))", "season-windows");
    }

    @Override
    @Transactional
    public SeasonPlan save(SeasonPlan plan) {
        jdbc.queryForList("select id from season_plans where id = ? for update", plan.id());
        var history = materialHistory(plan.id());
        var revision = plan.currentMaterialRevision();
        revision.requireConsistentWith(history.isEmpty() ? null : history.get(history.size() - 1));
        if (history.isEmpty()) findById(plan.id()).ifPresent(current -> {
            if (!current.materialSnapshot().equals(revision.material()) || !java.util.Objects.equals(current.executionRules(), revision.executionRules()))
                throw new IllegalStateException("persisted material differs from the unrecorded baseline");
        });
        var stamp = !history.isEmpty() && history.get(history.size() - 1).stamp().number() == plan.materialRevision()
                ? history.get(history.size() - 1).stamp() : plan.materialRevisionStamp();
        int updated = jdbc.update("""
                update season_plans
                   set season_type = ?,
                       status = ?,
                       approval_comment = ?,
                       approval_conditions = cast(? as jsonb),
                       natural_phenomenon_parameters = cast(? as jsonb),
                       target_climate_metrics = cast(? as jsonb),
                       updated_by = ?,
                       updated_at = ?,
                       material_revision = ?, material_revision_actor = ?, material_revision_at = ?, material_revision_reason = ?
                 where id = ?
                """,
                plan.seasonType(),
                plan.status().name(),
                plan.approvalComment(),
                JdbcJson.write(objectMapper, plan.approvalConditions()),
                JdbcJson.write(objectMapper, plan.naturalPhenomenonParameters()),
                JdbcJson.write(objectMapper, plan.targetClimateMetrics()),
                plan.updatedBy(),
                Timestamp.from(plan.updatedAt()),
                stamp.number(), stamp.actorId(), Timestamp.from(stamp.capturedAt()), stamp.reason().name(),
                plan.id()
        );
        if (updated == 0) {
            jdbc.update("""
                    insert into season_plans (
                        id, season_type, status, approval_comment, approval_conditions, natural_phenomenon_parameters,
                        target_climate_metrics, created_by, updated_by, created_at, updated_at, execution_rules, material_revision, material_revision_actor, material_revision_at, material_revision_reason
                    ) values (?, ?, ?, ?, cast(? as jsonb), cast(? as jsonb), cast(? as jsonb), ?, ?, ?, ?, cast(? as jsonb), ?, ?, ?, ?)
                    """,
                    plan.id(),
                    plan.seasonType(),
                    plan.status().name(),
                    plan.approvalComment(),
                    JdbcJson.write(objectMapper, plan.approvalConditions()),
                    JdbcJson.write(objectMapper, plan.naturalPhenomenonParameters()),
                    JdbcJson.write(objectMapper, plan.targetClimateMetrics()),
                    plan.createdBy(),
                    plan.updatedBy(),
                    Timestamp.from(plan.createdAt()),
                    Timestamp.from(plan.updatedAt()),
                    plan.executionRules() == null ? null : JdbcJson.write(objectMapper, plan.executionRules()),
                    stamp.number(), stamp.actorId(), Timestamp.from(stamp.capturedAt()), stamp.reason().name()
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

        for (ChecklistItem item : plan.readinessChecklist()) {
            jdbc.update("""
                    insert into readiness_checklist_items (id, season_plan_id, item_key, complete, completed_by, completed_at)
                    values (?, ?, ?, ?, ?, ?)
                    on conflict (season_plan_id, item_key) do update
                       set complete = excluded.complete,
                           completed_by = case
                               when readiness_checklist_items.complete then readiness_checklist_items.completed_by
                               when excluded.complete then excluded.completed_by
                               else null
                           end,
                           completed_at = case
                               when readiness_checklist_items.complete then readiness_checklist_items.completed_at
                               when excluded.complete then excluded.completed_at
                               else null
                           end
                    """,
                    UUID.randomUUID(), plan.id(), item.key(), item.complete(),
                    item.complete() ? plan.updatedBy() : null,
                    item.complete() ? Timestamp.from(plan.updatedAt()) : null);
        }

        jdbc.update("insert into season_plan_material_revisions(season_plan_id, material_revision, snapshot) values (?, ?, cast(? as jsonb)) on conflict do nothing",
                plan.id(), plan.materialRevision(), JdbcJson.write(objectMapper, revision));
        var stored = materialHistory(plan.id());
        revision.requireConsistentWith(stored.get(stored.size() - 1));
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
        var history = materialHistory(id);
        if (!history.isEmpty()) {
            var exact = history.get(history.size() - 1).material().timeWindows();
            var order = java.util.Comparator.comparing(TimeWindow::start).thenComparing(TimeWindow::end);
            var projected = exact.stream().map(window -> new TimeWindow(postgresProjection(window.start()), postgresProjection(window.end()))).sorted(order).toList();
            if (!plan.timeWindows().stream().sorted(order).toList().equals(projected))
                throw new IllegalStateException("material window projection differs from immutable revision history");
            plan.setTimeWindows(exact);
        }
        return Optional.of(plan);
    }

    @Override
    public List<SeasonPlan> findAll() {
        return jdbc.query("select id from season_plans order by updated_at desc", (rs, rowNum) -> rs.getObject("id", UUID.class))
                .stream()
                .map(this::findById)
                .flatMap(Optional::stream)
                .toList();
    }

    @Override public List<MaterialRevision> materialHistory(UUID id) {
        return List.copyOf(jdbc.query("select snapshot from season_plan_material_revisions where season_plan_id = ? order by material_revision", (rs, row) -> {
            try { return objectMapper.readValue(rs.getString("snapshot"), MaterialRevision.class); }
            catch (com.fasterxml.jackson.core.JsonProcessingException error) { throw new IllegalStateException("invalid material revision snapshot", error); }
        }, id));
    }

    private SeasonPlan mapPlan(ResultSet rs, int rowNum) throws SQLException {
        var history = materialHistory(rs.getObject("id", UUID.class));
        if (!history.isEmpty() && history.get(history.size() - 1).stamp().number() != rs.getLong("material_revision"))
            throw new IllegalStateException("current material revision differs from history");
        var stamp = history.isEmpty() ? new MaterialRevisionStamp(rs.getLong("material_revision"), rs.getString("material_revision_actor"), rs.getTimestamp("material_revision_at").toInstant(), MaterialRevisionStamp.Reason.valueOf(rs.getString("material_revision_reason"))) : history.get(history.size() - 1).stamp();
        SeasonPlan plan = SeasonPlan.rehydrate(
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
                rs.getTimestamp("updated_at").toInstant(),
                readApprovalConditions(rs.getString("approval_conditions")),
                stamp
        );
        String snapshot = rs.getString("execution_rules");
        if (snapshot != null) {
            try {
                plan.pinExecutionRules(objectMapper.readValue(snapshot, com.seasonplan.reference.domain.ExecutionRuleSnapshot.class));
            } catch (com.fasterxml.jackson.core.JsonProcessingException ex) {
                throw new IllegalStateException("Invalid persisted execution rules for plan " + plan.id(), ex);
            }
        }
        return plan;
    }

    private List<com.seasonplan.season.domain.ApprovalCondition> readApprovalConditions(String json) {
        try {
            return objectMapper.readValue(json, new com.fasterxml.jackson.core.type.TypeReference<>() {});
        } catch (com.fasterxml.jackson.core.JsonProcessingException error) {
            throw new IllegalArgumentException("Could not deserialize approval conditions", error);
        }
    }

    /** PostgreSQL timestamp projection rounds to microseconds, including carry into the next second. */
    private static LocalDateTime postgresProjection(LocalDateTime value) {
        return value.plusNanos(500).truncatedTo(java.time.temporal.ChronoUnit.MICROS);
    }

    private LocalDateTime toLocalDateTime(ResultSet rs, String column) throws SQLException {
        return rs.getTimestamp(column).toLocalDateTime();
    }
}
