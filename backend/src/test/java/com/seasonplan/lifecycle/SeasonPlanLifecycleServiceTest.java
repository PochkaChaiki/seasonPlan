package com.seasonplan.lifecycle;

import com.seasonplan.lifecycle.domain.ActorRole;
import com.seasonplan.lifecycle.domain.AssignmentStatus;
import com.seasonplan.lifecycle.domain.AuditAction;
import com.seasonplan.lifecycle.domain.DomainValidationException;
import com.seasonplan.lifecycle.domain.MagicTask;
import com.seasonplan.lifecycle.domain.PollenReservation;
import com.seasonplan.lifecycle.domain.PollenReservationStatus;
import com.seasonplan.lifecycle.domain.SeasonPlan;
import com.seasonplan.lifecycle.domain.SeasonPlanStatus;
import com.seasonplan.lifecycle.domain.TimeWindow;
import com.seasonplan.lifecycle.application.shared.AccessDeniedForRoleException;
import com.seasonplan.lifecycle.application.preparation.FairyAssignmentService;
import com.seasonplan.lifecycle.application.preparation.MagicTaskService;
import com.seasonplan.lifecycle.application.pollen.PollenService;
import com.seasonplan.lifecycle.application.preparation.ReadinessChecklistService;
import com.seasonplan.lifecycle.service.SeasonPlanService;
import com.seasonplan.lifecycle.service.SeasonPlanTestFixture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SeasonPlanLifecycleServiceTest {

    private SeasonPlanTestFixture fixture;
    private SeasonPlanService seasonPlans;
    private PollenService pollenReservations;
    private MagicTaskService magicTasks;
    private FairyAssignmentService fairyAssignments;
    private ReadinessChecklistService readinessChecklist;

    @BeforeEach
    void setUp() {
        fixture = SeasonPlanTestFixture.create();
        seasonPlans = fixture.seasonPlanService();
        pollenReservations = fixture.pollenService();
        magicTasks = fixture.magicTaskService();
        fairyAssignments = fixture.fairyAssignmentService();
        readinessChecklist = fixture.readinessChecklistService();
    }

    @Test
    void plannerCanCreateValidDraftAndInvalidDraftReturnsFieldErrors() {
        SeasonPlan created = seasonPlans.createDraft(SeasonPlanTestFixture.validPlanDraft("planner-1"), ActorRole.FAIRY_PLANNER, "planner-1");

        assertThat(created.status()).isEqualTo(SeasonPlanStatus.DRAFT);
        assertThat(fixture.auditLogService().entriesFor(created.id()))
                .extracting("action")
                .containsExactly(AuditAction.SEASON_PLAN_CREATED);

        assertThatThrownBy(() -> seasonPlans.createDraft(SeasonPlanTestFixture.invalidPlanDraft(), ActorRole.FAIRY_PLANNER, "planner-1"))
                .isInstanceOf(DomainValidationException.class)
                .satisfies(error -> assertThat(((DomainValidationException) error).fieldErrors())
                        .containsKeys("seasonType", "timeWindows", "targetTerritories", "naturalPhenomenonParameters", "targetClimateMetrics"));
    }

    @Test
    void overlappingTimeWindowsBlockSave() {
        var draft = SeasonPlanTestFixture.validPlanDraft("planner-1");
        draft.setTimeWindows(List.of(
                new TimeWindow(LocalDateTime.parse("2026-03-01T09:00:00"), LocalDateTime.parse("2026-03-01T12:00:00")),
                new TimeWindow(LocalDateTime.parse("2026-03-01T11:00:00"), LocalDateTime.parse("2026-03-01T15:00:00"))
        ));

        assertThatThrownBy(() -> seasonPlans.createDraft(draft, ActorRole.FAIRY_PLANNER, "planner-1"))
                .isInstanceOf(DomainValidationException.class)
                .satisfies(error -> assertThat(((DomainValidationException) error).fieldErrors())
                        .containsEntry("timeWindows", List.of("time windows must not overlap")));
    }

    @Test
    void plannerCanSubmitValidDraft() {
        SeasonPlan draft = seasonPlans.createDraft(SeasonPlanTestFixture.validPlanDraft("planner-1"), ActorRole.FAIRY_PLANNER, "planner-1");

        SeasonPlan submitted = seasonPlans.submitForApproval(draft.id(), ActorRole.FAIRY_PLANNER, "planner-1");

        assertThat(submitted.status()).isEqualTo(SeasonPlanStatus.SUBMITTED);
        assertThat(fixture.auditLogService().entriesFor(draft.id()))
                .extracting("action")
                .contains(AuditAction.SEASON_PLAN_SUBMITTED);
    }

    @Test
    void reviewRejectsOverlappingTerritoryInAnotherApplicablePlan() {
        SeasonPlan first = submittedPlan();
        SeasonPlan secondDraft = SeasonPlanTestFixture.validPlanDraft("planner-2");
        secondDraft.setTargetTerritories(List.of(" NORTH-GROVE "));
        SeasonPlan second = seasonPlans.createDraft(secondDraft, ActorRole.FAIRY_PLANNER, "planner-2");
        seasonPlans.submitForApproval(second.id(), ActorRole.FAIRY_PLANNER, "planner-2");
        assertThatThrownBy(() -> seasonPlans.verify(first.id(), ActorRole.CHIEF_GUARDIAN, "guardian-1"))
                .isInstanceOf(DomainValidationException.class)
                .satisfies(error -> assertThat(((DomainValidationException) error).fieldErrors()).containsKey("targetTerritories"));
        assertThat(first.status()).isEqualTo(SeasonPlanStatus.SUBMITTED);
    }

    @Test
    void approvalRechecksTerritoryConflictsAfterVerification() {
        SeasonPlan first = submittedPlan();
        confirmPollen(first);
        seasonPlans.verify(first.id(), ActorRole.CHIEF_GUARDIAN, "guardian-1");
        submittedPlan();
        assertThatThrownBy(() -> seasonPlans.approve(first.id(), "ok", ActorRole.CHIEF_GUARDIAN, "guardian-1"))
                .isInstanceOf(DomainValidationException.class)
                .satisfies(error -> assertThat(((DomainValidationException) error).fieldErrors()).containsKey("targetTerritories"));
        assertThat(first.status()).isEqualTo(SeasonPlanStatus.VERIFIED);
    }

    @Test
    void verifyRequiresGuardianAndEnqueuesAuditAndNotification() {
        SeasonPlan submitted = submittedPlan();
        assertThatThrownBy(() -> seasonPlans.verify(submitted.id(), ActorRole.FAIRY_PLANNER, "planner-1"))
                .isInstanceOf(AccessDeniedForRoleException.class);
        SeasonPlan verified = seasonPlans.verify(submitted.id(), ActorRole.CHIEF_GUARDIAN, "guardian-1");
        assertThat(verified.status()).isEqualTo(SeasonPlanStatus.VERIFIED);
        assertThat(fixture.auditLogService().entriesFor(submitted.id())).extracting("action")
                .contains(AuditAction.SEASON_PLAN_VERIFIED);
        assertThat(fixture.reviewEvents()).anySatisfy(event -> {
            assertThat(event.type()).isEqualTo("SEASON_PLAN_VERIFIED");
            assertThat(event.recipients()).containsExactly("planner-1");
            assertThat(event.channels()).containsExactlyInAnyOrder(
                    com.seasonplan.lifecycle.application.port.NotificationOutbox.Channel.IN_APP,
                    com.seasonplan.lifecycle.application.port.NotificationOutbox.Channel.EMAIL);
        });
    }

    @Test
    void verifiedPlanCanBeReturnedOnlyWithReason() {
        SeasonPlan submitted = submittedPlan();
        seasonPlans.verify(submitted.id(), ActorRole.CHIEF_GUARDIAN, "guardian-1");
        assertThatThrownBy(() -> seasonPlans.returnForRevision(submitted.id(), " ", ActorRole.CHIEF_GUARDIAN, "guardian-1"))
                .isInstanceOf(DomainValidationException.class);
        assertThat(seasonPlans.returnForRevision(submitted.id(), "revise", ActorRole.CHIEF_GUARDIAN, "guardian-1").status())
                .isEqualTo(SeasonPlanStatus.DRAFT);
    }

    @Test
    void confirmedPollenDoesNotPermitDirectApprovalFromSubmitted() {
        SeasonPlan submitted = submittedPlan();
        confirmPollen(submitted);
        assertThatThrownBy(() -> seasonPlans.approve(submitted.id(), "approved", ActorRole.CHIEF_GUARDIAN, "guardian-1"))
                .isInstanceOf(DomainValidationException.class);
        assertThat(submitted.status()).isEqualTo(SeasonPlanStatus.SUBMITTED);
        assertThat(fixture.reviewEvents()).isEmpty();
        assertThat(fixture.auditLogService().entriesFor(submitted.id())).extracting("action")
                .doesNotContain(AuditAction.SEASON_PLAN_APPROVED);
    }

    @Test
    void chiefGuardianCanApproveVerifiedPlan() {
        SeasonPlan submitted = submittedPlan();
        confirmPollen(submitted);
        seasonPlans.verify(submitted.id(), ActorRole.CHIEF_GUARDIAN, "guardian-1");

        SeasonPlan approved = seasonPlans.approve(submitted.id(), "approved for spring", ActorRole.CHIEF_GUARDIAN, "guardian-1");

        assertThat(approved.status()).isEqualTo(SeasonPlanStatus.APPROVED);
        assertThat(approved.approvalComment()).isEqualTo("approved for spring");
        assertThat(fixture.reviewEvents()).anyMatch(event -> event.type().equals("SEASON_PLAN_APPROVED"));
        assertThat(fixture.auditLogService().entriesFor(submitted.id()))
                .extracting("action")
                .contains(AuditAction.SEASON_PLAN_APPROVED);
    }

    @Test
    void approvalRequiresAnExistingConfirmedReservation() {
        SeasonPlan submitted = submittedPlan();
        seasonPlans.verify(submitted.id(), ActorRole.CHIEF_GUARDIAN, "guardian-1");

        assertThatThrownBy(() -> seasonPlans.approve(submitted.id(), "approved", ActorRole.CHIEF_GUARDIAN, "guardian-1"))
                .isInstanceOf(DomainValidationException.class);
        assertThat(submitted.status()).isEqualTo(SeasonPlanStatus.VERIFIED);
        assertThat(fixture.auditLogService().entriesFor(submitted.id()))
                .extracting("action").doesNotContain(AuditAction.SEASON_PLAN_APPROVED);
    }

    @Test
    void pendingReservationBlocksApproval() {
        SeasonPlan submitted = submittedPlan();
        pollenReservations.requestReservation(submitted.id(), 100, ActorRole.POLLEN_GUARDIAN, "pollen-1");
        seasonPlans.verify(submitted.id(), ActorRole.CHIEF_GUARDIAN, "guardian-1");

        assertThatThrownBy(() -> seasonPlans.approve(submitted.id(), "approved", ActorRole.CHIEF_GUARDIAN, "guardian-1"))
                .isInstanceOf(DomainValidationException.class);
        assertThat(submitted.status()).isEqualTo(SeasonPlanStatus.VERIFIED);
    }

    @Test
    void chiefGuardianCanReturnSubmittedPlanOnlyWithComment() {
        SeasonPlan submitted = submittedPlan();

        assertThatThrownBy(() -> seasonPlans.returnForRevision(submitted.id(), " ", ActorRole.CHIEF_GUARDIAN, "guardian-1"))
                .isInstanceOf(DomainValidationException.class)
                .satisfies(error -> assertThat(((DomainValidationException) error).fieldErrors())
                        .containsKey("comment"));

        SeasonPlan returned = seasonPlans.returnForRevision(submitted.id(), "revise target climate", ActorRole.CHIEF_GUARDIAN, "guardian-1");

        assertThat(returned.status()).isEqualTo(SeasonPlanStatus.DRAFT);
        assertThat(returned.approvalComment()).isEqualTo("revise target climate");
        assertThat(fixture.reviewEvents()).anyMatch(event -> event.type().equals("SEASON_PLAN_RETURNED"));
        assertThat(fixture.auditLogService().entriesFor(submitted.id()))
                .extracting("action")
                .contains(AuditAction.SEASON_PLAN_RETURNED);
    }

    @Test
    void rejectedReservationWithoutAuthorityReturnsPlanWithProviderReason() {
        SeasonPlan submitted = submittedPlan();
        PollenReservation reservation = pollenReservations.requestReservation(submitted.id(), 100, ActorRole.POLLEN_GUARDIAN, "pollen-1");
        pollenReservations.rejectReservation(reservation.id(), "not enough stock", ActorRole.POLLEN_GUARDIAN, "pollen-1");
        seasonPlans.verify(submitted.id(), ActorRole.CHIEF_GUARDIAN, "guardian-1");
        SeasonPlan result = seasonPlans.approve(submitted.id(), "approve", ActorRole.CHIEF_GUARDIAN, "guardian-1");
        assertThat(result.status()).isEqualTo(SeasonPlanStatus.DRAFT);
        assertThat(result.approvalComment()).contains("not enough stock");
        assertThat(fixture.auditLogService().entriesFor(submitted.id())).extracting("action").contains(AuditAction.SEASON_PLAN_RETURNED);
        assertThat(fixture.reviewEvents()).anyMatch(event -> event.type().equals("SEASON_PLAN_RETURNED"));
    }

    @Test
    void draftReplacementDoesNotSupersedeConfirmedAuthorityForApproval() {
        SeasonPlan plan = submittedPlan();
        confirmPollen(plan);
        pollenReservations.requestReservation(plan.id(), 120, ActorRole.POLLEN_GUARDIAN, "pollen-1");
        seasonPlans.verify(plan.id(), ActorRole.CHIEF_GUARDIAN, "guardian-1");
        assertThat(seasonPlans.approve(plan.id(), "approved", ActorRole.CHIEF_GUARDIAN, "guardian-1").status())
                .isEqualTo(SeasonPlanStatus.APPROVED);
    }

    @Test
    void rejectedReplacementRetainsAuthorityForReadinessButPendingReplacementBlocks() {
        SeasonPlan plan = submittedPlan();
        confirmPollen(plan);
        PollenReservation replacement = pollenReservations.requestReservation(plan.id(), 120, ActorRole.POLLEN_GUARDIAN, "pollen-1");
        assertThat(readinessChecklist.calculateChecklist(plan.id())).containsKey("pollenReservation");
        pollenReservations.rejectReservation(replacement.id(), "capacity", ActorRole.POLLEN_GUARDIAN, "pollen-1");
        assertThat(readinessChecklist.calculateChecklist(plan.id())).doesNotContainKey("pollenReservation");
    }

    @Test
    void rejectedReplacementRetainsPriorConfirmedAuthorityForApproval() {
        SeasonPlan plan = submittedPlan();
        confirmPollen(plan);
        PollenReservation replacement = pollenReservations.requestReservation(plan.id(), 120, ActorRole.POLLEN_GUARDIAN, "pollen-1");
        pollenReservations.rejectReservation(replacement.id(), "capacity", ActorRole.POLLEN_GUARDIAN, "pollen-1");
        seasonPlans.verify(plan.id(), ActorRole.CHIEF_GUARDIAN, "guardian-1");
        assertThat(seasonPlans.approve(plan.id(), "approved", ActorRole.CHIEF_GUARDIAN, "guardian-1").status())
                .isEqualTo(SeasonPlanStatus.APPROVED);
    }

    @Test
    void unevidencedApprovalConditionBlocksReadyEvenWhenAllStandardChecksAndManualFlagPass() {
        SeasonPlan plan = submittedPlan();
        confirmPollen(plan);
        seasonPlans.verify(plan.id(), ActorRole.CHIEF_GUARDIAN, "guardian-1");
        var condition = new com.seasonplan.lifecycle.domain.ApprovalCondition("weather-evidence", "Weather", "Calibration", ActorRole.TALENT_COORDINATOR);
        seasonPlans.approve(plan.id(), "conditional", List.of(condition), ActorRole.CHIEF_GUARDIAN, "guardian-1");
        MagicTask task = magicTasks.createTask(plan.id(), SeasonPlanTestFixture.validTaskDraft(), ActorRole.TALENT_COORDINATOR, "coordinator-1");
        fairyAssignments.assignFairy(task.id(), "fairy-1", "LEAD", "SPRING_WEATHER", AssignmentStatus.CONFIRMED, ActorRole.TALENT_COORDINATOR, "coordinator-1");
        readinessChecklist.completeItem(plan.id(), "calendar-reviewed", ActorRole.TALENT_COORDINATOR, "coordinator-1");
        readinessChecklist.completeItem(plan.id(), "roles-reviewed", ActorRole.TALENT_COORDINATOR, "coordinator-1");
        readinessChecklist.completeItem(plan.id(), "weather-evidence", ActorRole.TALENT_COORDINATOR, "coordinator-1");
        assertThat(readinessChecklist.calculateChecklist(plan.id())).containsOnlyKeys("weather-evidence");
        assertThatThrownBy(() -> seasonPlans.markRitualReady(plan.id(), ActorRole.TALENT_COORDINATOR, "coordinator-1"))
                .isInstanceOf(DomainValidationException.class);
        assertThat(plan.status()).isEqualTo(SeasonPlanStatus.APPROVED);
    }

    @Test
    void ritualReadyIsBlockedUntilChecklistReservationTasksAndAssignmentsAreComplete() {
        SeasonPlan approved = approvedPlan();
        pollenReservations.requestReservation(approved.id(), 100, ActorRole.POLLEN_GUARDIAN, "pollen-1");

        assertThatThrownBy(() -> seasonPlans.markRitualReady(approved.id(), ActorRole.TALENT_COORDINATOR, "coordinator-1"))
                .isInstanceOf(DomainValidationException.class)
                .satisfies(error -> assertThat(((DomainValidationException) error).fieldErrors())
                        .containsKeys("pollenReservation", "magicTasks", "readinessChecklist"));
    }

    @Test
    void ritualReadyIsAllowedWhenReadinessChecksPass() {
        SeasonPlan approved = approvedPlan();
        PollenReservation reservation = pollenReservations.requestReservation(approved.id(), 100, ActorRole.POLLEN_GUARDIAN, "pollen-1");
        pollenReservations.confirmReservation(reservation.id(), 100, "within limit", ActorRole.POLLEN_GUARDIAN, "pollen-1");
        MagicTask task = magicTasks.createTask(approved.id(), SeasonPlanTestFixture.validTaskDraft(), ActorRole.TALENT_COORDINATOR, "coordinator-1");
        fairyAssignments.assignFairy(task.id(), "fairy-1", "LEAD", "SPRING_WEATHER", AssignmentStatus.CONFIRMED, ActorRole.TALENT_COORDINATOR, "coordinator-1");
        readinessChecklist.completeItem(approved.id(), "calendar-reviewed", ActorRole.TALENT_COORDINATOR, "coordinator-1");
        readinessChecklist.completeItem(approved.id(), "roles-reviewed", ActorRole.TALENT_COORDINATOR, "coordinator-1");

        SeasonPlan ready = seasonPlans.markRitualReady(approved.id(), ActorRole.TALENT_COORDINATOR, "coordinator-1");

        assertThat(ready.status()).isEqualTo(SeasonPlanStatus.RITUAL_READY);
        assertThat(fixture.auditLogService().entriesFor(approved.id()))
                .extracting("action")
                .contains(AuditAction.POLLEN_RESERVATION_CONFIRMED, AuditAction.MAGIC_TASK_CREATED,
                        AuditAction.FAIRY_ASSIGNED, AuditAction.SEASON_MARKED_RITUAL_READY);
    }

    @Test
    void rolesAreEnforcedForCommands() {
        assertThatThrownBy(() -> seasonPlans.createDraft(SeasonPlanTestFixture.validPlanDraft("guardian-1"), ActorRole.CHIEF_GUARDIAN, "guardian-1"))
                .isInstanceOf(AccessDeniedForRoleException.class);

        SeasonPlan submitted = submittedPlan();

        assertThatThrownBy(() -> seasonPlans.approve(submitted.id(), "approved", ActorRole.FAIRY_PLANNER, "planner-1"))
                .isInstanceOf(AccessDeniedForRoleException.class);
    }

    private SeasonPlan submittedPlan() {
        SeasonPlan draft = seasonPlans.createDraft(SeasonPlanTestFixture.validPlanDraft("planner-1"), ActorRole.FAIRY_PLANNER, "planner-1");
        return seasonPlans.submitForApproval(draft.id(), ActorRole.FAIRY_PLANNER, "planner-1");
    }

    private void confirmPollen(SeasonPlan plan) {
        PollenReservation reservation = pollenReservations.requestReservation(plan.id(), 100, ActorRole.POLLEN_GUARDIAN, "pollen-1");
        pollenReservations.confirmReservation(reservation.id(), 100, "within limit", ActorRole.POLLEN_GUARDIAN, "pollen-1");
    }

    private SeasonPlan approvedPlan() {
        SeasonPlan submitted = submittedPlan();
        confirmPollen(submitted);
        seasonPlans.verify(submitted.id(), ActorRole.CHIEF_GUARDIAN, "guardian-1");
        return seasonPlans.approve(submitted.id(), "approved", ActorRole.CHIEF_GUARDIAN, "guardian-1");
    }
}
