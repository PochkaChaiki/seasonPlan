package com.seasonplan.lifecycle.service;

import com.seasonplan.monitoring.application.MonitoringService;
import com.seasonplan.response.application.IncidentResponseService;
import com.seasonplan.response.domain.CorrectionAction;
import com.seasonplan.response.domain.MagicIncident;
import com.seasonplan.season.domain.SeasonPlanStatus;
import com.seasonplan.shared.domain.DomainValidationException;

import com.seasonplan.identity.api.ActorRole;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.util.List;
import static org.assertj.core.api.Assertions.*;

class EmergencyTransitionTest {
    @Test void aRunningRitualCanRecordEmergencyDiagnosticsAndExplicitFailureButNotSuccess() {
        var f = new ExecutionTestFixture(SeasonPlanStatus.RITUAL_READY);
        var session = f.rituals.start(f.plan.id(), ActorRole.TALENT_COORDINATOR, "coordinator");
        f.response.registerIncident(f.plan.id(), MagicIncident.Type.RITUAL, "cause", "effect", ActorRole.MAGIC_ANOMALY_CONTROLLER, "controller");
        f.rituals.recordEvent(session.id(), "EMERGENCY_DIAGNOSTIC", "stopped safely", ActorRole.TALENT_COORDINATOR, "coordinator");
        assertThatThrownBy(() -> f.rituals.finish(session.id(), null, null, ActorRole.TALENT_COORDINATOR, "coordinator"))
                .isInstanceOf(com.seasonplan.shared.domain.DomainValidationException.class);
        var failed = f.rituals.finish(session.id(), "EMERGENCY_STOP", "stopped safely", ActorRole.TALENT_COORDINATOR, "coordinator");
        assertThat(failed.result()).isEqualTo(com.seasonplan.ritual.domain.RitualSession.Result.FAILED);
        assertThat(failed.events()).extracting(com.seasonplan.ritual.domain.RitualSession.Event::type).contains("EMERGENCY_DIAGNOSTIC");
        assertThat(f.plans.findById(f.plan.id()).orElseThrow().status()).isEqualTo(SeasonPlanStatus.EMERGENCY);
        assertThat(f.audit.entriesFor(f.plan.id())).extracting(com.seasonplan.audit.api.AuditLogEntry::action)
                .contains(com.seasonplan.audit.api.AuditAction.RITUAL_EVENT_RECORDED, com.seasonplan.audit.api.AuditAction.RITUAL_SESSION_FINISHED)
                .doesNotContain(com.seasonplan.audit.api.AuditAction.SEASON_PLAN_ACTIVATED);
    }

    @Test void criticalRegistrationEntersEmergencyAndMitigationRemainsAvailable() {
        var f = new ExecutionTestFixture(SeasonPlanStatus.ACTIVE);
        var incident = f.response.registerIncident(f.plan.id(), MagicIncident.Type.CLIMATE, "cause", "effect", ActorRole.MAGIC_ANOMALY_CONTROLLER, "controller");
        assertThat(f.plans.findById(f.plan.id()).orElseThrow().status().name()).isEqualTo("EMERGENCY");
        var action = f.response.createAction(f.plan.id(), incident.id(), "stabilize", "controller", f.now.plusSeconds(60), ActorRole.MAGIC_ANOMALY_CONTROLLER, "controller");
        f.response.changeAction(action.id(), CorrectionAction.Status.IN_PROGRESS, null, ActorRole.MAGIC_ANOMALY_CONTROLLER, "controller");
        f.response.changeAction(action.id(), CorrectionAction.Status.DONE, null, ActorRole.MAGIC_ANOMALY_CONTROLLER, "controller");
        f.response.resolveIncident(incident.id(), ActorRole.MAGIC_ANOMALY_CONTROLLER, "controller");
        assertThat(f.plans.findById(f.plan.id()).orElseThrow().status().name()).isEqualTo("EMERGENCY");
        assertThat(f.audit.entriesFor(f.plan.id())).extracting(entry -> entry.action().name()).contains("SEASON_EMERGENCY_ENTERED");
    }

