package com.seasonplan.season.application;

import com.seasonplan.season.api.SeasonFacts;
import com.seasonplan.season.api.SeasonDraft;
import com.seasonplan.season.domain.SeasonPlan;

final class SeasonFactMapper {
    private SeasonFactMapper() { }
    static SeasonPlan draft(SeasonDraft input) {
        var draft = new SeasonPlan();
        draft.setSeasonType(input.seasonType()); draft.setTargetTerritories(input.targetTerritories());
        draft.setTimeWindows(input.timeWindows()); draft.setNaturalPhenomenonParameters(input.naturalPhenomenonParameters());
        draft.setTargetClimateMetrics(input.targetClimateMetrics());
        return draft;
    }
    static SeasonFacts facts(SeasonPlan plan) {
        return new SeasonFacts(plan.id(), plan.seasonType(), plan.targetTerritories(), plan.timeWindows(),
                plan.naturalPhenomenonParameters(), plan.targetClimateMetrics(), plan.status(), plan.approvalComment(),
                plan.readinessChecklist().stream().map(item -> new SeasonFacts.ChecklistFact(item.key(), item.complete())).toList(),
                plan.createdBy(), plan.updatedBy(), plan.createdAt(), plan.updatedAt(), plan.approvalConditions(), plan.executionRules(), plan.materialRevision());
    }
}
