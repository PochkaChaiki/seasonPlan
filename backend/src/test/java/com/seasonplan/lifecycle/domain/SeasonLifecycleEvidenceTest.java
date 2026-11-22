package com.seasonplan.lifecycle.domain;

import com.seasonplan.season.api.SeasonLifecycle;
import com.seasonplan.season.domain.*;
import org.junit.jupiter.api.Test;
import java.time.Instant;
import java.util.*;
import static org.assertj.core.api.Assertions.*;

class SeasonLifecycleEvidenceTest {
    private SeasonPlan plan(SeasonPlanStatus status) {
        return SeasonPlan.rehydrate(UUID.randomUUID(), "SPRING", List.of(), List.of(), Map.of(), Map.of(), status,
                null, List.of(), "planner", "planner", Instant.EPOCH, Instant.EPOCH, new com.seasonplan.season.domain.MaterialRevisionStamp(0, "system:migration", java.time.Instant.EPOCH, com.seasonplan.season.domain.MaterialRevisionStamp.Reason.LEGACY_BASELINE));
    }
    @Test void rejectsWrongSeasonAndUnsuccessfulRitualEvidence() {
        var plan = plan(SeasonPlanStatus.RITUAL_READY);
        for (var evidence : List.of(new SeasonLifecycle.RitualEvidence(UUID.randomUUID(), UUID.randomUUID(), true),
                new SeasonLifecycle.RitualEvidence(UUID.randomUUID(), plan.id(), false))) {
            assertThatThrownBy(() -> plan.activate(evidence, "actor", Instant.EPOCH)).isInstanceOf(IllegalArgumentException.class);
            assertThat(plan.status()).isEqualTo(SeasonPlanStatus.RITUAL_READY);
        }
    }
    @Test void rejectsWrongSeasonUnapprovedAndCriticalReportEvidenceForBothTransitions() {
        for (var status : List.of(SeasonPlanStatus.ACTIVE, SeasonPlanStatus.COMPLETED)) {
            var plan = plan(status);
            for (var evidence : List.of(new SeasonLifecycle.ReportEvidence(UUID.randomUUID(), UUID.randomUUID(), true, false),
                    new SeasonLifecycle.ReportEvidence(UUID.randomUUID(), plan.id(), false, false),
                    new SeasonLifecycle.ReportEvidence(UUID.randomUUID(), plan.id(), true, true))) {
                assertThatThrownBy(() -> {
                    if (status == SeasonPlanStatus.ACTIVE) plan.complete(evidence, "actor", Instant.EPOCH);
                    else plan.archive(evidence, "actor", Instant.EPOCH);
                }).isInstanceOf(IllegalArgumentException.class);
                assertThat(plan.status()).isEqualTo(status);
            }
        }
    }
    @Test void acceptsValidatedMatchingEvidence() {
        var plan = plan(SeasonPlanStatus.RITUAL_READY);
        plan.activate(new SeasonLifecycle.RitualEvidence(UUID.randomUUID(), plan.id(), true), "actor", Instant.EPOCH);
        var report = new SeasonLifecycle.ReportEvidence(UUID.randomUUID(), plan.id(), true, false);
        plan.complete(report, "actor", Instant.EPOCH);
        plan.archive(report, "actor", Instant.EPOCH);
        assertThat(plan.status()).isEqualTo(SeasonPlanStatus.ARCHIVED);
    }
}
