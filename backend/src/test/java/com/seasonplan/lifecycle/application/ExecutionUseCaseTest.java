package com.seasonplan.lifecycle.application;

import com.seasonplan.lifecycle.application.execution.*;
import com.seasonplan.lifecycle.domain.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;

class ExecutionUseCaseTest {
    private UseCaseFixture f;

    @BeforeEach void setUp() { f = new UseCaseFixture(SeasonPlanStatus.RITUAL_READY); }

    @Test void successfulRitualSnapshotsParticipantsAndActivatesOnlyAfterCompletion() {
        RitualSession session = f.rituals.start(f.plan.id(), ActorRole.TALENT_COORDINATOR, "coordinator");
        assertThat(f.plan.status()).isEqualTo(SeasonPlanStatus.RITUAL_READY);
        assertThat(session.participants()).hasSize(1);
        f.rituals.recordEvent(session.id(), "LAUNCH_VALIDATED", "ok", ActorRole.TALENT_COORDINATOR, "coordinator");
        RitualSession completed = f.rituals.finish(session.id(), null, null, ActorRole.TALENT_COORDINATOR, "coordinator");
        assertThat(completed.result()).isEqualTo(RitualSession.Result.SUCCESS);
        assertThat(f.plans.findById(f.plan.id()).orElseThrow().status()).isEqualTo(SeasonPlanStatus.ACTIVE);
        assertThatThrownBy(() -> f.rituals.start(f.plan.id(), ActorRole.TALENT_COORDINATOR, "coordinator"))
                .isInstanceOf(DomainValidationException.class);
        assertThat(f.audit.entriesFor(f.plan.id())).extracting(AuditLogEntry::action)
                .contains(AuditAction.RITUAL_SESSION_STARTED, AuditAction.SEASON_PLAN_ACTIVATED);
    }

    @Test void onlyOneAttemptMayRunAndFailedAttemptCanBeRetried() {
        RitualSession first = f.rituals.start(f.plan.id(), ActorRole.TALENT_COORDINATOR, "coordinator");
        assertThatThrownBy(() -> f.rituals.start(f.plan.id(), ActorRole.TALENT_COORDINATOR, "coordinator"))
                .isInstanceOf(DomainValidationException.class);
        RitualSession failed = f.rituals.finish(first.id(), "START_FAILURE", "Ритуал не выполнен", ActorRole.TALENT_COORDINATOR, "coordinator");
        assertThat(failed.result()).isEqualTo(RitualSession.Result.FAILED);
        assertThat(failed.failureCode()).isEqualTo("START_FAILURE");
        assertThat(f.rituals.start(f.plan.id(), ActorRole.TALENT_COORDINATOR, "coordinator").attempt()).isEqualTo(2);
    }

    @Test void readinessIsRecheckedAtCompletionAndMissingEventsAreNotSuccess() {
        RitualSession first = f.rituals.start(f.plan.id(), ActorRole.TALENT_COORDINATOR, "coordinator");
        assertThat(f.rituals.finish(first.id(), null, null, ActorRole.TALENT_COORDINATOR, "coordinator").result())
                .isEqualTo(RitualSession.Result.FAILED);
        RitualSession second = f.rituals.start(f.plan.id(), ActorRole.TALENT_COORDINATOR, "coordinator");
        f.rituals.recordEvent(second.id(), "LAUNCH_VALIDATED", "ok", ActorRole.TALENT_COORDINATOR, "coordinator");
        f.readinessBlockers = List.of("pollen-confirmed");
        assertThat(f.rituals.finish(second.id(), null, null, ActorRole.TALENT_COORDINATOR, "coordinator").result())
                .isEqualTo(RitualSession.Result.FAILED);
        assertThat(f.plans.findById(f.plan.id()).orElseThrow().status()).isEqualTo(SeasonPlanStatus.RITUAL_READY);
    }

    @Test void participantRevocationBetweenStartAndFinishPreventsActivation() {
        RitualSession session = f.rituals.start(f.plan.id(), ActorRole.TALENT_COORDINATOR, "coordinator");
        f.rituals.recordEvent(session.id(), "LAUNCH_VALIDATED", "ok", ActorRole.TALENT_COORDINATOR, "coordinator");
        f.participants = List.of(new RitualSession.Participant("replacement", "RITUAL_LEAD", f.now));
        assertThat(f.rituals.finish(session.id(), null, null, ActorRole.TALENT_COORDINATOR, "coordinator").result())
                .isEqualTo(RitualSession.Result.FAILED);
    }

