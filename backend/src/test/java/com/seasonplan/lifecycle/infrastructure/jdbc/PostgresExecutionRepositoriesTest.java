package com.seasonplan.lifecycle.infrastructure.jdbc;

import com.seasonplan.lifecycle.domain.*;
import com.seasonplan.lifecycle.notification.NotificationOutbox;
import com.seasonplan.lifecycle.repository.*;
import com.seasonplan.lifecycle.service.AuditLogService;
import com.seasonplan.lifecycle.service.CommandTransaction;
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
@ActiveProfiles("test")
@Testcontainers
class PostgresExecutionRepositoriesTest {
    @Container static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:14");
    @DynamicPropertySource static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
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
    @Autowired com.seasonplan.lifecycle.service.MonitoringService monitoring;
    @Autowired com.seasonplan.lifecycle.service.RitualService ritualService;
    @Autowired com.seasonplan.lifecycle.service.IncidentResponseService incidentService;
    @Autowired com.seasonplan.lifecycle.service.RitualReadinessChecker readiness;
    @Autowired com.seasonplan.lifecycle.service.SeasonPlanService seasonPlans;
    @Autowired com.seasonplan.lifecycle.config.ConfiguredExecutionRules configuredRules;
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

    @Test void runtimeCompositionUsesRealRepositoriesAndProviders() {
        assertThat(metrics).isInstanceOf(JdbcClimateMetricRepository.class);
        assertThat(actions).isInstanceOf(JdbcCorrectionActionRepository.class);
        assertThat(incidents).isInstanceOf(JdbcMagicIncidentRepository.class);
        assertThat(rituals).isInstanceOf(JdbcRitualSessionRepository.class);
        assertThat(readiness).isInstanceOf(com.seasonplan.lifecycle.service.ComputedRitualReadinessChecker.class);
        assertThat(org.springframework.test.util.ReflectionTestUtils.getField(monitoring, "metrics")).isSameAs(metrics);
        assertThat(org.springframework.test.util.ReflectionTestUtils.getField(monitoring, "rules")).isInstanceOf(com.seasonplan.lifecycle.service.PinnedMetricRuleProvider.class);
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
        var input = new com.seasonplan.lifecycle.service.MonitoringService.Measurement("sensor", key, "temperature", "CELSIUS", new BigDecimal("20.00"), TIME);
        ClimateMetric first = monitoring.record(plan, input, com.seasonplan.security.identity.ActorRole.CLIMATE_BALANCER, "owner");
        ClimateMetric replay = monitoring.record(plan, input, com.seasonplan.security.identity.ActorRole.CLIMATE_BALANCER, "owner");
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
