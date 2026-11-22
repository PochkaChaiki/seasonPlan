package com.seasonplan.work.application;

import com.seasonplan.work.api.*;
import com.seasonplan.work.domain.AssignmentStatus;
import com.seasonplan.identity.api.ActorRole;
import java.util.*;

/** Isolates immutable public inputs and snapshots command results directly, without rereading state. */
public final class WorkPreparationFacade implements WorkPreparation {
    private final MagicTaskService service;
    private final WorkQueries queries;
    public WorkPreparationFacade(MagicTaskService service, WorkQueries queries) { this.service = service; this.queries = queries; }
    @Override public TaskFacts createTask(UUID planId, TaskDraft draft, ActorRole role, String actorId) { return WorkFactMapper.task(service.createTask(planId, WorkFactMapper.draft(draft), role, actorId)); }
    @Override public TaskFacts updateTask(UUID id, TaskDraft draft, ActorRole role, String actorId) { return WorkFactMapper.task(service.updateTask(id, WorkFactMapper.draft(draft), role, actorId)); }
    @Override public List<TaskFacts> listTasksBySeasonPlan(UUID planId) { return queries.tasksForPlan(planId); }
    @Override public TaskFacts get(UUID id) { return queries.get(id); }
    @Override public AssignmentFacts assignFairy(UUID taskId, String fairyId, String role, String talent, AssignmentStatus status, ActorRole actorRole, String actorId) { return WorkFactMapper.assignment(service.assignFairy(taskId, fairyId, role, talent, status, actorRole, actorId)); }
    @Override public List<AssignmentFacts> assignmentsForTask(UUID id) { return queries.assignmentsForTask(id); }
}