    @Test void failureMessageWithoutFailureCodeIsNotTreatedAsSuccess() {
        RitualSession session = f.rituals.start(f.plan.id(), ActorRole.TALENT_COORDINATOR, "coordinator");
        f.rituals.recordEvent(session.id(), "LAUNCH_VALIDATED", "ok", ActorRole.TALENT_COORDINATOR, "coordinator");
        assertThatThrownBy(() -> f.rituals.finish(session.id(), null, "Ошибка", ActorRole.TALENT_COORDINATOR, "coordinator"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test void criticalMetricImportIsIdempotentAndCreatesIncidentActionAndNotification() {
        f.setStatus(SeasonPlanStatus.ACTIVE);
        MonitoringService.Measurement measurement = new MonitoringService.Measurement("sensor", "event-1", "temperature", "CELSIUS",
                new BigDecimal("24"), f.now);
        ClimateMetric metric = f.monitoring.record(f.plan.id(), measurement, ActorRole.CLIMATE_BALANCER, "balancer");
        assertThat(metric.deviationScore()).isEqualByComparingTo("2.0000");
        assertThat(f.monitoring.record(f.plan.id(), measurement, ActorRole.CLIMATE_BALANCER, "balancer").id()).isEqualTo(metric.id());
        assertThat(f.metrics).hasSize(1);
        assertThat(f.incidents).hasSize(1);
        assertThat(f.actions).hasSize(1);
        assertThat(f.events).hasSize(1);
        assertThat(f.response.hasCriticalCondition(f.plan.id())).isTrue();
        assertThat(f.plans.findById(f.plan.id()).orElseThrow().status()).isEqualTo(SeasonPlanStatus.ACTIVE);
    }

    @Test void boundaryDeviationCreatesActionWithoutCriticalOperatingCondition() {
        f.setStatus(SeasonPlanStatus.ACTIVE);
        ClimateMetric metric = f.monitoring.record(f.plan.id(), new MonitoringService.Measurement("manual", UUID.randomUUID().toString(),
                "temperature", "CELSIUS", new BigDecimal("22"), f.now), ActorRole.CLIMATE_BALANCER, "balancer");
        assertThat(metric.deviationScore()).isEqualByComparingTo("1.0000");
        assertThat(f.actions).hasSize(1);
        assertThat(f.incidents).isEmpty();
        assertThat(f.events).extracting(com.seasonplan.lifecycle.application.port.NotificationOutbox.Event::type).contains("CORRECTION_ASSIGNED");
    }

    @Test void criticalNotificationsDeduplicateRecipientsWhenOwnerIsAlsoGuardian() {
        f.setStatus(SeasonPlanStatus.ACTIVE);
        var monitoring = new MonitoringService(f.plans, f.metricPort, f.incidentPort, f.correctionPort,
                (id, metric) -> new com.seasonplan.lifecycle.application.port.ExecutionPorts.MetricRule(new BigDecimal("20"), new BigDecimal("2"),
                        "v1", "CHIEF_GUARDIAN", java.time.Duration.ofHours(1)), f.outbox, f.transactions, f.audit, f.clock);
        monitoring.record(f.plan.id(), new MonitoringService.Measurement("sensor", "owner-event", "temperature", "CELSIUS", new BigDecimal("24"), f.now),
                ActorRole.CLIMATE_BALANCER, "balancer");
        assertThat(f.events.get(0).recipients()).containsExactlyInAnyOrder("CHIEF_GUARDIAN", "MAGIC_ANOMALY_CONTROLLER");
    }

    @Test void everyResolvedThenReopenedIncidentProducesANewNotification() {
        f.setStatus(SeasonPlanStatus.ACTIVE);
        MagicIncident incident = f.response.registerIncident(f.plan.id(), MagicIncident.Type.SECURITY, "Причина", "Последствия",
                ActorRole.MAGIC_ANOMALY_CONTROLLER, "controller");
        for (int i = 0; i < 2; i++) {
            f.response.resolveIncident(incident.id(), ActorRole.MAGIC_ANOMALY_CONTROLLER, "controller");
            f.response.reopenIncident(incident.id(), ActorRole.MAGIC_ANOMALY_CONTROLLER, "controller");
        }
        assertThat(f.events).hasSize(3);
    }

    @Test void invalidMetricAndUnauthorizedCommandsHaveNoSideEffects() {
        f.setStatus(SeasonPlanStatus.ACTIVE);
        assertThatThrownBy(() -> f.monitoring.record(f.plan.id(), new MonitoringService.Measurement("sensor", "bad", "temperature", "PERCENT",
                BigDecimal.ONE, f.now), ActorRole.CLIMATE_BALANCER, "balancer")).isInstanceOf(IllegalArgumentException.class);
        assertThat(f.metrics).isEmpty();
        assertThatThrownBy(() -> f.rituals.start(f.plan.id(), ActorRole.FAIRY_PLANNER, "planner"))
                .isInstanceOf(com.seasonplan.lifecycle.application.shared.AccessDeniedForRoleException.class);
    }

    @Test void distinctCompositeImportKeysCannotCollideInCorrectionDeduplication() {
        f.setStatus(SeasonPlanStatus.ACTIVE);
        f.monitoring.record(f.plan.id(), new MonitoringService.Measurement("a:b", "c", "temperature", "CELSIUS", new BigDecimal("22"), f.now),
                ActorRole.CLIMATE_BALANCER, "balancer");
        f.monitoring.record(f.plan.id(), new MonitoringService.Measurement("a", "b:c", "temperature", "CELSIUS", new BigDecimal("22"), f.now),
                ActorRole.CLIMATE_BALANCER, "balancer");
        assertThat(f.metrics).hasSize(2);
        assertThat(f.actions).hasSize(2);
    }

    @Test void importedHistoryIsOrderedAndReusedKeysCannotChangeData() {
        f.setStatus(SeasonPlanStatus.ACTIVE);
        var newer = new MonitoringService.Measurement("sensor", "newer", "temperature", "CELSIUS", new BigDecimal("20"), f.now);
        var older = new MonitoringService.Measurement("sensor", "older", "temperature", "CELSIUS", new BigDecimal("20"), f.now.minusSeconds(60));
        f.monitoring.importMetrics(f.plan.id(), List.of(newer, older), ActorRole.CLIMATE_BALANCER, "balancer");
        assertThat(f.monitoring.history(f.plan.id())).extracting(ClimateMetric::sourceEventId).containsExactly("older", "newer");
        var changed = new MonitoringService.Measurement("sensor", "newer", "temperature", "CELSIUS", new BigDecimal("21"), f.now);
        assertThatThrownBy(() -> f.monitoring.record(f.plan.id(), changed, ActorRole.CLIMATE_BALANCER, "balancer"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(f.metrics).hasSize(2);
    }

    @Test void criticalIncidentBlocksLaunchAndSeverityOverrideRequiresGuardianAndReason() {
        MagicIncident incident = f.response.registerIncident(f.plan.id(), MagicIncident.Type.CLIMATE, "Причина", "Последствия",
                ActorRole.MAGIC_ANOMALY_CONTROLLER, "controller");
        assertThatThrownBy(() -> f.rituals.start(f.plan.id(), ActorRole.TALENT_COORDINATOR, "coordinator"))
                .isInstanceOf(DomainValidationException.class);
        assertThat(f.sessions).isEmpty();
        assertThatThrownBy(() -> f.response.overrideSeverity(incident.id(), MagicIncident.Severity.LOW, " ", ActorRole.CHIEF_GUARDIAN, "guardian"))
                .isInstanceOf(DomainValidationException.class);
        assertThatThrownBy(() -> f.response.overrideSeverity(incident.id(), MagicIncident.Severity.LOW, "Проверено", ActorRole.MAGIC_ANOMALY_CONTROLLER, "controller"))
                .isInstanceOf(com.seasonplan.lifecycle.application.shared.AccessDeniedForRoleException.class);
        f.response.overrideSeverity(incident.id(), MagicIncident.Severity.HIGH, "Проверено", ActorRole.CHIEF_GUARDIAN, "guardian");
        assertThat(f.response.hasCriticalCondition(f.plan.id())).isFalse();
        assertThat(f.rituals.start(f.plan.id(), ActorRole.TALENT_COORDINATOR, "coordinator").attempt()).isEqualTo(1);
    }

    @Test void incidentResolutionClearsCriticalConditionAndOverdueEscalationIsDeduplicated() {
        f.setStatus(SeasonPlanStatus.ACTIVE);
        MagicIncident incident = f.response.registerIncident(f.plan.id(), MagicIncident.Type.CLIMATE, "Причина", "Последствия",
                ActorRole.MAGIC_ANOMALY_CONTROLLER, "controller");
        CorrectionAction action = f.response.createAction(f.plan.id(), incident.id(), "Устранить", "owner", f.now.minusSeconds(1),
                ActorRole.MAGIC_ANOMALY_CONTROLLER, "controller");
        f.response.escalateOverdue();
        f.response.escalateOverdue();
        assertThat(f.events.stream().filter(event -> event.type().equals("CORRECTION_OVERDUE"))).hasSize(1);
        var nextDay = new IncidentResponseService(f.plans, f.incidentPort, f.correctionPort, type -> MagicIncident.Severity.CRITICAL,
                f.transactions, f.outbox, f.audit, java.time.Clock.offset(f.clock, java.time.Duration.ofDays(1)));
        nextDay.escalateOverdue();
        assertThat(f.events.stream().filter(event -> event.type().equals("CORRECTION_OVERDUE"))).hasSize(2);
        assertThatThrownBy(() -> f.response.resolveIncident(incident.id(), ActorRole.MAGIC_ANOMALY_CONTROLLER, "controller"))
                .isInstanceOf(DomainValidationException.class);
        f.response.changeAction(action.id(), CorrectionAction.Status.IN_PROGRESS, null, ActorRole.MAGIC_ANOMALY_CONTROLLER, "controller");
        f.response.changeAction(action.id(), CorrectionAction.Status.DONE, null, ActorRole.MAGIC_ANOMALY_CONTROLLER, "controller");
        f.response.resolveIncident(incident.id(), ActorRole.MAGIC_ANOMALY_CONTROLLER, "controller");
        assertThat(f.response.hasCriticalCondition(f.plan.id())).isFalse();
    }
}
