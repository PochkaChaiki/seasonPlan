package com.seasonplan.lifecycle.service;

import com.seasonplan.lifecycle.authorization.LifecycleAccessPolicy;
import com.seasonplan.lifecycle.authorization.LifecyclePermission;

import com.seasonplan.lifecycle.repository.SeasonPlanRepository;
import com.seasonplan.lifecycle.repository.MagicTaskRepository;
import com.seasonplan.lifecycle.repository.FairyAssignmentRepository;
import com.seasonplan.lifecycle.exception.NotFoundException;




import com.seasonplan.security.identity.ActorRole;
import com.seasonplan.lifecycle.domain.AuditAction;
import com.seasonplan.lifecycle.domain.MagicTask;
import com.seasonplan.lifecycle.domain.MagicTaskStatus;
import com.seasonplan.lifecycle.domain.SeasonPlanStatus;
import com.seasonplan.lifecycle.domain.AssignmentStatus;
import com.seasonplan.lifecycle.domain.FairyAssignment;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.UUID;

/** Task preparation and fairy assignments share task validation and scheduling data. */
public class MagicTaskService {
    private final MagicTaskRepository tasks;
    private final FairyAssignmentRepository assignments;
    private final SeasonPlanRepository plans;
    private final AuditLogService auditLogService;
    private final CommandTransaction transactions;
    private final java.time.Clock clock;

    public MagicTaskService(MagicTaskRepository tasks, FairyAssignmentRepository assignments,
                            SeasonPlanRepository plans, AuditLogService auditLogService, CommandTransaction transactions, java.time.Clock clock) {
        this.tasks = tasks;
        this.assignments = assignments;
        this.plans = plans;
        this.auditLogService = auditLogService;
        this.transactions = transactions;
        this.clock = clock;
    }

    public MagicTask createTask(UUID seasonPlanId, MagicTask draft, ActorRole actorRole, String actorId) {
        LifecycleAccessPolicy.require(actorRole, LifecyclePermission.CREATE_TASK);
        return transactions.execute(seasonPlanId, () -> {
        if (plans.findById(seasonPlanId)
                .orElseThrow(() -> new NotFoundException("season plan not found: " + seasonPlanId))
                .status() != SeasonPlanStatus.APPROVED) {
            ServiceSupport.reject("seasonPlanStatus", "tasks can only be created for APPROVED plans");
        }
        validateTask(draft);
        if (draft.status() == MagicTaskStatus.COMPLETED) ServiceSupport.reject("status", "a task cannot be created completed before an assignment is confirmed");
        validateCategory(draft, ServiceSupport.plan(plans, seasonPlanId));
        draft.setId(UUID.randomUUID());
        draft.setSeasonPlanId(seasonPlanId);
        if (draft.status() == null) {
            draft.updateFrom(new MagicTask(draft.title(), draft.description(), draft.requiredTalent(), draft.timeWindow(), MagicTaskStatus.READY, draft.category()));
        }
        tasks.save(draft, actorId);
        auditLogService.append(seasonPlanId, AuditAction.MAGIC_TASK_CREATED, actorId, draft.title());
        return draft;
        });
    }

    public MagicTask updateTask(UUID taskId, MagicTask draft, ActorRole actorRole, String actorId) {
        LifecycleAccessPolicy.require(actorRole, LifecyclePermission.UPDATE_TASK);
        UUID planId = get(taskId).seasonPlanId();
        return transactions.execute(planId, () -> {
            MagicTask existing = get(taskId);
            var plan = ServiceSupport.plan(plans, planId, SeasonPlanStatus.APPROVED, SeasonPlanStatus.RITUAL_READY, SeasonPlanStatus.ACTIVE);
            if (existing.status() == MagicTaskStatus.COMPLETED || existing.status() == MagicTaskStatus.CANCELLED)
                ServiceSupport.reject("task", "terminal task is immutable");
            validateTask(draft);
            validateCategory(draft, plan);
            if (draft.status() == MagicTaskStatus.COMPLETED && assignments.findByMagicTaskId(taskId).stream().noneMatch(assignment ->
                    assignment.status() == AssignmentStatus.CONFIRMED && assignment.confirmedAt() != null
                            && draft.requiredTalent().equals(assignment.talent()))) {
                ServiceSupport.reject("assignments", "a confirmed compatible assignment with a confirmation timestamp is required before completion");
            }
            existing.updateFrom(draft);
            MagicTask saved = tasks.save(existing, actorId);
            auditLogService.append(planId, AuditAction.MAGIC_TASK_UPDATED, actorId, taskId.toString());
            return saved;
        });
    }

    public List<MagicTask> listTasksBySeasonPlan(UUID seasonPlanId) {
        return tasks.findBySeasonPlanId(seasonPlanId);
    }

