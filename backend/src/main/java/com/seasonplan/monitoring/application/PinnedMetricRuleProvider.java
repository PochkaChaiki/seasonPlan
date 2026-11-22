package com.seasonplan.monitoring.application;

import com.seasonplan.monitoring.application.port.MetricRuleProvider;

import com.seasonplan.reference.api.MetricDefinition;
import com.seasonplan.season.api.SeasonQueries;

import java.util.UUID;

public final class PinnedMetricRuleProvider implements MetricRuleProvider {
    private final SeasonQueries seasons;
    public PinnedMetricRuleProvider(SeasonQueries seasons) { this.seasons = seasons; }
    @Override public MetricRule forMetric(UUID planId, MetricDefinition metric) {
        var plan = seasons.get(planId);
        var selected = com.seasonplan.monitoring.domain.MetricRulePolicy.select(plan, metric);
        return new MetricRule(selected.target(), selected.tolerance(), selected.version(), selected.owner(), selected.responseTime());
    }
}
