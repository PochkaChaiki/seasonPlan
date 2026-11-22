package com.seasonplan.reference.domain;

import com.seasonplan.reference.api.MetricDefinition;

import java.math.BigDecimal;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** Immutable deployment rules selected when a plan is created. */
public record ExecutionRuleSnapshot(String version, Map<String, MetricSettings> metrics,
                                    Set<String> requiredEvents, Map<String, SeasonRequirements> seasons) {
    public record MetricSettings(BigDecimal tolerance, String owner, long responseSeconds) {
        public MetricSettings {
            if (tolerance == null || tolerance.signum() <= 0 || owner == null || owner.isBlank() || responseSeconds <= 0)
                throw new IllegalArgumentException("positive tolerance, owner and response time are required");
        }
    }
    public record SeasonRequirements(Set<String> categories, Set<String> roles) {
        public SeasonRequirements {
            categories = Set.copyOf(categories); roles = Set.copyOf(roles);
            if (categories.isEmpty() || roles.isEmpty() || categories.stream().anyMatch(String::isBlank) || roles.stream().anyMatch(String::isBlank))
                throw new IllegalArgumentException("season requirements cannot be empty");
        }
    }
    public ExecutionRuleSnapshot {
        if (version == null || version.isBlank()) throw new IllegalArgumentException("rule version is required");
        metrics = Map.copyOf(metrics); requiredEvents = Set.copyOf(requiredEvents); seasons = Map.copyOf(seasons);
        if (requiredEvents.isEmpty() || requiredEvents.stream().anyMatch(String::isBlank)) throw new IllegalArgumentException("ritual events are required");
        for (MetricDefinition metric : MetricDefinition.values()) Objects.requireNonNull(metrics.get(metric.key()), "metric settings required: " + metric.key());
        for (String type : Set.of("SPRING", "SUMMER", "AUTUMN", "WINTER")) Objects.requireNonNull(seasons.get(type), "season requirements required: " + type);
    }
}
