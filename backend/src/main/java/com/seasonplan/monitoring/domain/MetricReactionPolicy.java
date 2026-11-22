package com.seasonplan.monitoring.domain;

import com.seasonplan.response.api.MetricReactions;
import com.seasonplan.response.api.MetricReactions.IncidentSpec;
import com.seasonplan.response.api.MetricReactions.CorrectionSpec;
import com.seasonplan.response.api.MetricReactions.AlertSpec;
import com.seasonplan.response.api.MetricReactions.Reaction;
import java.time.*;
import java.util.*;

/** Existing metric response rules. Returns decisions/specifications, never performs side effects. */
public final class MetricReactionPolicy {
    private MetricReactionPolicy() { }
    public static void requireRecordingState(com.seasonplan.season.domain.SeasonPlanStatus status) {
        if (!java.util.EnumSet.of(com.seasonplan.season.domain.SeasonPlanStatus.ACTIVE, com.seasonplan.season.domain.SeasonPlanStatus.CORRECTION,
                com.seasonplan.season.domain.SeasonPlanStatus.EMERGENCY).contains(status))
            throw new com.seasonplan.shared.domain.DomainValidationException(Map.of("status", List.of("operation is not allowed in " + status)));
    }
    public static Reaction evaluate(ClimateMetric metric, String version, String owner, Duration responseTime, Instant now) {
        IncidentSpec incident = null;
        CorrectionSpec correction = null;
        AlertSpec criticalAlert = null, correctionAlert = null;
        if (metric.critical()) {
            incident = new IncidentSpec(MetricReactions.IncidentType.CLIMATE, MetricReactions.Severity.CRITICAL,
                    "Критическое отклонение " + metric.definition().key(), "deviation_score=" + metric.deviationScore());
            criticalAlert = new AlertSpec("CRITICAL_METRIC", Set.copyOf(List.of("CHIEF_GUARDIAN", "MAGIC_ANOMALY_CONTROLLER", owner)),
                    "Критическое отклонение: " + metric.deviationScore());
        }
        if (metric.deviating()) {
            correction = new CorrectionSpec(metric.seasonPlanId() + ":" + metric.id() + ":" + version,
                    "Устранить отклонение " + metric.definition().key(), owner, now.plus(responseTime));
            if (!metric.critical()) correctionAlert = new AlertSpec("CORRECTION_ASSIGNED", Set.of(owner), correction.description());
        }
        return new Reaction(incident, correction, criticalAlert, correctionAlert);
    }
}
