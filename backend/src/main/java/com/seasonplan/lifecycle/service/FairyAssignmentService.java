package com.seasonplan.lifecycle.service;

import com.seasonplan.lifecycle.domain.ActorRole;
import com.seasonplan.lifecycle.domain.AssignmentStatus;
import com.seasonplan.lifecycle.domain.AuditAction;
import com.seasonplan.lifecycle.domain.FairyAssignment;
import com.seasonplan.lifecycle.domain.MagicTask;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.UUID;

public class FairyAssignmentService {
    private final FairyAssignmentRepository assignments;
    private final MagicTaskRepository magicTasks;
    private final FairySchedulePolicy fairySchedulePolicy;
    private final AuditLogService auditLogService;

    public FairyAssignmentService(
            FairyAssignmentRepository assignments,
            MagicTaskRepository magicTasks,
            FairySchedulePolicy fairySchedulePolicy,
            AuditLogService auditLogService
    ) {
        this.assignments = assignments;
        this.magicTasks = magicTasks;
        this.fairySchedulePolicy = fairySchedulePolicy;
        this.auditLogService = auditLogService;
    }

    public FairyAssignment assignFairy(UUID magicTaskId, String fairyId, String role, String talent, AssignmentStatus status, ActorRole actorRole, String actorId) {
        ValidationSupport.requireRole(actorRole, ActorRole.TALENT_COORDINATOR);
        MagicTask task = magicTasks.findById(magicTaskId).orElseThrow(() -> new NotFoundException("magic task not found: " + magicTaskId));
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
        if (status == AssignmentStatus.CONFIRMED && fairySchedulePolicy.hasScheduleConflict(task, fairyId)) {
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

}
