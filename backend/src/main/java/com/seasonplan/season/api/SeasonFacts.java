package com.seasonplan.season.api;

import com.seasonplan.season.domain.SeasonPlanStatus;
import com.seasonplan.season.domain.ApprovalCondition;
import com.seasonplan.reference.domain.ExecutionRuleSnapshot;
import com.seasonplan.shared.domain.TimeWindow;
import java.time.Instant;
import java.util.*;

/** Immutable persisted season snapshot. Status, conditions, windows and pinned rules are immutable values. */
public record SeasonFacts(UUID id, String seasonType, List<String> targetTerritories, List<TimeWindow> timeWindows,
                          Map<String, String> naturalPhenomenonParameters, Map<String, Double> targetClimateMetrics,
                          SeasonPlanStatus status, String approvalComment, List<ChecklistFact> readinessChecklist,
                          String createdBy, String updatedBy, Instant createdAt, Instant updatedAt,
                          List<ApprovalCondition> approvalConditions, ExecutionRuleSnapshot executionRules, long materialRevision) {
    public record ChecklistFact(String key, boolean complete) { }
    public SeasonFacts {
        // Preserve invalid draft values for domain validation, including null map values and list elements.
        targetTerritories = Collections.unmodifiableList(new ArrayList<>(targetTerritories));
        timeWindows = Collections.unmodifiableList(new ArrayList<>(timeWindows));
        naturalPhenomenonParameters = Collections.unmodifiableMap(new LinkedHashMap<>(naturalPhenomenonParameters));
        targetClimateMetrics = Collections.unmodifiableMap(new LinkedHashMap<>(targetClimateMetrics));
        readinessChecklist = List.copyOf(readinessChecklist);
        approvalConditions = List.copyOf(approvalConditions);
    }
}
