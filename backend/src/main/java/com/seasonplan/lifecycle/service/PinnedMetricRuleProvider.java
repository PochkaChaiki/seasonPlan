package com.seasonplan.lifecycle.service;

import com.seasonplan.lifecycle.domain.MetricDefinition;
import com.seasonplan.lifecycle.repository.SeasonPlanRepository;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.UUID;

public final class PinnedMetricRuleProvider implements MetricRuleProvider {
    private final SeasonPlanRepository plans;
    public PinnedMetricRuleProvider(SeasonPlanRepository plans) { this.plans = plans; }
    @Override public MetricRule forMetric(UUID planId, MetricDefinition metric) {
        var plan = plans.findById(planId).orElseThrow(() -> new IllegalStateException("season plan not found: " + planId));
        var snapshot = plan.executionRules();
        if (snapshot == null) throw new IllegalStateException("Legacy plan has no pinned execution rules; operator remediation required");
        var settings = snapshot.metrics().get(metric.key());
        Double target = plan.targetClimateMetrics().get(metric.key());
        if (settings == null || target == null || !Double.isFinite(target)) throw new IllegalStateException("Plan lacks configured target or rule for " + metric.key());
        return new MetricRule(BigDecimal.valueOf(target), settings.tolerance(), snapshot.version(), settings.owner(), Duration.ofSeconds(settings.responseSeconds()));
    }
}
