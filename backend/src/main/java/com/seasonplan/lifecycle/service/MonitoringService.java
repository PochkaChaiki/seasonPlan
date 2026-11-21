package com.seasonplan.lifecycle.service;

import com.seasonplan.lifecycle.authorization.LifecycleAccessPolicy;
import com.seasonplan.lifecycle.authorization.LifecyclePermission;

import com.seasonplan.lifecycle.repository.SeasonPlanRepository;
import com.seasonplan.lifecycle.notification.NotificationOutbox;
import com.seasonplan.lifecycle.repository.ClimateMetricRepository;
import com.seasonplan.lifecycle.repository.MagicIncidentRepository;
import com.seasonplan.lifecycle.repository.CorrectionActionRepository;

import com.seasonplan.lifecycle.domain.*;
import com.seasonplan.security.identity.ActorRole;


import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/** Manual/sensor measurements, idempotent deviation responses and critical warnings. */
public class MonitoringService {
    public record Measurement(String source, String eventId, String metric, String unit, BigDecimal value, Instant observedAt) { }
    private final SeasonPlanRepository plans;
    private final ClimateMetricRepository metrics;
    private final MagicIncidentRepository incidents;
    private final CorrectionActionRepository actions;
    private final MetricRuleProvider rules;
    private final NotificationOutbox outbox;
    private final CommandTransaction transactions;
    private final AuditLogService audit;
    private final Clock clock;

    public MonitoringService(SeasonPlanRepository plans, ClimateMetricRepository metrics, MagicIncidentRepository incidents,
                             CorrectionActionRepository actions, MetricRuleProvider rules, NotificationOutbox outbox,
                             CommandTransaction transactions, AuditLogService audit, Clock clock) {
        this.plans = plans; this.metrics = metrics; this.incidents = incidents; this.actions = actions;
        this.rules = rules; this.outbox = outbox; this.transactions = transactions; this.audit = audit; this.clock = clock;
    }
    public ClimateMetric record(UUID planId, Measurement input, ActorRole role, String actorId) {
        LifecycleAccessPolicy.require(role, LifecyclePermission.RECORD_MEASUREMENT);
        return transactions.execute(planId, () -> recordInsideCommand(planId, input, actorId));
    }
    public List<ClimateMetric> importMetrics(UUID planId, List<Measurement> inputs, ActorRole role, String actorId) {
        LifecycleAccessPolicy.require(role, LifecyclePermission.IMPORT_MEASUREMENTS);
        return transactions.execute(planId, () -> inputs.stream().map(input -> recordInsideCommand(planId, input, actorId)).toList());
    }
    public List<ClimateMetric> history(UUID planId) {
        ServiceSupport.plan(plans, planId);
        return metrics.forPlan(planId).stream().sorted(java.util.Comparator.comparing(ClimateMetric::observedAt).thenComparing(ClimateMetric::id)).toList();
    }
    private ClimateMetric recordInsideCommand(UUID planId, Measurement input, String actorId) {
        ServiceSupport.plan(plans, planId, SeasonPlanStatus.ACTIVE);
        MetricDefinition definition = MetricDefinition.forKey(input.metric());
        if (!definition.unit().equals(input.unit())) throw new IllegalArgumentException("unit does not match metric catalog");
        var existing = metrics.byImportKey(input.source(), input.eventId());
        if (existing.isPresent()) {
            ClimateMetric old = existing.get();
            if (!old.seasonPlanId().equals(planId) || old.definition() != definition || old.value().compareTo(input.value()) != 0
                    || !old.observedAt().equals(ExecutionTime.canonical(input.observedAt()))) throw new IllegalArgumentException("import key reused for different measurement");
            return old;
        }
        var rule = rules.forMetric(planId, definition);
        ClimateMetric metric = new ClimateMetric(UUID.randomUUID(), planId, input.source(), input.eventId(), definition,
                input.value(), rule.target(), rule.tolerance(), null, input.observedAt());
        UUID incidentId = null;
        if (metric.critical()) {
            incidentId = UUID.randomUUID();
            incidents.save(new MagicIncident(incidentId, planId, MagicIncident.Type.CLIMATE, MagicIncident.Severity.CRITICAL,
                    MagicIncident.Status.OPEN, "Критическое отклонение " + definition.key(), "deviation_score=" + metric.deviationScore(), actorId, clock.instant()));
            audit.append(planId, AuditAction.MAGIC_INCIDENT_REGISTERED, actorId, incidentId.toString());
            outbox.enqueue(new NotificationOutbox.Event("critical-metric:" + metric.id(), planId, "CRITICAL_METRIC",
                    Set.copyOf(List.of("CHIEF_GUARDIAN", "MAGIC_ANOMALY_CONTROLLER", rule.owner())), Set.of(NotificationOutbox.Channel.IN_APP, NotificationOutbox.Channel.EMAIL),
                    "Критическое отклонение: " + metric.deviationScore(), clock.instant()));
        }
        if (metric.deviating()) {
            // The persisted metric id uniquely identifies its composite import key without delimiter collisions.
            String key = planId + ":" + metric.id() + ":" + rule.version();
            if (actions.byKey(key).isEmpty()) {
                CorrectionAction action = CorrectionAction.open(UUID.randomUUID(), planId, incidentId, key,
                        "Устранить отклонение " + definition.key(), rule.owner(), clock.instant().plus(rule.responseTime()));
                actions.save(action);
                audit.append(planId, AuditAction.CORRECTION_ACTION_CREATED, actorId, action.id().toString());
                if (!metric.critical()) {
                    outbox.enqueue(new NotificationOutbox.Event("correction:" + action.id(), planId, "CORRECTION_ASSIGNED", Set.of(rule.owner()),
                            Set.of(NotificationOutbox.Channel.IN_APP, NotificationOutbox.Channel.EMAIL), action.description(), clock.instant()));
                }
            }
        }
        metrics.save(metric);
        audit.append(planId, AuditAction.CLIMATE_METRIC_RECORDED, actorId, metric.id().toString());
        return metric;
    }
}
