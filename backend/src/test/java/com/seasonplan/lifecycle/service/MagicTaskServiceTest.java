package com.seasonplan.lifecycle.service;

import com.seasonplan.season.application.SeasonPlanService;
import com.seasonplan.season.domain.SeasonPlan;
import com.seasonplan.shared.domain.DomainValidationException;
import com.seasonplan.shared.domain.TimeWindow;
import com.seasonplan.work.application.MagicTaskService;
import com.seasonplan.work.domain.AssignmentStatus;
import com.seasonplan.work.domain.FairyAssignment;
import com.seasonplan.work.domain.MagicTask;
import com.seasonplan.work.domain.MagicTaskStatus;

import com.seasonplan.audit.api.AuditLogEntry;
import com.seasonplan.audit.api.AuditAction;
import com.seasonplan.identity.api.AccessDeniedForRoleException;
import com.seasonplan.identity.api.ActorRole;
import com.seasonplan.shared.application.NotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;

/** Regression coverage for the combined task/assignment boundary. */
class MagicTaskServiceTest {
    private SeasonPlanTestFixture fixture;
    private SeasonPlanService plans;
    private MagicTaskService tasks;
    private SeasonPlan plan;

    @BeforeEach
    void setUp() {
        fixture = SeasonPlanTestFixture.create();
        plans = fixture.seasonPlanService();
        tasks = fixture.magicTaskService();
        plan = plans.createDraft(SeasonPlanTestFixture.validPlanDraft("planner"), ActorRole.FAIRY_PLANNER, "planner");
        plans.submitForApproval(plan.id(), ActorRole.FAIRY_PLANNER, "planner");
        var reservation = fixture.pollenService().requestReservation(plan.id(), 100, ActorRole.POLLEN_GUARDIAN, "pollen");
        fixture.pollenService().confirmReservation(reservation.id(), 100, "ok", ActorRole.POLLEN_GUARDIAN, "pollen");
        plans.verify(plan.id(), ActorRole.CHIEF_GUARDIAN, "chief");
        plans.approve(plan.id(), "ok", ActorRole.CHIEF_GUARDIAN, "chief");
    }

    @Test
    void taskAndAssignmentCommandsShareReadsAndKeepDistinctAuditEvents() {
        MagicTask task = createTask();
        var assignment = assign(task, "fairy", AssignmentStatus.CONFIRMED);

        assertThat(tasks.get(task.id())).isSameAs(task);
        assertThat(tasks.listTasksBySeasonPlan(plan.id())).containsExactly(task);
        assertThat(tasks.assignmentsForTask(task.id())).containsExactly(assignment);
        assertThat(tasks.validateRequiredRoles(task)).isTrue();
        assertThat(tasks.validateTalentCompatibility(task, "SPRING_WEATHER")).isTrue();
        assertThat(fixture.auditLogService().entriesFor(plan.id())).extracting(AuditLogEntry::action)
                .endsWith(AuditAction.MAGIC_TASK_CREATED, AuditAction.FAIRY_ASSIGNED);
    }

    @Test
    void onlyApprovedPlansAcceptTasksAndOnlyCoordinatorsCanPrepareThem() {
        var draft = plans.createDraft(SeasonPlanTestFixture.validPlanDraft("planner"), ActorRole.FAIRY_PLANNER, "planner");
        assertThatThrownBy(() -> tasks.createTask(draft.id(), SeasonPlanTestFixture.validTaskDraft(),
                ActorRole.TALENT_COORDINATOR, "coordinator")).isInstanceOf(DomainValidationException.class);
        assertThatThrownBy(() -> tasks.createTask(plan.id(), SeasonPlanTestFixture.validTaskDraft(),
                ActorRole.FAIRY_PLANNER, "planner")).isInstanceOf(AccessDeniedForRoleException.class);
        MagicTask task = createTask();
        assertThatThrownBy(() -> tasks.assignFairy(task.id(), "fairy", "LEAD", "SPRING_WEATHER",
                AssignmentStatus.CONFIRMED, ActorRole.FAIRY_PLANNER, "planner"))
                .isInstanceOf(AccessDeniedForRoleException.class);
        assertThat(tasks.assignmentsForTask(task.id())).isEmpty();
    }

    @Test
    void invalidAssignmentReturnsFieldErrorsWithoutSavingOrAuditing() {
        MagicTask task = createTask();
        int auditCount = fixture.auditLogService().entriesFor(plan.id()).size();
        assertThatThrownBy(() -> tasks.assignFairy(task.id(), " ", " ", "WRONG_TALENT",
                AssignmentStatus.CONFIRMED, ActorRole.TALENT_COORDINATOR, "coordinator"))
                .isInstanceOfSatisfying(DomainValidationException.class,
                        error -> assertThat(error.fieldErrors()).containsKeys("fairyId", "role", "talent"));
        assertThat(tasks.assignmentsForTask(task.id())).isEmpty();
        assertThat(fixture.auditLogService().entriesFor(plan.id())).hasSize(auditCount);
    }

