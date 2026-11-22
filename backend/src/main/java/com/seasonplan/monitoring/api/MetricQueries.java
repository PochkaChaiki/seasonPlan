package com.seasonplan.monitoring.api;

import com.seasonplan.monitoring.domain.ClimateMetric;
import java.util.List;
import java.util.UUID;

/** Frozen lists of supported immutable ClimateMetric records, including nested immutable values. */
public interface MetricQueries {
    List<ClimateMetric> forPlan(UUID planId);
}
