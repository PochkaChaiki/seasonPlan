package com.seasonplan.lifecycle.application.execution;

import com.seasonplan.lifecycle.application.UseCaseSupport;
import com.seasonplan.lifecycle.application.port.*;
import com.seasonplan.lifecycle.domain.*;
import com.seasonplan.lifecycle.application.shared.*;
import com.seasonplan.lifecycle.service.AuditLogService;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;

/** UC-4: incident registration/response and callable (not scheduled here) overdue escalation. */
public class IncidentResponseService {
    private final SeasonPlanRepository plans;
    private final ExecutionPorts.Incidents incidents;
    private final ExecutionPorts.Corrections actions;
    private final ExecutionPorts.IncidentRules rules;
    private final CommandTransaction transactions;
    private final NotificationOutbox outbox;
    private final AuditLogService audit;
    private final Clock clock;

    public IncidentResponseService(SeasonPlanRepository plans, ExecutionPorts.Incidents incidents, ExecutionPorts.Corrections actions,
                                   ExecutionPorts.IncidentRules rules, CommandTransaction transactions, NotificationOutbox outbox,
                                   AuditLogService audit, Clock clock) {
        this.plans = plans; this.incidents = incidents; this.actions = actions; this.rules = rules;
        this.transactions = transactions; this.outbox = outbox; this.audit = audit; this.clock = clock;
    }
    public boolean hasCriticalCondition(UUID planId) { return incidents.forPlan(planId).stream().anyMatch(MagicIncident::critical); }
    public MagicIncident registerIncident(UUID planId, MagicIncident.Type type, String cause, String consequences, ActorRole role, String actorId) {
        requireResponder(role);
        return transactions.execute(planId, () -> {
            mutablePlan(planId);
            MagicIncident incident = new MagicIncident(UUID.randomUUID(), planId, type, rules.severityFor(type), MagicIncident.Status.OPEN,
                    cause, consequences, actorId, clock.instant());
            incidents.save(incident);
            audit.append(planId, AuditAction.MAGIC_INCIDENT_REGISTERED, actorId, incident.id().toString());
            notifyIncident(incident, "registered");
            return incident;
        });
    }
    public MagicIncident overrideSeverity(UUID id, MagicIncident.Severity severity, String reason, ActorRole role, String actorId) {
        ValidationSupport.requireRole(role, ActorRole.CHIEF_GUARDIAN);
        if (reason == null || reason.isBlank()) UseCaseSupport.reject("reason", "severity override requires a reason");
        return transactions.execute(incident(id).seasonPlanId(), () -> {
            MagicIncident current = incident(id); mutablePlan(current.seasonPlanId());
            MagicIncident changed = current.withSeverity(severity); incidents.save(changed);
            audit.append(current.seasonPlanId(), AuditAction.INCIDENT_SEVERITY_CHANGED, actorId, reason);
            notifyIncident(changed, "severity:" + severity);
            return changed;
        });
    }
    public CorrectionAction createAction(UUID planId, UUID incidentId, String description, String owner, Instant deadline, ActorRole role, String actorId) {
        requireResponder(role);
        return transactions.execute(planId, () -> {
            mutablePlan(planId);
            if (incidentId != null && !incident(incidentId).seasonPlanId().equals(planId)) UseCaseSupport.reject("incident", "incident belongs to another plan");
            UUID id = UUID.randomUUID();
            CorrectionAction action = CorrectionAction.open(id, planId, incidentId, "manual:" + id, description, owner, deadline);
            actions.save(action);
            audit.append(planId, AuditAction.CORRECTION_ACTION_CREATED, actorId, id.toString());
            outbox.enqueue(new NotificationOutbox.Event("correction:" + action.id(), planId, "CORRECTION_ASSIGNED", Set.of(owner),
                    Set.of(NotificationOutbox.Channel.IN_APP, NotificationOutbox.Channel.EMAIL), description, clock.instant()));
            return action;
        });
    }
    public CorrectionAction changeAction(UUID id, CorrectionAction.Status status, String reason, ActorRole role, String actorId) {
        ValidationSupport.requireAnyRole(role, ActorRole.CHIEF_GUARDIAN, ActorRole.MAGIC_ANOMALY_CONTROLLER, ActorRole.CLIMATE_BALANCER,
                ActorRole.TALENT_COORDINATOR, ActorRole.FAIRY_PARTICIPANT);
        return transactions.execute(action(id).seasonPlanId(), () -> {
            CorrectionAction current = action(id); mutablePlan(current.seasonPlanId());
            if ((role == ActorRole.FAIRY_PARTICIPANT || role == ActorRole.TALENT_COORDINATOR) && !current.owner().equals(actorId)) {
                throw new AccessDeniedForRoleException(role);
            }
            CorrectionAction changed = current.transition(status, reason); actions.save(changed);
            audit.append(current.seasonPlanId(), AuditAction.CORRECTION_ACTION_CHANGED, actorId, status.name());
            return changed;
        });
    }
    public MagicIncident resolveIncident(UUID id, ActorRole role, String actorId) {
        requireResponder(role);
        return transactions.execute(incident(id).seasonPlanId(), () -> {
            MagicIncident current = incident(id); mutablePlan(current.seasonPlanId());
            if (actions.forPlan(current.seasonPlanId()).stream().anyMatch(action -> id.equals(action.incidentId()) && !action.terminal())) {
                UseCaseSupport.reject("correctionActions", "linked correction actions are incomplete");
            }
            MagicIncident resolved = current.resolve(); incidents.save(resolved);
            audit.append(current.seasonPlanId(), AuditAction.MAGIC_INCIDENT_RESOLVED, actorId, id.toString());
            return resolved;
        });
    }
    public MagicIncident reopenIncident(UUID id, ActorRole role, String actorId) {
        requireResponder(role);
        return transactions.execute(incident(id).seasonPlanId(), () -> {
            MagicIncident current = incident(id); mutablePlan(current.seasonPlanId());
            MagicIncident reopened = current.reopen(); incidents.save(reopened);
            audit.append(current.seasonPlanId(), AuditAction.MAGIC_INCIDENT_REOPENED, actorId, id.toString());
            notifyIncident(reopened, "reopened");
            return reopened;
        });
    }
    public void escalateOverdue() {
        for (CorrectionAction candidate : actions.allOpen()) {
            transactions.execute(candidate.seasonPlanId(), () -> {
                CorrectionAction current = action(candidate.id());
                if (current.overdue(clock.instant())) {
                    long level = Duration.between(current.deadline(), clock.instant()).toDays() + 1;
                    outbox.enqueue(new NotificationOutbox.Event("overdue:" + current.id() + ":" + level + ":" + current.deadline(),
                            current.seasonPlanId(), "CORRECTION_OVERDUE", Set.copyOf(java.util.List.of("CHIEF_GUARDIAN", "MAGIC_ANOMALY_CONTROLLER", current.owner())),
                            Set.of(NotificationOutbox.Channel.IN_APP, NotificationOutbox.Channel.EMAIL), "Просрочено: " + current.description(), clock.instant()));
                }
                return null;
            });
        }
    }
    private void notifyIncident(MagicIncident incident, String event) {
        outbox.enqueue(new NotificationOutbox.Event("incident:" + incident.id() + ":" + event + ":" + UUID.randomUUID(), incident.seasonPlanId(), "INCIDENT_REGISTERED",
                Set.of("CHIEF_GUARDIAN", "MAGIC_ANOMALY_CONTROLLER"), Set.of(NotificationOutbox.Channel.IN_APP, NotificationOutbox.Channel.EMAIL),
                incident.cause(), clock.instant()));
    }
    private void mutablePlan(UUID id) { UseCaseSupport.plan(plans, id, SeasonPlanStatus.RITUAL_READY, SeasonPlanStatus.ACTIVE); }
    private MagicIncident incident(UUID id) { return incidents.find(id).orElseThrow(() -> new NotFoundException("incident not found: " + id)); }
    private CorrectionAction action(UUID id) { return actions.find(id).orElseThrow(() -> new NotFoundException("correction action not found: " + id)); }
    private void requireResponder(ActorRole role) {
        ValidationSupport.requireAnyRole(role, ActorRole.CHIEF_GUARDIAN, ActorRole.MAGIC_ANOMALY_CONTROLLER, ActorRole.CLIMATE_BALANCER);
    }
}