    @Test
    void overlappingConfirmedAssignmentsAreRejectedButProposalsDoNotSatisfyReadiness() {
        MagicTask first = createTask();
        MagicTask second = createTask();
        assign(first, "fairy", AssignmentStatus.CONFIRMED);
        int auditCount = fixture.auditLogService().entriesFor(plan.id()).size();
        assertThatThrownBy(() -> assign(second, "fairy", AssignmentStatus.CONFIRMED))
                .isInstanceOfSatisfying(DomainValidationException.class,
                        error -> assertThat(error.fieldErrors()).containsKey("fairyId"));
        assertThat(fixture.auditLogService().entriesFor(plan.id())).hasSize(auditCount);
        assign(second, "fairy", AssignmentStatus.PROPOSED);
        assertThat(tasks.validateRequiredRoles(second)).isFalse();
        assertThat(plans.calculateChecklist(plan.id())).containsKey("role:RITUAL_LEAD");
    }

    @Test
    void touchingTaskWindowsDoNotConflictAndPlanReadinessUsesTheMergedAssignments() {
        MagicTask first = createTask();
        MagicTask later = SeasonPlanTestFixture.validTaskDraft();
        later.updateFrom(new MagicTask(later.title(), later.description(), later.requiredTalent(),
                new TimeWindow(LocalDateTime.parse("2026-03-01T10:00:00"), LocalDateTime.parse("2026-03-01T11:00:00")),
                MagicTaskStatus.READY));
        later = tasks.createTask(plan.id(), later, ActorRole.TALENT_COORDINATOR, "coordinator");
        assign(first, "fairy", AssignmentStatus.CONFIRMED);
        assign(later, "fairy", AssignmentStatus.CONFIRMED);
        plans.completeItem(plan.id(), "calendar-reviewed", ActorRole.TALENT_COORDINATOR, "coordinator");
        plans.completeItem(plan.id(), "roles-reviewed", ActorRole.POLLEN_GUARDIAN, "pollen");
        assertThat(plans.calculateChecklist(plan.id())).containsKeys("task-category:" + first.id(), "task-category:" + later.id(), "category:WEATHER", "category:POLLEN");
        assertThatThrownBy(() -> plans.markRitualReady(plan.id(), ActorRole.TALENT_COORDINATOR, "coordinator"))
                .isInstanceOf(DomainValidationException.class);
    }

    @Test
    void cannotCompleteAnUncategorizedTaskOrCancelWithoutReason() {
        MagicTask task = createTask();
        var uncategorized = new MagicTask(task.title(), task.description(), task.requiredTalent(), task.timeWindow(), MagicTaskStatus.COMPLETED);
        assertThatThrownBy(() -> tasks.updateTask(task.id(), uncategorized, ActorRole.TALENT_COORDINATOR, "coordinator"))
                .isInstanceOfSatisfying(DomainValidationException.class, ex -> assertThat(ex.fieldErrors()).containsKey("category"));
        var cancelled = new MagicTask(task.title(), task.description(), task.requiredTalent(), task.timeWindow(), MagicTaskStatus.CANCELLED, "WEATHER");
        assertThatThrownBy(() -> tasks.updateTask(task.id(), cancelled, ActorRole.TALENT_COORDINATOR, "coordinator"))
                .isInstanceOfSatisfying(DomainValidationException.class, ex -> assertThat(ex.fieldErrors()).containsKey("status"));
        assertThat(tasks.get(task.id()).status()).isEqualTo(MagicTaskStatus.READY);
    }

    @Test
    void completionRequiresConfirmedCompatibleAssignmentAndPreservesStateOnFailure() {
        MagicTask task = createTask();
        var completed = new MagicTask(task.title(), task.description(), task.requiredTalent(), task.timeWindow(), MagicTaskStatus.COMPLETED, "WEATHER");
        int auditCount = fixture.auditLogService().entriesFor(plan.id()).size();
        assertThatThrownBy(() -> tasks.updateTask(task.id(), completed, ActorRole.TALENT_COORDINATOR, "coordinator"))
                .isInstanceOfSatisfying(DomainValidationException.class, ex -> assertThat(ex.fieldErrors()).containsKey("assignments"));
        assertThat(tasks.get(task.id()).status()).isEqualTo(MagicTaskStatus.READY);
        assertThat(fixture.auditLogService().entriesFor(plan.id())).hasSize(auditCount);
        assign(task, "proposal", AssignmentStatus.PROPOSED);
        assertThatThrownBy(() -> tasks.updateTask(task.id(), completed, ActorRole.TALENT_COORDINATOR, "coordinator"))
                .isInstanceOf(DomainValidationException.class);
        assign(task, "fairy", AssignmentStatus.CONFIRMED);
        var changedTalent = new MagicTask(task.title(), task.description(), "OTHER", task.timeWindow(), MagicTaskStatus.COMPLETED, "WEATHER");
        assertThatThrownBy(() -> tasks.updateTask(task.id(), changedTalent, ActorRole.TALENT_COORDINATOR, "coordinator"))
                .isInstanceOf(DomainValidationException.class);
        assertThat(tasks.get(task.id()).status()).isEqualTo(MagicTaskStatus.READY);
        assertThat(tasks.updateTask(task.id(), completed, ActorRole.TALENT_COORDINATOR, "coordinator").status()).isEqualTo(MagicTaskStatus.COMPLETED);
    }

