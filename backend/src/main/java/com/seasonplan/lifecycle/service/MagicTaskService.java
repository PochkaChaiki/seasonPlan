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

    public MagicTaskService(MagicTaskRepository tasks, FairyAssignmentRepository assignments,
                            SeasonPlanRepository plans, AuditLogService auditLogService) {
        this.tasks = tasks;
        this.assignments = assignments;
        this.plans = plans;
        this.auditLogService = auditLogService;
    }

    public MagicTask createTask(UUID seasonPlanId, MagicTask draft, ActorRole actorRole, String actorId) {
        LifecycleAccessPolicy.require(actorRole, LifecyclePermission.CREATE_TASK);
        if (plans.findById(seasonPlanId)
                .orElseThrow(() -> new NotFoundException("season plan not found: " + seasonPlanId))
                .status() != SeasonPlanStatus.APPROVED) {
            ServiceSupport.reject("seasonPlanStatus", "tasks can only be created for APPROVED plans");
        }
        validateTask(draft);
        draft.setId(UUID.randomUUID());
        draft.setSeasonPlanId(seasonPlanId);
        if (draft.status() == null) {
            draft.updateFrom(new MagicTask(draft.title(), draft.description(), draft.requiredTalent(), draft.timeWindow(), MagicTaskStatus.READY));
        }
        tasks.save(draft, actorId);
        auditLogService.append(seasonPlanId, AuditAction.MAGIC_TASK_CREATED, actorId, draft.title());
        return draft;
    }

    public MagicTask updateTask(UUID taskId, MagicTask draft, ActorRole actorRole, String actorId) {
        LifecycleAccessPolicy.require(actorRole, LifecyclePermission.UPDATE_TASK);
        MagicTask existing = get(taskId);
        validateTask(draft);
        existing.updateFrom(draft);
        return tasks.save(existing, actorId);
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
        MagicTask task = get(magicTaskId);
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
        FairyAssignment assignment = new FairyAssignment(UUID.randomUUID(), magicTaskId, fairyId, role, talent, status);
        assignments.save(assignment, actorId);
        auditLogService.append(task.seasonPlanId(), AuditAction.FAIRY_ASSIGNED, actorId, fairyId);
        return assignment;
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

    private void validateTask(MagicTask task) {
        LinkedHashMap<String, List<String>> errors = new LinkedHashMap<>();
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
