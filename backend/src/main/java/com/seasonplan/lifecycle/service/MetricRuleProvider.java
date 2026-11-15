package com.seasonplan.lifecycle.service;

import com.seasonplan.lifecycle.domain.ClimateMetric;
import com.seasonplan.lifecycle.domain.MetricDefinition;
import java.math.BigDecimal;
import java.time.Duration;
import java.util.UUID;

public interface MetricRuleProvider {
    public record MetricRule(BigDecimal target, BigDecimal tolerance, String version, String owner, Duration responseTime) {
        public MetricRule {
            ClimateMetric.score(target, target, tolerance);
            if (version == null || version.isBlank() || owner == null || owner.isBlank()
                    || responseTime == null || responseTime.isNegative() || responseTime.isZero()) {
                throw new IllegalArgumentException("a versioned metric response rule is required");
            }
        }
    }
    MetricRule forMetric(UUID planId, MetricDefinition metric);
}