    @Test
    void legacyConfirmedAssignmentWithoutRealTimestampCannotCompleteTask() {
        var repository = new com.seasonplan.work.adapter.out.fake.InMemoryMagicTaskRepository();
        var assignments = org.mockito.Mockito.mock(com.seasonplan.work.application.port.FairyAssignmentRepository.class);
        var planRepository = org.mockito.Mockito.mock(com.seasonplan.season.application.port.SeasonPlanRepository.class);
        org.mockito.Mockito.when(planRepository.findById(plan.id())).thenReturn(java.util.Optional.of(fixture.seasonPlanService().get(plan.id())));
        var service = new MagicTaskService(repository, assignments, new com.seasonplan.season.application.SeasonQueryService(planRepository), fixture.auditLogService(), new DirectTestTransaction(), java.time.Clock.systemUTC());
        MagicTask task = SeasonPlanTestFixture.validTaskDraft(); task.setId(UUID.randomUUID()); task.setSeasonPlanId(plan.id()); repository.save(task, "actor");
        var legacy = new FairyAssignment(UUID.randomUUID(), task.id(), "fairy", "LEAD", task.requiredTalent(), AssignmentStatus.CONFIRMED);
        org.mockito.Mockito.when(assignments.findByMagicTaskId(task.id())).thenReturn(java.util.List.of(legacy));
        int before = fixture.auditLogService().entriesFor(plan.id()).size();
        assertThatThrownBy(() -> service.updateTask(task.id(), new MagicTask(task.title(), task.description(), task.requiredTalent(), task.timeWindow(), MagicTaskStatus.COMPLETED, "WEATHER"), ActorRole.TALENT_COORDINATOR, "coordinator"))
                .isInstanceOfSatisfying(DomainValidationException.class, ex -> assertThat(ex.fieldErrors()).containsKey("assignments"));
        assertThat(repository.findById(task.id()).orElseThrow().status()).isEqualTo(MagicTaskStatus.READY);
        assertThat(fixture.auditLogService().entriesFor(plan.id())).hasSize(before);
    }

    @Test
    void directCompletedTaskCreationIsRejectedWithoutAudit() {
        MagicTask base = SeasonPlanTestFixture.validTaskDraft();
        var completed = new MagicTask(base.title(), base.description(), base.requiredTalent(), base.timeWindow(), MagicTaskStatus.COMPLETED, "WEATHER");
        int before = fixture.auditLogService().entriesFor(plan.id()).size();
        assertThatThrownBy(() -> tasks.createTask(plan.id(), completed, ActorRole.TALENT_COORDINATOR, "coordinator"))
                .isInstanceOfSatisfying(DomainValidationException.class, ex -> assertThat(ex.fieldErrors()).containsKey("status"));
        assertThat(tasks.listTasksBySeasonPlan(plan.id())).isEmpty();
        assertThat(fixture.auditLogService().entriesFor(plan.id())).hasSize(before);
    }

    @Test
    void taskCompletionIsAuditedAndTerminal() {
        MagicTask task = createTask();
        assign(task, "fairy", AssignmentStatus.CONFIRMED);
        tasks.updateTask(task.id(), new MagicTask(task.title(), task.description(), task.requiredTalent(), task.timeWindow(), MagicTaskStatus.COMPLETED, "WEATHER"), ActorRole.TALENT_COORDINATOR, "coordinator");
        int before = fixture.auditLogService().entriesFor(plan.id()).size();
        assertThat(fixture.auditLogService().entriesFor(plan.id())).extracting(AuditLogEntry::action).contains(AuditAction.MAGIC_TASK_UPDATED);
        assertThatThrownBy(() -> tasks.updateTask(task.id(), SeasonPlanTestFixture.validTaskDraft(), ActorRole.TALENT_COORDINATOR, "coordinator"))
                .isInstanceOfSatisfying(DomainValidationException.class, ex -> assertThat(ex.fieldErrors()).containsKey("task"));
        assertThat(tasks.get(task.id()).status()).isEqualTo(MagicTaskStatus.COMPLETED);
        assertThat(fixture.auditLogService().entriesFor(plan.id())).hasSize(before);
    }

