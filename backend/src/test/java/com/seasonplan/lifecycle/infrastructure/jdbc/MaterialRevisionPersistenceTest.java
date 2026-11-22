package com.seasonplan.lifecycle.infrastructure.jdbc;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.seasonplan.season.adapter.out.persistence.*;
import com.seasonplan.season.application.*;
import com.seasonplan.season.domain.*;
import com.seasonplan.identity.api.ActorRole;
import com.seasonplan.lifecycle.service.SeasonPlanTestFixture;
import com.seasonplan.lifecycle.service.TestExecutionRules;
import org.junit.jupiter.api.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.*;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.*;
import java.time.*;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@Testcontainers
class MaterialRevisionPersistenceTest {
    @Container static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:14");
    static JdbcTemplate jdbc;
    static JdbcSeasonPlanRepository plans;
    static JdbcCommandTransaction commands;
    static final UUID LEGACY = UUID.randomUUID();
    @BeforeAll static void migrateLegacyDataThenUpgrade() throws Exception {
        var source = new DriverManagerDataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
        jdbc = new JdbcTemplate(source);
        try (var connection = source.getConnection()) {
            var migration = new liquibase.Liquibase("db/changelog/db.changelog-master.yaml", new liquibase.resource.ClassLoaderResourceAccessor(), new liquibase.database.jvm.JdbcConnection(connection));
            migration.update(17, new liquibase.Contexts(), new liquibase.LabelExpression());
            jdbc.update("insert into season_plans(id,season_type,status,natural_phenomenon_parameters,target_climate_metrics,created_by,updated_by,created_at,updated_at) values (?,'SPRING','VERIFIED','{\"bloom\":\"early\"}'::jsonb,'{\"temperature\":20}'::jsonb,'historic','historic','2000-01-01','2000-01-01')", LEGACY);
            jdbc.update("insert into season_plan_target_territories(id,season_plan_id,territory) values (?,?,'north')", UUID.randomUUID(), LEGACY);
            jdbc.update("insert into season_plan_time_windows(id,season_plan_id,starts_at,ends_at) values (?,?,'2000-01-01 09:00:00.123456','2000-01-01 10:00:00')", UUID.randomUUID(), LEGACY);
            migration.update(new liquibase.Contexts(), new liquibase.LabelExpression());
        }
        plans = new JdbcSeasonPlanRepository(jdbc, new ObjectMapper().findAndRegisterModules());
        commands = new JdbcCommandTransaction(jdbc, new DataSourceTransactionManager(source));
    }
    @Test void upgradeLabelsOnlyCurrentLegacyDataWithoutInventingEarlierHistory() {
        var history = plans.materialHistory(LEGACY);
        assertThat(history).hasSize(1);
        var entry = history.get(0);
        assertThat(entry.stamp().number()).isZero();
        assertThat(entry.stamp().reason()).isEqualTo(MaterialRevisionStamp.Reason.LEGACY_BASELINE);
        assertThat(entry.stamp().actorId()).isEqualTo("system:migration");
        assertThat(entry.stamp().capturedAt()).isAfter(Instant.parse("2020-01-01T00:00:00Z"));
        assertThat(entry.executionRules()).isNull();
        assertThat(entry.material().targetTerritories()).containsExactly("north");
        assertThat(plans.findById(LEGACY).orElseThrow().materialRevision()).isZero();
    }
    @Test void reloadNoOpsEditsReturnResubmitAndRejectedRewritesPreserveHistory() {
        var plan = draft(); commands.create(() -> plans.save(plan));
        var original = plans.materialHistory(plan.id()).get(0);
        var loaded = plans.findById(plan.id()).orElseThrow();
        var same = draft(); var territories = new ArrayList<>(loaded.targetTerritories()); Collections.reverse(territories); same.setTargetTerritories(territories);
        var windows = new ArrayList<>(loaded.timeWindows()); Collections.reverse(windows); same.setTimeWindows(windows);
        loaded.updateDraftFieldsFrom(same, "editor", Instant.now()); commands.execute(plan.id(), () -> plans.save(loaded));
        assertThat(plans.materialHistory(plan.id())).containsExactly(original);
        var edit = plans.findById(plan.id()).orElseThrow(); var changed = draft(); changed.setNaturalPhenomenonParameters(Map.of("bloom", "late"));
        edit.updateDraftFieldsFrom(changed, "editor", Instant.now()); commands.execute(plan.id(), () -> plans.save(edit));
        var second = plans.materialHistory(plan.id()).get(1);
        var state = plans.findById(plan.id()).orElseThrow(); state.submit("editor", Instant.now()); state.verify("chief", Instant.now()); state.returnForRevision("revise", "chief", Instant.now());
        commands.execute(plan.id(), () -> plans.save(state));
        assertThat(plans.materialHistory(plan.id())).containsExactly(original, second);
        assertThat(new SeasonQueryService(plans).get(plan.id()).materialRevision()).isEqualTo(2);
        assertThat(new SeasonQueryService(plans).materialHistory(plan.id())).containsExactly(original, second);
        var again = draft(); var targets = new HashMap<>(again.targetClimateMetrics()); targets.put("temperature", 18.0); again.setTargetClimateMetrics(targets);
        state.updateDraftFieldsFrom(again, "editor2", Instant.now()); state.submit("editor2", Instant.now()); commands.execute(plan.id(), () -> plans.save(state));
        var third = plans.materialHistory(plan.id()).get(2);
        assertThat(third.stamp().number()).isEqualTo(3); assertThat(second.executionRules()).isEqualTo(original.executionRules());
        var accidental = plans.findById(plan.id()).orElseThrow(); accidental.setTargetClimateMetrics(Map.of("temperature", 99.0));
        assertThatThrownBy(() -> commands.execute(plan.id(), () -> plans.save(accidental))).isInstanceOf(IllegalStateException.class);
        assertThat(plans.materialHistory(plan.id())).containsExactly(original, second, third);
        assertThat(plans.findById(plan.id()).orElseThrow().targetClimateMetrics()).containsEntry("temperature", 18.0);
    }
    @Test void failureAfterSnapshotSaveRollsBackRevisionAndHistoryAndCreateAuditFailureLeavesNothing() {
        var plan = draft(); commands.create(() -> plans.save(plan));
        assertThatThrownBy(() -> commands.execute(plan.id(), () -> {
            var loaded = plans.findById(plan.id()).orElseThrow(); var changed = draft(); changed.setNaturalPhenomenonParameters(Map.of("bloom", "late"));
            loaded.updateDraftFieldsFrom(changed, "editor", Instant.now()); plans.save(loaded); throw new IllegalStateException("after snapshot");
        })).hasMessage("after snapshot");
        assertThat(plans.materialHistory(plan.id())).hasSize(1);
        assertThat(plans.findById(plan.id()).orElseThrow().materialRevision()).isEqualTo(1);
        var audit = mock(com.seasonplan.audit.api.AuditTrail.class);
        when(audit.append(any(), any(), any(), any())).thenThrow(new IllegalStateException("audit failed"));
        var owner = new SeasonPlanService(plans, null, audit, commands, event -> {}, input -> List.of(), Clock.systemUTC(), TestExecutionRules::snapshot, id -> List.of());
        int before = jdbc.queryForObject("select count(*) from season_plan_material_revisions", Integer.class);
        int rows = jdbc.queryForObject("select count(*) from season_plans", Integer.class);
        assertThatThrownBy(() -> owner.createDraft(SeasonPlanTestFixture.validPlanDraft("creator"), ActorRole.FAIRY_PLANNER, "creator")).hasMessage("audit failed");
        assertThat(jdbc.queryForObject("select count(*) from season_plan_material_revisions", Integer.class)).isEqualTo(before);
        assertThat(jdbc.queryForObject("select count(*) from season_plans", Integer.class)).isEqualTo(rows);
    }
    @Test void exactFractionalWindowsSurviveRoundedProjectionAndRealTamperingIsRejected() {
        var plan = draft();
        var exact = new com.seasonplan.shared.domain.TimeWindow(LocalDateTime.parse("2026-03-01T09:00:00.123456789"), LocalDateTime.parse("2026-03-01T12:00:00.999999789"));
        plan.setTimeWindows(List.of(exact)); commands.create(() -> plans.save(plan));
        var history = plans.materialHistory(plan.id());
        var loaded = plans.findById(plan.id()).orElseThrow();
        assertThat(loaded.timeWindows()).containsExactly(exact);
        loaded.completeReadinessChecklistItem("manual", "coordinator", Instant.now());
        commands.execute(plan.id(), () -> plans.save(loaded));
        loaded.updateDraftFieldsFrom(plan, "editor", Instant.now()); commands.execute(plan.id(), () -> plans.save(loaded));
        assertThat(plans.materialHistory(plan.id())).isEqualTo(history);
        assertThat(loaded.materialRevision()).isEqualTo(1);
        assertThat(jdbc.queryForObject("select ends_at from season_plan_time_windows where season_plan_id = ?", java.sql.Timestamp.class, plan.id()).toLocalDateTime())
                .isEqualTo(LocalDateTime.parse("2026-03-01T12:00:01"));
        jdbc.update("update season_plan_time_windows set starts_at = starts_at + interval '1 second' where season_plan_id = ?", plan.id());
        assertThatThrownBy(() -> plans.findById(plan.id())).isInstanceOf(IllegalStateException.class).hasMessageContaining("window projection");
    }

    private SeasonPlan draft() {
        var input = SeasonPlanTestFixture.validPlanDraft("creator");
        var value = SeasonPlan.createDraft(UUID.randomUUID(), input.seasonType(), input.targetTerritories(), input.timeWindows(), input.naturalPhenomenonParameters(), input.targetClimateMetrics(), List.of(), "creator", Instant.now());
        value.setTargetTerritories(List.of("south", "north"));
        var first = input.timeWindows().get(0);
        value.setTimeWindows(List.of(new com.seasonplan.shared.domain.TimeWindow(first.end().plusHours(1), first.end().plusHours(2)), first));
        value.pinExecutionRules(TestExecutionRules.snapshot()); return value;
    }
}
