package com.seasonplan.work.application;

import com.seasonplan.work.domain.*;
import com.seasonplan.work.application.port.*;
import com.seasonplan.season.api.CommandTransaction;
import com.seasonplan.season.api.SeasonQueries;
import com.seasonplan.audit.api.*;
import com.seasonplan.identity.api.ActorRole;
import com.seasonplan.shared.application.NotFoundException;
import com.seasonplan.identity.api.LifecycleAccessPolicy;
import com.seasonplan.identity.api.LifecyclePermission;
import java.time.Clock;
import java.util.*;

/** Work command orchestration. Business decisions are delegated to the work domain. */
public class MagicTaskService {
    private final MagicTaskRepository tasks;
    private final FairyAssignmentRepository assignments;
    private final SeasonQueries seasons;
    private final AuditTrail audit;
    private final CommandTransaction transactions;
    private final Clock clock;

    public MagicTaskService(MagicTaskRepository tasks, FairyAssignmentRepository assignments, SeasonQueries seasons,
                            AuditTrail audit, CommandTransaction transactions, Clock clock) {
        this.tasks = tasks; this.assignments = assignments; this.seasons = seasons;
        this.audit = audit; this.transactions = transactions; this.clock = clock;
    }

    public MagicTask createTask(UUID planId, MagicTask draft, ActorRole role, String actorId) {
        LifecycleAccessPolicy.require(role, LifecyclePermission.CREATE_TASK);
        return transactions.execute(planId, () -> {
            var plan = seasons.get(planId);
            TaskPreparationPolicy.requirePlanStatus(plan.status(), true);
            TaskPreparationPolicy.validateCreation(draft, plan.executionRules());
            TaskPreparationPolicy.initialize(draft, UUID.randomUUID(), planId);
            MagicTask saved = tasks.save(draft, actorId);
            audit.append(planId, AuditAction.MAGIC_TASK_CREATED, actorId, draft.title());
            return saved;
        });
    }

    public MagicTask updateTask(UUID id, MagicTask draft, ActorRole role, String actorId) {
        LifecycleAccessPolicy.require(role, LifecyclePermission.UPDATE_TASK);
        UUID planId = get(id).seasonPlanId();
        return transactions.execute(planId, () -> {
            MagicTask existing = get(id);
            var plan = seasons.get(planId);
            TaskPreparationPolicy.requirePlanStatus(plan.status(), false);
            var attached = assignments.findByMagicTaskId(id);
            TaskPreparationPolicy.validateUpdate(existing, draft, plan.executionRules(), attached);
            var participants = TaskPreparationPolicy.confirmedParticipants(attached);
            assignments.lockParticipants(participants);
            for (String participant : participants) {
                TaskPreparationPolicy.requireReschedulable(id, draft.timeWindow(), assignedTasks(participant));
            }
            existing.updateFrom(draft);
            MagicTask saved = tasks.save(existing, actorId);
            audit.append(planId, AuditAction.MAGIC_TASK_UPDATED, actorId, id.toString());
            return saved;
        });
    }

    public FairyAssignment assignFairy(UUID taskId, String fairyId, String taskRole, String talent,
                                       AssignmentStatus status, ActorRole role, String actorId) {
        LifecycleAccessPolicy.require(role, LifecyclePermission.ASSIGN_FAIRY);
        UUID planId = get(taskId).seasonPlanId();
        return transactions.execute(planId, () -> {
            MagicTask task = get(taskId);
            var plan = seasons.get(planId);
            TaskPreparationPolicy.requirePlanStatus(plan.status(), false);
            TaskPreparationPolicy.requireAssignmentMutable(task, plan.executionRules());
            assignments.lockParticipants(TaskPreparationPolicy.participantLocks(fairyId, status));
            boolean conflict = TaskPreparationPolicy.needsScheduleCheck(status)
                    && TaskPreparationPolicy.scheduleConflict(taskId, task.timeWindow(), assignedTasks(fairyId));
            var assignment = TaskPreparationPolicy.assignment(UUID.randomUUID(), task, fairyId, taskRole, talent, status, conflict, clock.instant());
            assignments.save(assignment, actorId);
            audit.append(planId, AuditAction.FAIRY_ASSIGNED, actorId, fairyId);
            return assignment;
        });
    }

    public List<MagicTask> listTasksBySeasonPlan(UUID id) { return tasks.findBySeasonPlanId(id); }
    public MagicTask get(UUID id) { return tasks.findById(id).orElseThrow(() -> new NotFoundException("magic task not found: " + id)); }
    public List<FairyAssignment> assignmentsForTask(UUID id) { return assignments.findByMagicTaskId(id); }
    public boolean validateTalentCompatibility(MagicTask task, String talent) { return TaskPreparationPolicy.compatible(task, talent); }
    public boolean validateRequiredRoles(MagicTask task) { return TaskPreparationPolicy.hasConfirmedAssignment(assignmentsForTask(task.id())); }
    private List<MagicTask> assignedTasks(String fairyId) {
        return assignments.findConfirmedByFairyId(fairyId).stream().map(assignment -> get(assignment.magicTaskId())).toList();
    }
}