    public MagicTask get(UUID taskId) {
        return tasks.findById(taskId).orElseThrow(() -> new NotFoundException("magic task not found: " + taskId));
    }

    public FairyAssignment assignFairy(UUID magicTaskId, String fairyId, String role, String talent,
                                       AssignmentStatus status, ActorRole actorRole, String actorId) {
        LifecycleAccessPolicy.require(actorRole, LifecyclePermission.ASSIGN_FAIRY);
        UUID planId = get(magicTaskId).seasonPlanId();
        return transactions.execute(planId, () -> {
        MagicTask task = get(magicTaskId);
        var plan = ServiceSupport.plan(plans, planId, SeasonPlanStatus.APPROVED, SeasonPlanStatus.RITUAL_READY, SeasonPlanStatus.ACTIVE);
        if (plan.executionRules() == null) ServiceSupport.reject("executionRules", "Legacy plan has no pinned execution rules; operator remediation required");
        if (task.status() == MagicTaskStatus.COMPLETED || task.status() == MagicTaskStatus.CANCELLED) ServiceSupport.reject("task", "terminal task is immutable");
        LinkedHashMap<String, List<String>> errors = new LinkedHashMap<>();
        if (fairyId == null || fairyId.isBlank()) {
            ValidationSupport.add(errors, "fairyId", "fairy id is required");
        }
        if (role == null || role.isBlank()) {
            ValidationSupport.add(errors, "role", "role is required");
        }
        if (!validateTalentCompatibility(task, talent)) {
            ValidationSupport.add(errors, "talent", "fairy talent must match required task talent");
        }
        if (status == AssignmentStatus.CONFIRMED && hasScheduleConflict(task, fairyId)) {
            ValidationSupport.add(errors, "fairyId", "fairy has a schedule conflict");
        }
        ValidationSupport.throwIfAny(errors);
        FairyAssignment assignment = new FairyAssignment(UUID.randomUUID(), magicTaskId, fairyId, role, talent, status, status == AssignmentStatus.CONFIRMED ? clock.instant() : null);
        assignments.save(assignment, actorId);
        auditLogService.append(task.seasonPlanId(), AuditAction.FAIRY_ASSIGNED, actorId, fairyId);
        return assignment;
        });
    }

    public boolean validateTalentCompatibility(MagicTask task, String talent) {
        return task.requiredTalent() != null && task.requiredTalent().equals(talent);
    }

    public boolean validateRequiredRoles(MagicTask task) {
        return assignments.findByMagicTaskId(task.id()).stream()
                .anyMatch(assignment -> assignment.status() == AssignmentStatus.CONFIRMED);
    }

    public List<FairyAssignment> assignmentsForTask(UUID taskId) {
        return assignments.findByMagicTaskId(taskId);
    }

    private boolean hasScheduleConflict(MagicTask task, String fairyId) {
        return assignments.findConfirmedByFairyId(fairyId).stream()
                .map(assignment -> get(assignment.magicTaskId()))
                .filter(existingTask -> existingTask.seasonPlanId().equals(task.seasonPlanId()))
                .anyMatch(existingTask -> existingTask.timeWindow().overlaps(task.timeWindow()));
    }

    private void validateCategory(MagicTask task, com.seasonplan.lifecycle.domain.SeasonPlan plan) {
        var rules = plan.executionRules();
        if (rules == null) ServiceSupport.reject("executionRules", "Legacy plan has no pinned execution rules; operator remediation required");
        if (task.category() == null || task.category().isBlank()) {
            if (task.status() == MagicTaskStatus.COMPLETED) ServiceSupport.reject("category", "a catalog category is required before completion");
            return;
        }
        if (rules.seasons().values().stream().noneMatch(season -> season.categories().contains(task.category())))
            ServiceSupport.reject("category", "task category is not in the pinned catalog");
    }

    private void validateTask(MagicTask task) {
        LinkedHashMap<String, List<String>> errors = new LinkedHashMap<>();
        if (task.status() == MagicTaskStatus.CANCELLED) ValidationSupport.add(errors, "status", "task cancellation requires a reason and is not supported by this command");
        if (task.title() == null || task.title().isBlank()) {
            ValidationSupport.add(errors, "title", "title is required");
        }
        if (task.requiredTalent() == null || task.requiredTalent().isBlank()) {
            ValidationSupport.add(errors, "requiredTalent", "required talent is required");
        }
        if (task.timeWindow() == null || task.timeWindow().start() == null || task.timeWindow().end() == null
                || !task.timeWindow().start().isBefore(task.timeWindow().end())) {
            ValidationSupport.add(errors, "timeWindow", "valid time window is required");
        }
        ValidationSupport.throwIfAny(errors);
    }
}
