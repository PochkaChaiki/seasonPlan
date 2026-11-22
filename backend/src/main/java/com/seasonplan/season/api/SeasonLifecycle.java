package com.seasonplan.season.api;

import java.util.Objects;
import java.util.UUID;

/** Season-owned transitions. Calls join the same season command transaction as the caller. */
public interface SeasonLifecycle {
    /** Facts from the validated, persisted ritual outcome, not a request's assertion. */
    record RitualEvidence(UUID ritualId, UUID seasonPlanId, boolean successful) {
        public RitualEvidence { Objects.requireNonNull(ritualId); Objects.requireNonNull(seasonPlanId); }
    }
    /** Facts from a validated report snapshot and its approval record. */
    record ReportEvidence(UUID reportId, UUID seasonPlanId, boolean approved, boolean criticalIncidents) {
        public ReportEvidence { Objects.requireNonNull(reportId); Objects.requireNonNull(seasonPlanId); }
    }
    void activate(UUID planId, RitualEvidence evidence, String actorId);
    void complete(UUID planId, ReportEvidence evidence, String actorId);
    void archive(UUID planId, ReportEvidence evidence, String actorId);
    /** Called only after the response domain has established a critical incident. */
    void enterEmergency(UUID planId, String actorId);
}
