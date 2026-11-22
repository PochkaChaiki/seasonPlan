package com.seasonplan.lifecycle.infrastructure.jdbc;

import com.seasonplan.monitoring.adapter.out.persistence.JdbcClimateMetricRepository;
import com.seasonplan.monitoring.application.port.ClimateMetricRepository;
import com.seasonplan.monitoring.domain.ClimateMetric;
import com.seasonplan.notification.adapter.out.persistence.JdbcNotificationOutbox;
import com.seasonplan.reference.api.MetricDefinition;
import com.seasonplan.response.adapter.out.persistence.JdbcCorrectionActionRepository;
import com.seasonplan.response.adapter.out.persistence.JdbcMagicIncidentRepository;
import com.seasonplan.response.application.port.CorrectionActionRepository;
import com.seasonplan.response.application.port.MagicIncidentRepository;
import com.seasonplan.response.domain.CorrectionAction;
import com.seasonplan.response.domain.MagicIncident;
import com.seasonplan.ritual.adapter.out.persistence.JdbcRitualSessionRepository;
import com.seasonplan.ritual.application.port.RitualSessionRepository;
import com.seasonplan.ritual.domain.RitualSession;
import com.seasonplan.shared.domain.DomainValidationException;

import com.seasonplan.audit.api.AuditLogEntry;
import com.seasonplan.audit.api.AuditAction;
import com.seasonplan.notification.api.NotificationOutbox;
import com.seasonplan.audit.application.AuditLogService;
import com.seasonplan.season.api.CommandTransaction;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.*;

import static org.assertj.core.api.Assertions.*;

@SpringBootTest
@org.springframework.test.annotation.DirtiesContext(classMode = org.springframework.test.annotation.DirtiesContext.ClassMode.AFTER_CLASS)
@ActiveProfiles("test")
@Testcontainers
class PostgresExecutionRepositoriesTest {
    /** Close workers and datasource before the Testcontainers afterAll callback stops PostgreSQL. */
    @org.junit.jupiter.api.AfterAll
    static void closeApplicationBeforeContainerStops(org.springframework.context.ConfigurableApplicationContext context) {
        assertThat(postgres.isRunning()).isTrue();
        // Evict through Spring so later test listeners do not access a closed cached context.
        new org.springframework.test.context.TestContextManager(PostgresExecutionRepositoriesTest.class).getTestContext()
                .markApplicationContextDirty(org.springframework.test.annotation.DirtiesContext.HierarchyMode.EXHAUSTIVE);
        assertThat(context.isActive()).isFalse();
    }

