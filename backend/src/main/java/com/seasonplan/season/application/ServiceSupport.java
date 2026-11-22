package com.seasonplan.season.application;

import com.seasonplan.season.domain.SeasonPlan;
import com.seasonplan.season.domain.SeasonPlanStatus;
import com.seasonplan.shared.domain.DomainValidationException;

import com.seasonplan.season.application.port.SeasonPlanRepository;
import com.seasonplan.shared.application.NotFoundException;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.UUID;

public final class ServiceSupport {
    private ServiceSupport() { }
    public static SeasonPlan plan(SeasonPlanRepository plans, UUID id, SeasonPlanStatus... allowed) {
        SeasonPlan plan = plans.findById(id).orElseThrow(() -> new NotFoundException("season plan not found: " + id));
        plan.requireOperationalState(allowed);
        return plan;
    }
    public static void reject(String field, String message) {
        LinkedHashMap<String, List<String>> errors = new LinkedHashMap<>();
        errors.put(field, List.of(message));
        throw new DomainValidationException(errors);
    }
    /** Isolate transitions from mutable legacy repository objects until the command commits. */
    public static SeasonPlan copy(SeasonPlan plan) {
        SeasonPlan copy = SeasonPlan.rehydrate(plan.id(), plan.seasonType(), plan.targetTerritories(), plan.timeWindows(),
                plan.naturalPhenomenonParameters(), plan.targetClimateMetrics(), plan.status(), plan.approvalComment(),
                plan.readinessChecklist(), plan.createdBy(), plan.updatedBy(), plan.createdAt(), plan.updatedAt(), plan.approvalConditions(), plan.materialRevisionStamp());
        if (plan.executionRules() != null) copy.pinExecutionRules(plan.executionRules());
        return copy;
    }
}
