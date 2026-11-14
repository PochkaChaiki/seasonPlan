package com.seasonplan.lifecycle.domain;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SeasonPlanDomainTest {
    @Test
    void aggregateProtectsCollectionsFromExternalMutation() {
        SeasonPlan plan = draft();

        assertThatThrownBy(() -> plan.targetTerritories().add("south-grove"))
                .isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> plan.timeWindows().clear())
                .isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> plan.naturalPhenomenonParameters().put("rain", "late"))
                .isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> plan.readinessChecklist().add(new ChecklistItem("roles-reviewed", false)))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void aggregateOwnsLifecycleTransitions() {
        SeasonPlan plan = draft();

        plan.submit("planner-1", Instant.parse("2026-01-01T01:00:00Z"));
        assertThat(plan.status()).isEqualTo(SeasonPlanStatus.SUBMITTED);

        assertThatThrownBy(() -> plan.markRitualReady("coordinator-1", Instant.parse("2026-01-01T02:00:00Z")))
                .isInstanceOf(DomainValidationException.class)
                .satisfies(error -> assertThat(((DomainValidationException) error).fieldErrors())
                        .containsEntry("status", List.of("only APPROVED plans can be marked RITUAL_READY")));

        plan.verify("guardian-1", Instant.parse("2026-01-01T02:30:00Z"));
        plan.approve("approved", "guardian-1", Instant.parse("2026-01-01T03:00:00Z"));
        plan.markRitualReady("coordinator-1", Instant.parse("2026-01-01T04:00:00Z"));

        assertThat(plan.status()).isEqualTo(SeasonPlanStatus.RITUAL_READY);
        assertThat(plan.updatedBy()).isEqualTo("coordinator-1");
    }

    @Test
    void approvalConditionsAreCopiedAndKeysMustBeUnique() {
        SeasonPlan plan = draft();
        plan.submit("planner", Instant.now());
        plan.verify("chief", Instant.now());
        var condition = new ApprovalCondition("weather", "Weather", "Calibration", ActorRole.TALENT_COORDINATOR);
        var conditions = new java.util.ArrayList<>(List.of(condition));
        assertThatThrownBy(() -> plan.approve("ok", List.of(condition, condition), "chief", Instant.now()))
                .isInstanceOf(DomainValidationException.class);
        assertThat(plan.status()).isEqualTo(SeasonPlanStatus.VERIFIED);
        plan.approve("ok", conditions, "chief", Instant.now());
        conditions.clear();
        assertThat(plan.approvalConditions()).containsExactly(condition);
        assertThatThrownBy(() -> plan.approvalConditions().clear()).isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void submittedPlanCannotBeApprovedWithoutVerification() {
        SeasonPlan plan = draft();
        plan.submit("planner-1", Instant.parse("2026-01-01T01:00:00Z"));
        assertThatThrownBy(() -> plan.approve("approved", "guardian-1", Instant.parse("2026-01-01T02:00:00Z")))
                .isInstanceOf(DomainValidationException.class);
        assertThat(plan.status()).isEqualTo(SeasonPlanStatus.SUBMITTED);
    }

    private SeasonPlan draft() {
        return SeasonPlan.createDraft(
                UUID.fromString("11111111-1111-1111-1111-111111111111"),
                "SPRING",
                List.of("north-grove"),
                List.of(new TimeWindow(LocalDateTime.parse("2026-03-01T09:00:00"), LocalDateTime.parse("2026-03-01T12:00:00"))),
                Map.of("bloom", "early"),
                Map.of("temperature", 17.5),
                List.of(new ChecklistItem("calendar-reviewed", false)),
                "planner-1",
                Instant.parse("2026-01-01T00:00:00Z")
        );
    }
}