    @Test void criticalMetricBatchEntersEmergencyButContinuesDiagnosticsAndReplay() {
        var f = new ExecutionTestFixture(SeasonPlanStatus.ACTIVE);
        var critical = new MonitoringService.Measurement("sensor", "critical", "temperature", "CELSIUS", new BigDecimal("24"), f.now);
        var normal = new MonitoringService.Measurement("sensor", "normal", "temperature", "CELSIUS", new BigDecimal("20"), f.now);
        assertThat(f.monitoring.importMetrics(f.plan.id(), List.of(critical, normal), ActorRole.CLIMATE_BALANCER, "balancer")).hasSize(2);
        assertThat(f.plans.findById(f.plan.id()).orElseThrow().status().name()).isEqualTo("EMERGENCY");
        f.monitoring.record(f.plan.id(), critical, ActorRole.CLIMATE_BALANCER, "balancer");
        assertThat(f.incidents).hasSize(1);
        assertThat(f.audit.entriesFor(f.plan.id()).stream().filter(entry -> entry.action().name().equals("SEASON_EMERGENCY_ENTERED"))).hasSize(1);
    }

    @Test void escalationAndReopeningCannotBypassEmergency() {
        var f = new ExecutionTestFixture(SeasonPlanStatus.ACTIVE);
        var highRules = new IncidentResponseService(new com.seasonplan.season.application.SeasonQueryService(f.plans), new com.seasonplan.season.application.SeasonLifecycleService(f.plans, f.transactions, f.audit, f.clock), f.incidentPort, f.correctionPort, type -> MagicIncident.Severity.HIGH, f.transactions, f.outbox, f.audit, f.clock);
        var incident = highRules.registerIncident(f.plan.id(), MagicIncident.Type.CLIMATE, "cause", "effect", ActorRole.MAGIC_ANOMALY_CONTROLLER, "controller");
        assertThat(f.plans.findById(f.plan.id()).orElseThrow().status()).isEqualTo(SeasonPlanStatus.ACTIVE);
        highRules.overrideSeverity(incident.id(), MagicIncident.Severity.CRITICAL, "confirmed", ActorRole.CHIEF_GUARDIAN, "chief");
        assertThat(f.plans.findById(f.plan.id()).orElseThrow().status().name()).isEqualTo("EMERGENCY");
        highRules.resolveIncident(incident.id(), ActorRole.MAGIC_ANOMALY_CONTROLLER, "controller");
        // Simulate a future authorized recovery; reopening must reapply the critical guard.
        f.setStatus(SeasonPlanStatus.ACTIVE);
        highRules.reopenIncident(incident.id(), ActorRole.MAGIC_ANOMALY_CONTROLLER, "controller");
        assertThat(f.plans.findById(f.plan.id()).orElseThrow().status().name()).isEqualTo("EMERGENCY");
    }

    @Test void criticalIncidentBlocksRitualEvenAfterSeverityIsReduced() {
        var f = new ExecutionTestFixture(SeasonPlanStatus.RITUAL_READY);
        var incident = f.response.registerIncident(f.plan.id(), MagicIncident.Type.RITUAL, "cause", "effect", ActorRole.MAGIC_ANOMALY_CONTROLLER, "controller");
        assertThat(f.plans.findById(f.plan.id()).orElseThrow().status().name()).isEqualTo("EMERGENCY");
        f.response.overrideSeverity(incident.id(), MagicIncident.Severity.HIGH, "reassessed", ActorRole.CHIEF_GUARDIAN, "chief");
        assertThatThrownBy(() -> f.rituals.start(f.plan.id(), ActorRole.TALENT_COORDINATOR, "coordinator")).isInstanceOf(DomainValidationException.class);
    }
}
