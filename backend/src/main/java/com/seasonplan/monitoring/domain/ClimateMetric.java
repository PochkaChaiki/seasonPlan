package com.seasonplan.monitoring.domain;

import com.seasonplan.reference.api.MetricDefinition;
import com.seasonplan.shared.domain.ExecutionTime;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record ClimateMetric(UUID id, UUID seasonPlanId, String source, String sourceEventId,
                            MetricDefinition definition, BigDecimal value, BigDecimal target,
                            BigDecimal tolerance, BigDecimal deviationScore, Instant observedAt) {
    public ClimateMetric {
        Objects.requireNonNull(id); Objects.requireNonNull(seasonPlanId); Objects.requireNonNull(definition);
        Objects.requireNonNull(observedAt);
        observedAt = ExecutionTime.canonical(observedAt);
        if (source == null || source.isBlank() || sourceEventId == null || sourceEventId.isBlank()) {
            throw new IllegalArgumentException("metric source and event id are required");
        }
        deviationScore = score(value, target, tolerance);
    }
    public static BigDecimal score(BigDecimal value, BigDecimal target, BigDecimal tolerance) {
        Objects.requireNonNull(value); Objects.requireNonNull(target);
        if (tolerance == null || tolerance.signum() <= 0) throw new IllegalArgumentException("tolerance must be positive");
        return value.subtract(target).abs().divide(tolerance, 4, RoundingMode.HALF_UP);
    }
    public static MetricDefinition definitionFor(String metric, String unit) {
        MetricDefinition definition = MetricDefinition.forKey(metric);
        if (!definition.unit().equals(unit)) throw new IllegalArgumentException("unit does not match metric catalog");
        return definition;
    }
    public void requireSameObservation(UUID planId, MetricDefinition metric, BigDecimal observedValue, Instant observedTime) {
        if (!seasonPlanId.equals(planId) || definition != metric || value.compareTo(observedValue) != 0
                || !observedAt.equals(ExecutionTime.canonical(observedTime))) {
            throw new IllegalArgumentException("import key reused for different measurement");
        }
    }
    public boolean deviating() { return deviationScore.compareTo(BigDecimal.ONE) >= 0; }
    public boolean critical() { return deviationScore.compareTo(new BigDecimal("2")) >= 0; }
}
