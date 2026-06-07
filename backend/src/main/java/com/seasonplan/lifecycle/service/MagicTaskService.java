package com.seasonplan.lifecycle.service;

import com.seasonplan.lifecycle.domain.ActorRole;
import com.seasonplan.lifecycle.domain.DomainValidationException;
import com.seasonplan.lifecycle.domain.AuditAction;
import com.seasonplan.lifecycle.domain.MagicTask;
import com.seasonplan.lifecycle.domain.MagicTaskStatus;
import com.seasonplan.lifecycle.domain.SeasonPlanStatus;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.UUID;

public class MagicTaskService {
    private final MagicTaskRepository tasks;
    private final SeasonPlanService seasonPlanService;
    private final AuditLogService auditLogService;

    public MagicTaskService(MagicTaskRepository tasks, SeasonPlanService seasonPlanService, AuditLogService auditLogService) {
        this.tasks = tasks;
        this.seasonPlanService = seasonPlanService;
        this.auditLogService = auditLogService;
    }

    public MagicTask createTask(UUID seasonPlanId, MagicTask draft, ActorRole actorRole, String actorId) {
        ValidationSupport.requireRole(actorRole, ActorRole.TALENT_COORDINATOR);
        if (seasonPlanService.get(seasonPlanId).status() != SeasonPlanStatus.APPROVED) {
            throw new DomainValidationException(new LinkedHashMap<>() {{
                put("seasonPlanStatus", List.of("tasks can only be created for APPROVED plans"));
            }});
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
        ValidationSupport.requireRole(actorRole, ActorRole.TALENT_COORDINATOR);
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
