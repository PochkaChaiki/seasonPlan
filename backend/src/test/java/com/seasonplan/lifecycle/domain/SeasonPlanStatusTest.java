package com.seasonplan.lifecycle.domain;

import org.junit.jupiter.api.Test;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Executable counterpart of docs/architecture/ST-00-specification-resolution.md,
 * section "SeasonPlan lifecycle transition table".
 */
class SeasonPlanStatusTest {

    private static final Map<SeasonPlanStatus, Set<SeasonPlanStatus>> ALLOWED_TARGETS = transitionTable();

    @Test
    void exposesOnlyTheEightApprovedLifecycleStatuses() {
        assertThat(SeasonPlanStatus.values()).containsExactly(
                SeasonPlanStatus.DRAFT,
                SeasonPlanStatus.SUBMITTED,
                SeasonPlanStatus.VERIFIED,
                SeasonPlanStatus.APPROVED,
                SeasonPlanStatus.RITUAL_READY,
                SeasonPlanStatus.ACTIVE,
                SeasonPlanStatus.COMPLETED,
                SeasonPlanStatus.ARCHIVED
        );
    }

    @Test
    void allowsEveryTransitionInTheAuthoritativeLifecycleTable() {
        ALLOWED_TARGETS.forEach((source, targets) ->
                targets.forEach(target -> assertThat(source.canTransitionTo(target))
                        .as("%s -> %s", source, target)
                        .isTrue()));
    }

    @Test
    void forbidsEveryTransitionOutsideTheAuthoritativeLifecycleTable() {
        for (SeasonPlanStatus source : SeasonPlanStatus.values()) {
            for (SeasonPlanStatus target : SeasonPlanStatus.values()) {
                if (!ALLOWED_TARGETS.get(source).contains(target)) {
                    assertThat(source.canTransitionTo(target))
                            .as("%s -> %s", source, target)
                            .isFalse();
                }
            }
        }
    }

    private static Map<SeasonPlanStatus, Set<SeasonPlanStatus>> transitionTable() {
        Map<SeasonPlanStatus, Set<SeasonPlanStatus>> table = new EnumMap<>(SeasonPlanStatus.class);
        table.put(SeasonPlanStatus.DRAFT, EnumSet.of(SeasonPlanStatus.SUBMITTED));
        table.put(SeasonPlanStatus.SUBMITTED, EnumSet.of(SeasonPlanStatus.VERIFIED, SeasonPlanStatus.DRAFT));
        table.put(SeasonPlanStatus.VERIFIED, EnumSet.of(SeasonPlanStatus.APPROVED, SeasonPlanStatus.DRAFT));
        table.put(SeasonPlanStatus.APPROVED, EnumSet.of(SeasonPlanStatus.RITUAL_READY));
        table.put(SeasonPlanStatus.RITUAL_READY, EnumSet.of(SeasonPlanStatus.ACTIVE));
        table.put(SeasonPlanStatus.ACTIVE, EnumSet.of(SeasonPlanStatus.COMPLETED));
        table.put(SeasonPlanStatus.COMPLETED, EnumSet.of(SeasonPlanStatus.ARCHIVED));
        table.put(SeasonPlanStatus.ARCHIVED, EnumSet.noneOf(SeasonPlanStatus.class));
        return table;
    }
}
