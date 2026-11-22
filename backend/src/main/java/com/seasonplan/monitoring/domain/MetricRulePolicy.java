package com.seasonplan.monitoring.domain;

import com.seasonplan.reference.api.MetricDefinition;
import com.seasonplan.season.api.SeasonFacts;
import java.math.BigDecimal;
import java.time.Duration;

/** Select only the rule version and target pinned to the season; never guess missing business data. */
public final class MetricRulePolicy {
    private MetricRulePolicy() { }
    public record Selection(BigDecimal target, BigDecimal tolerance, String version, String owner, Duration responseTime) { }
    public static Selection select(SeasonFacts season, MetricDefinition metric) {
        var snapshot = season.executionRules();
        var targets = season.targetClimateMetrics();
        if (snapshot == null) throw new IllegalStateException("Legacy plan has no pinned execution rules; operator remediation required");
        var settings = snapshot.metrics().get(metric.key());
        Double target = targets.get(metric.key());
        if (settings == null || target == null || !Double.isFinite(target)) throw new IllegalStateException("Plan lacks configured target or rule for " + metric.key());
        return new Selection(BigDecimal.valueOf(target), settings.tolerance(), snapshot.version(), settings.owner(), Duration.ofSeconds(settings.responseSeconds()));
    }
}
