package com.seasonplan.lifecycle.service;

import com.seasonplan.season.api.SeasonDraft;
import com.seasonplan.work.api.TaskDraft;
import com.seasonplan.identity.api.ActorRole;
import com.seasonplan.shared.domain.DomainValidationException;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.assertj.core.api.Assertions.*;

class CommandFacadeIsolationTest {
    @Test void seasonInputAndCommandResultAreDetachedAndInvalidValuesReachDomainValidation() {
        var f = SeasonPlanTestFixture.create(); var draft = SeasonPlanTestFixture.validPlanDraft("planner");
        var territories = new ArrayList<>(draft.targetTerritories()); var targets = new HashMap<>(draft.targetClimateMetrics());
        var windows = new ArrayList<>(draft.timeWindows()); var parameters = new HashMap<>(draft.naturalPhenomenonParameters());
        var input = new SeasonDraft(draft.seasonType(), territories, windows, parameters, targets);
        territories.clear(); targets.clear(); windows.clear(); parameters.clear();
        var result = f.seasonPlanning().createDraft(input, ActorRole.FAIRY_PLANNER, "planner");
        f.seasonPlanService().completeItem(result.id(), "calendar-reviewed", ActorRole.TALENT_COORDINATOR, "coordinator");
        assertThat(result.readinessChecklist()).anyMatch(item -> item.key().equals("calendar-reviewed") && !item.complete());
        assertThat(result.targetClimateMetrics()).isNotEmpty(); assertThat(input.timeWindows()).isNotEmpty(); assertThat(input.naturalPhenomenonParameters()).isNotEmpty();
        assertThatThrownBy(() -> input.timeWindows().clear()).isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> input.naturalPhenomenonParameters().clear()).isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> input.targetClimateMetrics().clear()).isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> input.targetTerritories().clear()).isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> result.targetTerritories().clear()).isInstanceOf(UnsupportedOperationException.class);
        var invalid = new HashMap<>(draft.targetClimateMetrics()); invalid.put("temperature", null);
        var invalidInput = new SeasonDraft(draft.seasonType(), draft.targetTerritories(), draft.timeWindows(), draft.naturalPhenomenonParameters(), invalid);
        assertThatThrownBy(() -> f.seasonPlanning().createDraft(invalidInput, ActorRole.FAIRY_PLANNER, "planner"))
                .isInstanceOf(DomainValidationException.class);
    }
    @Test void workCommandReturnsSnapshotWithoutRereadingMutableOwnerState() {
        var f = SeasonPlanTestFixture.create();
        var plan = f.seasonPlanService().createDraft(SeasonPlanTestFixture.validPlanDraft("planner"), ActorRole.FAIRY_PLANNER, "planner");
        f.seasonPlanService().submitForApproval(plan.id(), ActorRole.FAIRY_PLANNER, "planner");
        var reservation = f.pollenOperations().requestReservation(plan.id(), 100, ActorRole.POLLEN_GUARDIAN, "guardian");
        f.pollenOperations().confirmReservation(reservation.id(), 100, "ok", ActorRole.POLLEN_GUARDIAN, "guardian");
        f.seasonPlanService().verify(plan.id(), ActorRole.CHIEF_GUARDIAN, "chief");
        assertThatThrownBy(() -> f.seasonPlanning().approve(plan.id(), "ok", Arrays.asList((com.seasonplan.season.domain.ApprovalCondition) null), ActorRole.CHIEF_GUARDIAN, "chief"))
                .isInstanceOf(DomainValidationException.class);
        f.seasonPlanService().approve(plan.id(), "ok", ActorRole.CHIEF_GUARDIAN, "chief");
        var draft = SeasonPlanTestFixture.validTaskDraft();
        var result = f.workPreparation().createTask(plan.id(), new TaskDraft(draft.title(), draft.description(), draft.requiredTalent(), draft.timeWindow(), draft.status(), "WEATHER"), ActorRole.TALENT_COORDINATOR, "coordinator");
        f.magicTaskService().updateTask(result.id(), new com.seasonplan.work.domain.MagicTask("changed", draft.description(), draft.requiredTalent(), draft.timeWindow(), draft.status(), "WEATHER"), ActorRole.TALENT_COORDINATOR, "coordinator");
        assertThat(result.title()).isEqualTo(draft.title());
        assertThat(f.workPreparation().get(result.id()).title()).isEqualTo("changed");
    }
    @Test void pollenCommandResultCannotTrackLaterProviderDecisionMutation() {
        var f = SeasonPlanTestFixture.create();
        var plan = f.seasonPlanService().createDraft(SeasonPlanTestFixture.validPlanDraft("planner"), ActorRole.FAIRY_PLANNER, "planner");
        var pending = f.pollenOperations().requestReservation(plan.id(), 100, ActorRole.POLLEN_GUARDIAN, "guardian");
        var confirmed = f.pollenOperations().confirmReservation(pending.id(), 100, "ok", ActorRole.POLLEN_GUARDIAN, "guardian");
        assertThat(pending.status()).isEqualTo(com.seasonplan.pollen.domain.PollenReservationStatus.PENDING);
        assertThat(pending.confirmedAmount()).isNull();
        assertThat(confirmed.status()).isEqualTo(com.seasonplan.pollen.domain.PollenReservationStatus.CONFIRMED);
        assertThat(confirmed.confirmedBy()).isEqualTo("guardian");
        assertThat(confirmed.getClass().isRecord()).isTrue();
    }
    @Test void facadesMapReturnedCommandObjectsWithoutPostCommitQueries() {
        var seasonOwner = org.mockito.Mockito.mock(com.seasonplan.season.application.SeasonPlanService.class);
        var seasonQueries = org.mockito.Mockito.mock(com.seasonplan.season.api.SeasonQueries.class);
        var material = SeasonPlanTestFixture.validPlanDraft("planner");
        var raw = com.seasonplan.season.domain.SeasonPlan.createDraft(UUID.randomUUID(), material.seasonType(), material.targetTerritories(), material.timeWindows(), material.naturalPhenomenonParameters(), material.targetClimateMetrics(), List.of(), "planner", java.time.Instant.EPOCH);
        org.mockito.Mockito.when(seasonOwner.createDraft(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any())).thenReturn(raw);
        var input = new SeasonDraft(raw.seasonType(), raw.targetTerritories(), raw.timeWindows(), raw.naturalPhenomenonParameters(), raw.targetClimateMetrics());
        var result = new com.seasonplan.season.application.SeasonPlanningFacade(seasonOwner, seasonQueries).createDraft(input, ActorRole.FAIRY_PLANNER, "planner");
        assertThat(result.seasonType()).isEqualTo(raw.seasonType());
        org.mockito.Mockito.verifyNoInteractions(seasonQueries);
        var workOwner = org.mockito.Mockito.mock(com.seasonplan.work.application.MagicTaskService.class);
        var workQueries = org.mockito.Mockito.mock(com.seasonplan.work.api.WorkQueries.class);
        var task = SeasonPlanTestFixture.validTaskDraft();
        org.mockito.Mockito.when(workOwner.createTask(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any())).thenReturn(task);
        var taskInput = new TaskDraft(task.title(), task.description(), task.requiredTalent(), task.timeWindow(), task.status(), task.category());
        var taskResult = new com.seasonplan.work.application.WorkPreparationFacade(workOwner, workQueries).createTask(UUID.randomUUID(), taskInput, ActorRole.TALENT_COORDINATOR, "coordinator");
        assertThat(taskResult.title()).isEqualTo(task.title());
        org.mockito.Mockito.verifyNoInteractions(workQueries);
    }
}
