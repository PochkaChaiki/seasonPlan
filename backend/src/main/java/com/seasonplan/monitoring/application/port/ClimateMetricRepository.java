package com.seasonplan.monitoring.application.port;

import com.seasonplan.monitoring.domain.ClimateMetric;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ClimateMetricRepository {
    Optional<ClimateMetric> byImportKey(String source, String eventId);
    List<ClimateMetric> forPlan(UUID planId);
    /** (source, sourceEventId) is globally unique. */
    void save(ClimateMetric metric);
}
