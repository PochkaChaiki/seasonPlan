package com.seasonplan.lifecycle.service;

import com.seasonplan.identity.api.ActorRole;
import com.seasonplan.shared.domain.DomainValidationException;
import com.seasonplan.season.domain.SeasonPlanStatus;
import org.junit.jupiter.api.Test;
import java.util.Map;
import static org.assertj.core.api.Assertions.*;

class PollenFundingRevisionTest {
    @Test void oldConfirmationCannotApproveEditedMaterialAndNewConfirmationRestoresEligibility() {
        var f = SeasonPlanTestFixture.create();
        var plans = f.seasonPlanService();
        var pollen = f.pollenService();
        var plan = plans.createDraft(SeasonPlanTestFixture.validPlanDraft("planner"), ActorRole.FAIRY_PLANNER, "planner");
        plans.submitForApproval(plan.id(), ActorRole.FAIRY_PLANNER, "planner");
        var old = pollen.requestReservation(plan.id(), 100, ActorRole.POLLEN_GUARDIAN, "guardian");
        pollen.confirmReservation(old.id(), 100, "ok", ActorRole.POLLEN_GUARDIAN, "guardian");
        plans.verify(plan.id(), ActorRole.CHIEF_GUARDIAN, "chief");
        plans.returnForRevision(plan.id(), "change climate", ActorRole.CHIEF_GUARDIAN, "chief");
        var edited = SeasonPlanTestFixture.validPlanDraft("planner");
        edited.setTargetClimateMetrics(Map.of("temperature", 19.0, "humidity", 60.0, "pollen_density", 1.0));
        plans.updateDraft(plan.id(), edited, ActorRole.FAIRY_PLANNER, "planner");
        plans.submitForApproval(plan.id(), ActorRole.FAIRY_PLANNER, "planner");
        plans.verify(plan.id(), ActorRole.CHIEF_GUARDIAN, "chief");
        assertThatThrownBy(() -> plans.approve(plan.id(), "old funds", ActorRole.CHIEF_GUARDIAN, "chief"))
                .isInstanceOf(DomainValidationException.class);
        assertThat(plans.get(plan.id()).status()).isEqualTo(SeasonPlanStatus.VERIFIED);
        assertThat(plans.calculateChecklist(plan.id())).containsKey("pollen-confirmed");
        var current = pollen.requestReservation(plan.id(), 100, ActorRole.POLLEN_GUARDIAN, "guardian");
        pollen.confirmReservation(current.id(), 100, "current", ActorRole.POLLEN_GUARDIAN, "guardian");
        assertThat(plans.calculateChecklist(plan.id())).doesNotContainKey("pollen-confirmed");
        assertThat(plans.approve(plan.id(), "current funds", ActorRole.CHIEF_GUARDIAN, "chief").status()).isEqualTo(SeasonPlanStatus.APPROVED);
    }
}
