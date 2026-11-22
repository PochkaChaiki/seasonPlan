package com.seasonplan.ritual.api;

import com.seasonplan.pollen.api.ReservationFacts;
import com.seasonplan.response.domain.MagicIncident;
import com.seasonplan.season.api.SeasonFacts;
import com.seasonplan.work.api.AssignmentFacts;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Public pure-decision contract implemented by the ritual domain. No repositories or external calls. */
public interface ReadinessEvaluation {
    record TaskFacts(com.seasonplan.work.api.TaskFacts task, List<AssignmentFacts> assignments) {
        public TaskFacts { assignments = List.copyOf(assignments); }
    }
    void requireCatalog(SeasonFacts plan);
    boolean requiresResourceAuthority(UUID planId, Optional<ReservationFacts> latest);
    RitualReadinessChecker.Readiness evaluate(SeasonFacts plan, Optional<ReservationFacts> latest,
            Optional<ReservationFacts> authoritative, List<TaskFacts> tasks, List<MagicIncident> incidents);
    void legacyRecordsUnavailable();
}
