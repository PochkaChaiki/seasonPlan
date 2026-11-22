package com.seasonplan.monitoring.application;

import com.seasonplan.monitoring.application.port.MetricRuleProvider;
import com.seasonplan.monitoring.domain.ClimateMetric;
import com.seasonplan.monitoring.domain.MetricReactionPolicy;
import com.seasonplan.response.api.MetricReactions;
import com.seasonplan.reference.api.MetricDefinition;
import com.seasonplan.season.api.CommandTransaction;

import com.seasonplan.audit.api.AuditAction;
import com.seasonplan.audit.api.AuditTrail;
import com.seasonplan.identity.api.LifecycleAccessPolicy;
import com.seasonplan.identity.api.LifecyclePermission;

import com.seasonplan.season.api.SeasonQueries;
import com.seasonplan.monitoring.application.port.ClimateMetricRepository;

import com.seasonplan.identity.api.ActorRole;


import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Manual/sensor measurements, idempotent deviation responses and critical warnings. */
public class MonitoringService {
    public record Measurement(String source, String eventId, String metric, String unit, BigDecimal value, Instant observedAt) { }
    private final SeasonQueries seasons;
    private final ClimateMetricRepository metrics;
    private final MetricRuleProvider rules;
    private final MetricReactions reactions;
    private final CommandTransaction transactions;
    private final AuditTrail audit;
    private final Clock clock;

    public MonitoringService(SeasonQueries seasons, ClimateMetricRepository metrics, MetricRuleProvider rules,
                             MetricReactions reactions, CommandTransaction transactions, AuditTrail audit, Clock clock) {
        this.seasons = seasons; this.metrics = metrics; this.rules = rules; this.reactions = reactions;
        this.transactions = transactions; this.audit = audit; this.clock = clock;
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
        seasons.get(planId);
        return metrics.forPlan(planId).stream().sorted(java.util.Comparator.comparing(ClimateMetric::observedAt).thenComparing(ClimateMetric::id)).toList();
    }
    private ClimateMetric recordInsideCommand(UUID planId, Measurement input, String actorId) {
        MetricReactionPolicy.requireRecordingState(seasons.get(planId).status());
        MetricDefinition definition = ClimateMetric.definitionFor(input.metric(), input.unit());
        var existing = metrics.byImportKey(input.source(), input.eventId());
        if (existing.isPresent()) {
            ClimateMetric old = existing.get();
            old.requireSameObservation(planId, definition, input.value(), input.observedAt());
            return old;
        }
        var rule = rules.forMetric(planId, definition);
        ClimateMetric metric = new ClimateMetric(UUID.randomUUID(), planId, input.source(), input.eventId(), definition,
                input.value(), rule.target(), rule.tolerance(), null, input.observedAt());
        var reaction = MetricReactionPolicy.evaluate(metric, rule.version(), rule.owner(), rule.responseTime(), clock.instant());
        reactions.react(planId, metric.id(), reaction, actorId);
        metrics.save(metric);
        audit.append(planId, AuditAction.CLIMATE_METRIC_RECORDED, actorId, metric.id().toString());
        return metric;
    }
}
