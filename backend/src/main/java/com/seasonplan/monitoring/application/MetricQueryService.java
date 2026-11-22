package com.seasonplan.monitoring.application;

import com.seasonplan.monitoring.api.MetricQueries;
import com.seasonplan.monitoring.application.port.ClimateMetricRepository;
import com.seasonplan.monitoring.domain.ClimateMetric;
import java.util.List;
import java.util.UUID;

public final class MetricQueryService implements MetricQueries {
    private final ClimateMetricRepository records;
    public MetricQueryService(ClimateMetricRepository records) { this.records = records; }
    @Override public List<ClimateMetric> forPlan(UUID planId) { return List.copyOf(records.forPlan(planId)); }
}
