package com.seasonplan.lifecycle.domain;

import com.seasonplan.reporting.domain.SeasonReport;
import com.seasonplan.ritual.domain.RitualSession;
import com.seasonplan.season.domain.SeasonPlan;
import com.seasonplan.season.domain.SeasonPlanStatus;

import org.junit.jupiter.api.Test;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import static org.assertj.core.api.Assertions.*;

class ReportDomainTest {
    @Test void approvalCannotBeForgedByOmittingBlockersFromAnIncompleteSnapshot() {
        var snapshot = new SeasonReport.Snapshot("SPRING", "v1", List.of("north"), Map.of(), Map.of("temperature", 20.0),
                List.of(), List.of(), List.of(), List.of(), List.of(), null);
        assertThatThrownBy(() -> new SeasonReport(UUID.randomUUID(), UUID.randomUUID(), 1, "Итоги", snapshot, List.of(),
                SeasonReport.Status.APPROVED, "planner", Instant.EPOCH, "guardian", Instant.EPOCH))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test void activationRequiresSuccessfulSessionForTheSamePlan() {
        SeasonPlan plan = SeasonPlan.rehydrate(UUID.randomUUID(), "SPRING", List.of(), List.of(), Map.of(), Map.of(),
                SeasonPlanStatus.RITUAL_READY, null, List.of(), "planner", "planner", Instant.EPOCH, Instant.EPOCH, new com.seasonplan.season.domain.MaterialRevisionStamp(0, "system:migration", java.time.Instant.EPOCH, com.seasonplan.season.domain.MaterialRevisionStamp.Reason.LEGACY_BASELINE));
        RitualSession other = RitualSession.start(UUID.randomUUID(), UUID.randomUUID(), 1, "coordinator", Instant.EPOCH,
                List.of(new RitualSession.Participant("fairy", "LEAD", Instant.EPOCH))).succeed(Instant.EPOCH);
        assertThatThrownBy(() -> plan.activate(new com.seasonplan.season.api.SeasonLifecycle.RitualEvidence(other.id(), other.seasonPlanId(), other.result() == RitualSession.Result.SUCCESS), "coordinator", Instant.EPOCH)).isInstanceOf(IllegalArgumentException.class);
        assertThat(plan.status()).isEqualTo(SeasonPlanStatus.RITUAL_READY);
    }
}