    @Container static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:14");
    @DynamicPropertySource static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.liquibase.user", postgres::getUsername);
        registry.add("spring.liquibase.password", postgres::getPassword);
        registry.add("seasonplan.incident-rules.version", () -> "test-v1");
        for (var type : MagicIncident.Type.values()) registry.add("seasonplan.incident-rules.severity." + type.name(), () -> "HIGH");
        registry.add("seasonplan.execution-rules.version", () -> "test-v1");
        registry.add("seasonplan.execution-rules.required-events", () -> "LAUNCH");
        for (String metric : List.of("temperature", "humidity", "pollen_density")) {
            registry.add("seasonplan.execution-rules.metrics." + metric + ".tolerance", () -> "2");
            registry.add("seasonplan.execution-rules.metrics." + metric + ".owner", () -> "owner");
            registry.add("seasonplan.execution-rules.metrics." + metric + ".response-seconds", () -> "3600");
        }
    }
    @Autowired JdbcTemplate jdbc;
    @Autowired ClimateMetricRepository metrics;
    @Autowired MagicIncidentRepository incidents;
    @Autowired CorrectionActionRepository actions;
    @Autowired RitualSessionRepository rituals;
    @Autowired CommandTransaction transactions;
    @Autowired AuditLogService audit;
    @Autowired JdbcNotificationOutbox outbox;
    @Autowired com.seasonplan.monitoring.application.MonitoringService monitoring;
    @Autowired com.seasonplan.ritual.application.RitualService ritualService;
    @Autowired com.seasonplan.response.application.IncidentResponseService incidentService;
    @Autowired com.seasonplan.ritual.api.RitualReadinessChecker readiness;
    @Autowired com.seasonplan.season.application.SeasonPlanService seasonPlans;
    @Autowired com.seasonplan.reference.adapter.out.configuration.ConfiguredExecutionRules configuredRules;
    @Autowired com.fasterxml.jackson.databind.ObjectMapper mapper;
    private static final Instant TIME = Instant.parse("2026-01-02T03:04:05.123456789Z");

    private UUID plan() {
        UUID id = UUID.randomUUID();
        jdbc.update("insert into season_plans(id, season_type, status, natural_phenomenon_parameters, target_climate_metrics, created_by, updated_by) values (?, 'SPRING', 'DRAFT', '{}'::jsonb, '{}'::jsonb, 'test', 'test')", id);
        return id;
    }
    private ClimateMetric metric(UUID plan, String key, String value) {
        return new ClimateMetric(UUID.randomUUID(), plan, "sensor", key, MetricDefinition.TEMPERATURE, new BigDecimal(value), new BigDecimal("20.0"), new BigDecimal("2.00"), null, TIME);
    }
    private RitualSession ritual(UUID plan, int attempt) {
        return RitualSession.start(UUID.randomUUID(), plan, attempt, "starter", TIME, List.of(new RitualSession.Participant("fairy", "RITUAL_LEAD", TIME)));
    }
    private MagicIncident incident(UUID plan) {
        return new MagicIncident(UUID.randomUUID(), plan, MagicIncident.Type.CLIMATE, MagicIncident.Severity.CRITICAL, MagicIncident.Status.OPEN, "cause", "consequences", "actor", TIME);
    }

    @Autowired com.seasonplan.work.api.WorkQueries workQueries;
    @Autowired com.seasonplan.response.api.ResponseQueries responseQueries;
    @Autowired com.seasonplan.ritual.api.RitualQueries ritualQueries;
    @Autowired com.seasonplan.monitoring.api.MetricQueries metricQueries;
    @Autowired com.seasonplan.work.application.port.MagicTaskRepository taskRepository;
    @Autowired com.seasonplan.work.application.port.FairyAssignmentRepository assignmentRepository;

    @Test void publicOwnerQueriesRoundTripCurrentStateWithoutChangingEarlierSnapshots() {
        UUID id = plan();
        var window = new com.seasonplan.shared.domain.TimeWindow(java.time.LocalDateTime.of(2026, 1, 1, 10, 0), java.time.LocalDateTime.of(2026, 1, 1, 11, 0));
        var task = new com.seasonplan.work.domain.MagicTask("task", "description", "talent", window, com.seasonplan.work.domain.MagicTaskStatus.READY, "WEATHER");
        task.setId(UUID.randomUUID()); task.setSeasonPlanId(id); taskRepository.save(task, "actor");
        var assignment = new com.seasonplan.work.domain.FairyAssignment(UUID.randomUUID(), task.id(), "fairy", "LEAD", "talent", com.seasonplan.work.domain.AssignmentStatus.CONFIRMED, TIME);
        assignmentRepository.save(assignment, "actor");
        var observed = metric(id, UUID.randomUUID().toString(), "24"); metrics.save(observed);
        var incident = incident(id); incidents.save(incident);
        var action = CorrectionAction.open(UUID.randomUUID(), id, incident.id(), UUID.randomUUID().toString(), "correct", "owner", com.seasonplan.shared.domain.ExecutionTime.canonical(TIME)); actions.save(action);
        var session = ritual(id, 1); rituals.save(session);
        var taskFacts = workQueries.tasksForPlan(id); var assignmentFacts = workQueries.assignmentsForTask(task.id());
        var incidentFacts = responseQueries.incidentsForPlan(id); var ritualFacts = ritualQueries.forPlan(id);
        assertThat(metricQueries.forPlan(id)).containsExactly(observed);
        assertThat(responseQueries.correctionsForPlan(id)).containsExactly(action);
        assertThat(assignmentFacts.get(0).confirmedAt()).isEqualTo(com.seasonplan.shared.domain.ExecutionTime.canonical(TIME));
        task.updateFrom(new com.seasonplan.work.domain.MagicTask("updated", "description", "talent", window, com.seasonplan.work.domain.MagicTaskStatus.TODO, "WEATHER"));
        taskRepository.save(task, "actor"); incidents.save(incident.resolve());
        rituals.save(session.addEvent(new RitualSession.Event(TIME.plusSeconds(1), "LAUNCH", "ok")));
        assertThat(taskFacts.get(0).title()).isEqualTo("task");
        assertThat(workQueries.tasksForPlan(id).get(0).title()).isEqualTo("updated");
        assertThat(incidentFacts.get(0).critical()).isTrue();
        assertThat(responseQueries.incidentsForPlan(id).get(0).critical()).isFalse();
        assertThat(ritualFacts.get(0).events()).isEmpty(); assertThat(ritualQueries.forPlan(id).get(0).events()).hasSize(1);
        assertThatThrownBy(taskFacts::clear).isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(assignmentFacts::clear).isInstanceOf(UnsupportedOperationException.class);
    }

    @Test void runtimeCompositionUsesRealRepositoriesAndProviders() {
        assertThat(metrics).isInstanceOf(JdbcClimateMetricRepository.class);
        assertThat(actions).isInstanceOf(JdbcCorrectionActionRepository.class);
        assertThat(incidents).isInstanceOf(JdbcMagicIncidentRepository.class);
        assertThat(rituals).isInstanceOf(JdbcRitualSessionRepository.class);
        assertThat(readiness).isInstanceOf(com.seasonplan.workflows.application.ComputedRitualReadinessChecker.class);
        assertThat(org.springframework.test.util.ReflectionTestUtils.getField(monitoring, "metrics")).isSameAs(metrics);
        assertThat(org.springframework.test.util.ReflectionTestUtils.getField(monitoring, "rules")).isInstanceOf(com.seasonplan.monitoring.application.PinnedMetricRuleProvider.class);
        assertThat(org.springframework.test.util.ReflectionTestUtils.getField(ritualService, "readiness")).isSameAs(readiness);
        assertThat(org.springframework.test.util.ReflectionTestUtils.getField(incidentService, "incidents")).isSameAs(incidents);
    }

    @Test void roundTripsAndExactReplaysAndRejectsConflicts() {
        UUID plan = plan(); String key = UUID.randomUUID().toString();
        ClimateMetric metric = metric(plan, key, "23.0000000001"); metrics.save(metric);
        ClimateMetric read = metrics.byImportKey("sensor", key).orElseThrow();
        assertThat(read.value()).isEqualByComparingTo("23.0000000001");
        assertThat(read.observedAt()).isEqualTo(Instant.parse("2026-01-02T03:04:05.123456Z"));
        metrics.save(metric(plan, key, "23.00000000010"));
        assertThatThrownBy(() -> metrics.save(metric(plan, key, "23.1"))).hasRootCauseInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> jdbc.update("update climate_metrics set metric_value = 5 where id = ?", metric.id())).isInstanceOf(RuntimeException.class);
        MagicIncident incident = incident(plan); incidents.save(incident); incidents.save(incident.resolve());
        assertThat(incidents.find(incident.id()).orElseThrow().status()).isEqualTo(MagicIncident.Status.RESOLVED);
        CorrectionAction action = CorrectionAction.open(UUID.randomUUID(), plan, incident.id(), key, "correct", "owner", TIME);
        actions.save(action); actions.save(action);
        CorrectionAction completed = action.transition(CorrectionAction.Status.IN_PROGRESS, null).transition(CorrectionAction.Status.DONE, null);
        actions.save(action.transition(CorrectionAction.Status.IN_PROGRESS, null)); actions.save(completed); actions.save(completed);
        assertThat(actions.find(action.id()).orElseThrow().status()).isEqualTo(CorrectionAction.Status.DONE);
        assertThatThrownBy(() -> actions.save(action)).hasRootCauseInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> jdbc.update("update correction_actions set description = 'changed' where id = ?", action.id())).isInstanceOf(RuntimeException.class);
        RitualSession started = ritual(plan, 1); rituals.save(started);
        RitualSession withEvent = started.addEvent(new RitualSession.Event(TIME.plusSeconds(1), "LAUNCH", "ok"));
        rituals.save(withEvent); RitualSession finished = withEvent.succeed(TIME.plusSeconds(2)); rituals.save(finished); rituals.save(finished);
        RitualSession readRitual = rituals.find(started.id()).orElseThrow();
        assertThat(readRitual.participants()).hasSize(1); assertThat(readRitual.events()).hasSize(1); assertThat(readRitual.result()).isEqualTo(RitualSession.Result.SUCCESS);
        assertThatThrownBy(() -> rituals.save(ritual(plan, 2))).isInstanceOf(RuntimeException.class);
        assertThatThrownBy(() -> jdbc.update("update ritual_session_events set payload = 'changed' where session_id = ?", started.id())).isInstanceOf(RuntimeException.class);
        assertThatThrownBy(() -> jdbc.update("insert into ritual_session_participants values (?, 3, 'fake', 'fake', now())", started.id())).isInstanceOf(RuntimeException.class);
    }

    @Test void failedRitualCanRetryButAttemptNumberCannotBeReused() {
        UUID plan = plan(); RitualSession first = ritual(plan, 1);
        rituals.save(first); rituals.save(first.fail("FAIL", "failed", TIME.plusSeconds(2)));
        assertThatThrownBy(() -> rituals.save(ritual(plan, 1))).isInstanceOf(RuntimeException.class);
        RitualSession second = ritual(plan, 2); rituals.save(second);
        assertThat(rituals.forPlan(plan)).extracting(RitualSession::attempt).containsExactly(1, 2);
        rituals.save(second.succeed(TIME.plusSeconds(3)));
        rituals.save(first.fail("FAIL", "failed", TIME.plusSeconds(2)));
        assertThat(rituals.find(first.id()).orElseThrow().result()).isEqualTo(RitualSession.Result.FAILED);
    }

    @Test void databaseRejectsCrossPlanIncidentAndIncompleteCanonicalRows() {
        UUID first = plan(), second = plan(); MagicIncident incident = incident(first); incidents.save(incident);
        CorrectionAction wrong = CorrectionAction.open(UUID.randomUUID(), second, incident.id(), UUID.randomUUID().toString(), "bad", "owner", TIME);
        assertThatThrownBy(() -> actions.save(wrong)).isInstanceOf(RuntimeException.class);
        assertThatThrownBy(() -> jdbc.update("insert into climate_metrics(id, season_plan_id, metric_key, metric_value, unit, source, measured_at, canonical) values (?, ?, 'temperature', 1, 'CELSIUS', 'sensor', now(), true)", UUID.randomUUID(), first)).isInstanceOf(RuntimeException.class);
    }

    @Test void legacyRowsRemainPresentAndBlockAffectedOperations() throws Exception {
        UUID plan = plan(), id = UUID.randomUUID();
        jdbc.update("update season_plans set execution_rules = cast(? as jsonb) where id = ?", mapper.writeValueAsString(configuredRules.snapshot()), plan);
        jdbc.execute("alter table magic_incidents disable trigger magic_incidents_require_canonical");
        try {
            jdbc.update("insert into magic_incidents(id, season_plan_id, severity, status, title, opened_by) values (?, ?, 'LOW', 'OPEN', 'historical', 'actor')", id, plan);
        } finally { jdbc.execute("alter table magic_incidents enable trigger magic_incidents_require_canonical"); }
        try {
            assertThatThrownBy(() -> incidents.find(id)).hasRootCauseInstanceOf(IllegalStateException.class).hasMessageContaining("operator remediation");
            assertThatThrownBy(() -> incidents.forPlan(plan)).hasRootCauseInstanceOf(IllegalStateException.class).hasMessageContaining("operator remediation");
            assertThatThrownBy(() -> seasonPlans.calculateChecklist(plan)).isInstanceOfSatisfying(DomainValidationException.class,
                    ex -> assertThat(ex.fieldErrors()).containsKey("executionRules"));
            assertThat(jdbc.queryForObject("select title from magic_incidents where id = ?", String.class, id)).isEqualTo("historical");
        } finally { jdbc.update("delete from magic_incidents where id = ?", id); }
    }

    @Test void jdbcBackedMonitoringReplayUsesCanonicalMicrosecondsWithoutDuplicateEffects() throws Exception {
        UUID plan = plan();
        jdbc.update("update season_plans set status = 'ACTIVE', target_climate_metrics = '{\"temperature\":20}'::jsonb, execution_rules = cast(? as jsonb) where id = ?", mapper.writeValueAsString(configuredRules.snapshot()), plan);
        String key = UUID.randomUUID().toString();
        var input = new com.seasonplan.monitoring.application.MonitoringService.Measurement("sensor", key, "temperature", "CELSIUS", new BigDecimal("20.00"), TIME);
        ClimateMetric first = monitoring.record(plan, input, com.seasonplan.identity.api.ActorRole.CLIMATE_BALANCER, "owner");
        ClimateMetric replay = monitoring.record(plan, input, com.seasonplan.identity.api.ActorRole.CLIMATE_BALANCER, "owner");
        assertThat(replay.id()).isEqualTo(first.id());
        assertThat(metrics.forPlan(plan)).hasSize(1);
        assertThat(audit.entriesFor(plan)).extracting(AuditLogEntry::action).containsExactly(AuditAction.CLIMATE_METRIC_RECORDED);
        assertThat(jdbc.queryForObject("select count(*) from notification_outbox where season_plan_id = ?", Integer.class, plan)).isZero();
    }

    @Test void creationTransactionRollsBackPlanAndAuditOnFailure() {
        UUID id = UUID.randomUUID();
        assertThatThrownBy(() -> transactions.create(() -> {
            jdbc.update("insert into season_plans(id, season_type, status, natural_phenomenon_parameters, target_climate_metrics, created_by, updated_by) values (?, 'SPRING', 'DRAFT', '{}'::jsonb, '{}'::jsonb, 'test', 'test')", id);
            audit.append(id, AuditAction.SEASON_PLAN_CREATED, "actor", null);
            throw new IllegalStateException("after audit");
        })).hasMessage("after audit");
        assertThat(jdbc.queryForObject("select count(*) from season_plans where id = ?", Integer.class, id)).isZero();
        assertThat(jdbc.queryForObject("select count(*) from audit_log_entries where season_plan_id = ?", Integer.class, id)).isZero();
    }

    @Test void commandRollbackIncludesRepositoriesAuditAndOutbox() {
        UUID plan = plan(); ClimateMetric metric = metric(plan, UUID.randomUUID().toString(), "20"); String event = UUID.randomUUID().toString();
        assertThatThrownBy(() -> transactions.execute(plan, () -> {
            metrics.save(metric);
            audit.append(plan, AuditAction.SEASON_PLAN_CREATED, "actor", "rollback");
            outbox.enqueue(new NotificationOutbox.Event(event, plan, "TEST", Set.of("owner"), Set.of(NotificationOutbox.Channel.IN_APP), "message", TIME));
            throw new IllegalStateException("abort");
        })).hasMessage("abort");
        assertThat(metrics.forPlan(plan)).isEmpty();
        assertThat(audit.entriesFor(plan)).isEmpty();
        assertThat(jdbc.queryForObject("select count(*) from notification_outbox where event_id = ?", Integer.class, event)).isZero();
    }

    @Autowired com.seasonplan.season.api.SeasonLifecycle lifecycle;
    @Autowired com.seasonplan.season.application.port.SeasonPlanRepository planRepository;

    @Test void nestedLifecycleCommandsRollBackStateAuditRitualAndOutboxWithOuterFailure() {
        for (String state : List.of("RITUAL_READY", "ACTIVE", "COMPLETED")) {
            UUID id = plan();
            jdbc.update("update season_plans set status = ? where id = ?", state, id);
            var session = ritual(id, 1).succeed(TIME);
            String event = UUID.randomUUID().toString();
            assertThatThrownBy(() -> transactions.execute(id, () -> {
                rituals.save(session);
                var report = new com.seasonplan.season.api.SeasonLifecycle.ReportEvidence(UUID.randomUUID(), id, true, false);
                if (state.equals("RITUAL_READY")) lifecycle.activate(id,
                        new com.seasonplan.season.api.SeasonLifecycle.RitualEvidence(session.id(), id, true), "actor");
                else if (state.equals("ACTIVE")) lifecycle.complete(id, report, "actor");
                else lifecycle.archive(id, report, "actor");
                outbox.enqueue(new NotificationOutbox.Event(event, id, "TEST", Set.of("owner"), Set.of(NotificationOutbox.Channel.IN_APP), "message", TIME));
                throw new IllegalStateException("outer failure");
            })).hasMessage("outer failure");
            assertThat(planRepository.findById(id).orElseThrow().status().name()).isEqualTo(state);
            assertThat(rituals.forPlan(id)).isEmpty();
            assertThat(audit.entriesFor(id)).isEmpty();
            assertThat(jdbc.queryForObject("select count(*) from notification_outbox where event_id = ?", Integer.class, event)).isZero();
        }
    }

    @Test void lifecycleAuditFailureRollsBackEveryTransition() {
        var failingAudit = org.mockito.Mockito.mock(com.seasonplan.audit.api.AuditTrail.class);
        org.mockito.Mockito.doAnswer(call -> {
            audit.append(call.getArgument(0), call.getArgument(1), call.getArgument(2), call.getArgument(3));
            throw new IllegalStateException("owner audit failed");
        }).when(failingAudit).append(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
        var owner = new com.seasonplan.season.application.SeasonLifecycleService(planRepository, transactions, failingAudit, java.time.Clock.systemUTC());
        for (String state : List.of("RITUAL_READY", "ACTIVE", "COMPLETED", "CORRECTION")) {
            UUID id = plan();
            jdbc.update("update season_plans set status = ? where id = ?", state, id);
            var report = new com.seasonplan.season.api.SeasonLifecycle.ReportEvidence(UUID.randomUUID(), id, true, false);
            assertThatThrownBy(() -> {
                if (state.equals("RITUAL_READY")) owner.activate(id, new com.seasonplan.season.api.SeasonLifecycle.RitualEvidence(UUID.randomUUID(), id, true), "actor");
                else if (state.equals("ACTIVE")) owner.complete(id, report, "actor");
                else if (state.equals("COMPLETED")) owner.archive(id, report, "actor");
                else owner.enterEmergency(id, "actor");
            }).hasMessage("owner audit failed");
            assertThat(planRepository.findById(id).orElseThrow().status().name()).isEqualTo(state);
            assertThat(audit.entriesFor(id)).isEmpty();
        }
    }

    @Autowired com.seasonplan.response.api.MetricReactions metricReactions;

    @Test void responseOwnerReplayDeduplicatesPersistedIncidentCorrectionAuditAndNotification() {
        UUID id = plan();
        jdbc.update("update season_plans set status = 'ACTIVE' where id = ?", id);
        var measurement = metric(id, UUID.randomUUID().toString(), "24");
        var spec = com.seasonplan.monitoring.domain.MetricReactionPolicy.evaluate(measurement, "v1", "owner", java.time.Duration.ofMinutes(5), TIME);
        var first = metricReactions.react(id, measurement.id(), spec, "balancer");
        var replay = metricReactions.react(id, measurement.id(), spec, "balancer");
        assertThat(replay).isEqualTo(first);
        assertThat(incidents.forPlan(id)).hasSize(1);
        assertThat(actions.forPlan(id)).hasSize(1);
        assertThat(actions.forPlan(id).get(0).incidentId()).isEqualTo(first.incidentId());
        assertThat(audit.entriesFor(id)).hasSize(3).allMatch(entry -> entry.actorId().equals("balancer"));
        assertThat(planRepository.findById(id).orElseThrow().status().name()).isEqualTo("EMERGENCY");
        assertThat(jdbc.queryForObject("select count(*) from notification_outbox where season_plan_id = ? and event_type = 'CRITICAL_METRIC'", Integer.class, id)).isEqualTo(1);
    }

    @Test void concurrentGlobalKeysAndRitualAttemptsHaveOnlyOneWinner() throws Exception {
        UUID first = plan(), second = plan(); String metricKey = UUID.randomUUID().toString();
        assertThat(race(() -> metrics.save(metric(first, metricKey, "21")), () -> metrics.save(metric(second, metricKey, "22")))).isEqualTo(1);
        String actionKey = UUID.randomUUID().toString();
        assertThat(race(() -> actions.save(CorrectionAction.open(UUID.randomUUID(), first, null, actionKey, "a", "owner", TIME)), () -> actions.save(CorrectionAction.open(UUID.randomUUID(), second, null, actionKey, "b", "owner", TIME)))).isEqualTo(1);
        assertThat(race(() -> rituals.save(ritual(first, 1)), () -> rituals.save(ritual(first, 2)))).isEqualTo(1);
    }
    private int race(Runnable a, Runnable b) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(2); CountDownLatch start = new CountDownLatch(1);
        try {
            Callable<Boolean> first = () -> { start.await(); try { a.run(); return true; } catch (RuntimeException ex) { return false; } };
            Callable<Boolean> second = () -> { start.await(); try { b.run(); return true; } catch (RuntimeException ex) { return false; } };
            Future<Boolean> x = pool.submit(first), y = pool.submit(second); start.countDown();
            return (x.get(15, TimeUnit.SECONDS) ? 1 : 0) + (y.get(15, TimeUnit.SECONDS) ? 1 : 0);
        } finally { pool.shutdownNow(); }
    }
}
