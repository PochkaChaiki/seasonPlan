package com.seasonplan.lifecycle.domain;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * The authoritative executable transition table for the SeasonPlan lifecycle.
 * Its business rationale, actors and guards are recorded in
 * docs/architecture/ST-00-specification-resolution.md.
 */
public enum SeasonPlanStatus {
    DRAFT,
    SUBMITTED,
    VERIFIED,
    APPROVED,
    RITUAL_READY,
    ACTIVE,
    COMPLETED,
    ARCHIVED;

    private static final Map<SeasonPlanStatus, Set<SeasonPlanStatus>> ALLOWED_TARGETS = allowedTargets();

    public boolean canTransitionTo(SeasonPlanStatus target) {
        return target != null && ALLOWED_TARGETS.get(this).contains(target);
    }

    private static Map<SeasonPlanStatus, Set<SeasonPlanStatus>> allowedTargets() {
        Map<SeasonPlanStatus, Set<SeasonPlanStatus>> targets = new EnumMap<>(SeasonPlanStatus.class);
        targets.put(DRAFT, EnumSet.of(SUBMITTED));
        targets.put(SUBMITTED, EnumSet.of(VERIFIED, DRAFT));
        targets.put(VERIFIED, EnumSet.of(APPROVED, DRAFT));
        targets.put(APPROVED, EnumSet.of(RITUAL_READY));
        targets.put(RITUAL_READY, EnumSet.of(ACTIVE));
        targets.put(ACTIVE, EnumSet.of(COMPLETED));
        targets.put(COMPLETED, EnumSet.of(ARCHIVED));
        targets.put(ARCHIVED, EnumSet.noneOf(SeasonPlanStatus.class));
        return Map.copyOf(targets);
    }
}
