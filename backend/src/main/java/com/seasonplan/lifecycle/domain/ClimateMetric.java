package com.seasonplan.lifecycle.domain;

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
    public boolean deviating() { return deviationScore.compareTo(BigDecimal.ONE) >= 0; }
    public boolean critical() { return deviationScore.compareTo(new BigDecimal("2")) >= 0; }
}
