package com.seasonplan.lifecycle.application;

import com.seasonplan.lifecycle.domain.*;
import com.seasonplan.lifecycle.application.port.SeasonPlanRepository;
import com.seasonplan.lifecycle.application.shared.NotFoundException;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.UUID;

public final class UseCaseSupport {
    private UseCaseSupport() { }
    public static SeasonPlan plan(SeasonPlanRepository plans, UUID id, SeasonPlanStatus... allowed) {
        SeasonPlan plan = plans.findById(id).orElseThrow(() -> new NotFoundException("season plan not found: " + id));
        if (allowed.length > 0 && Arrays.stream(allowed).noneMatch(status -> status == plan.status())) {
            reject("status", "operation is not allowed in " + plan.status());
        }
        return plan;
    }
    public static void reject(String field, String message) {
        LinkedHashMap<String, List<String>> errors = new LinkedHashMap<>();
        errors.put(field, List.of(message));
        throw new DomainValidationException(errors);
    }
    /** Isolate transitions from mutable legacy repository objects until the command commits. */
    public static SeasonPlan copy(SeasonPlan plan) {
        return SeasonPlan.rehydrate(plan.id(), plan.seasonType(), plan.targetTerritories(), plan.timeWindows(),
                plan.naturalPhenomenonParameters(), plan.targetClimateMetrics(), plan.status(), plan.approvalComment(),
                plan.readinessChecklist(), plan.createdBy(), plan.updatedBy(), plan.createdAt(), plan.updatedAt(), plan.approvalConditions());
    }
}