    @Test
    void invalidTaskUpdatesAndMissingTaskAssignmentsLeaveExistingDataUntouched() {
        MagicTask task = createTask();
        var invalid = new MagicTask(" ", "", " ", null, MagicTaskStatus.BLOCKED);
        assertThatThrownBy(() -> tasks.updateTask(task.id(), invalid, ActorRole.TALENT_COORDINATOR, "coordinator"))
                .isInstanceOfSatisfying(DomainValidationException.class,
                        error -> assertThat(error.fieldErrors()).containsKeys("title", "requiredTalent", "timeWindow"));
        assertThat(tasks.get(task.id()).status()).isEqualTo(MagicTaskStatus.READY);
        assertThatThrownBy(() -> tasks.assignFairy(UUID.randomUUID(), "fairy", "LEAD", "SPRING_WEATHER",
                AssignmentStatus.CONFIRMED, ActorRole.TALENT_COORDINATOR, "coordinator"))
                .isInstanceOf(NotFoundException.class);
        assertThat(tasks.assignmentsForTask(task.id())).isEmpty();
    }

    @Test
    void aParticipantCannotBeConfirmedInOverlappingTasksAcrossSeasons() {
        MagicTask first = createTask();
        assign(first, "shared-fairy", AssignmentStatus.CONFIRMED);
        SeasonPlan draft = SeasonPlanTestFixture.validPlanDraft("planner");
        draft.setTargetTerritories(java.util.List.of("south-grove"));
        SeasonPlan other = plans.createDraft(draft, ActorRole.FAIRY_PLANNER, "planner");
        plans.submitForApproval(other.id(), ActorRole.FAIRY_PLANNER, "planner");
        var reserve = fixture.pollenService().requestReservation(other.id(), 100, ActorRole.POLLEN_GUARDIAN, "pollen");
        fixture.pollenService().confirmReservation(reserve.id(), 100, "ok", ActorRole.POLLEN_GUARDIAN, "pollen");
        plans.verify(other.id(), ActorRole.CHIEF_GUARDIAN, "chief");
        plans.approve(other.id(), "ok", ActorRole.CHIEF_GUARDIAN, "chief");
        MagicTask second = tasks.createTask(other.id(), SeasonPlanTestFixture.validTaskDraft(), ActorRole.TALENT_COORDINATOR, "coordinator");
        int auditCount = fixture.auditLogService().entriesFor(other.id()).size();
        assertThatThrownBy(() -> assign(second, "shared-fairy", AssignmentStatus.CONFIRMED))
                .isInstanceOf(DomainValidationException.class);
        assertThat(tasks.assignmentsForTask(second.id())).isEmpty();
        assertThat(fixture.auditLogService().entriesFor(other.id())).hasSize(auditCount);
    }

    @Test
    void movingAnAssignedTaskCannotBypassScheduleValidation() {
        MagicTask first = createTask();
        var laterWindow = new TimeWindow(LocalDateTime.parse("2026-03-01T10:00:00"), LocalDateTime.parse("2026-03-01T11:00:00"));
        MagicTask later = tasks.createTask(plan.id(), new MagicTask("Later", "", "SPRING_WEATHER", laterWindow, MagicTaskStatus.READY),
                ActorRole.TALENT_COORDINATOR, "coordinator");
        assign(first, "shared-fairy", AssignmentStatus.CONFIRMED);
        assign(later, "shared-fairy", AssignmentStatus.CONFIRMED);
        int auditCount = fixture.auditLogService().entriesFor(plan.id()).size();
        assertThatThrownBy(() -> tasks.updateTask(later.id(), new MagicTask("Later", "", "SPRING_WEATHER", first.timeWindow(), MagicTaskStatus.READY),
                ActorRole.TALENT_COORDINATOR, "coordinator")).isInstanceOf(DomainValidationException.class);
        assertThat(tasks.get(later.id()).timeWindow()).isEqualTo(laterWindow);
        assertThat(fixture.auditLogService().entriesFor(plan.id())).hasSize(auditCount);
        assertThat(tasks.updateTask(later.id(), new MagicTask("Renamed", "", "SPRING_WEATHER", laterWindow, MagicTaskStatus.READY),
                ActorRole.TALENT_COORDINATOR, "coordinator").title()).isEqualTo("Renamed");
    }

    private MagicTask createTask() {
        return tasks.createTask(plan.id(), SeasonPlanTestFixture.validTaskDraft(), ActorRole.TALENT_COORDINATOR, "coordinator");
    }

    private FairyAssignment assign(MagicTask task, String fairyId, AssignmentStatus status) {
        return tasks.assignFairy(task.id(), fairyId, "LEAD", "SPRING_WEATHER", status, ActorRole.TALENT_COORDINATOR, "coordinator");
    }
}
