package com.seasonplan.season.application;

import com.seasonplan.season.api.SeasonFacts;
import com.seasonplan.season.api.SeasonQueries;
import com.seasonplan.season.application.port.SeasonPlanRepository;
import com.seasonplan.season.domain.SeasonPlan;
import com.seasonplan.shared.application.NotFoundException;
import java.util.List;
import java.util.UUID;

/** Separate query owner avoids dependencies on command coordinators during fact collection. */
public final class SeasonQueryService implements SeasonQueries {
    private final SeasonPlanRepository plans;
    public SeasonQueryService(SeasonPlanRepository plans) { this.plans = plans; }
    @Override public SeasonFacts get(UUID id) {
        return SeasonFactMapper.facts(plans.findById(id).orElseThrow(() -> new NotFoundException("season plan not found: " + id)));
    }
    @Override public List<com.seasonplan.season.domain.MaterialRevision> materialHistory(UUID id) { get(id); return List.copyOf(plans.materialHistory(id)); }
    @Override public List<SeasonFacts> listPlans() { return plans.findAll().stream().map(SeasonFactMapper::facts).toList(); }
}
