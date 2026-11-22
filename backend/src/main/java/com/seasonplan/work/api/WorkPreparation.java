package com.seasonplan.work.api;

import com.seasonplan.work.domain.AssignmentStatus;
import com.seasonplan.identity.api.ActorRole;
import java.util.*;

/** Public work preparation commands; persistence remains private to this module. */
public interface WorkPreparation {
    TaskFacts createTask(UUID planId, TaskDraft draft, ActorRole role, String actorId);
    TaskFacts updateTask(UUID id, TaskDraft draft, ActorRole role, String actorId);
    List<TaskFacts> listTasksBySeasonPlan(UUID planId);
    TaskFacts get(UUID id);
    AssignmentFacts assignFairy(UUID taskId, String fairyId, String role, String talent, AssignmentStatus status, ActorRole actorRole, String actorId);
    List<AssignmentFacts> assignmentsForTask(UUID id);
}
