package com.seasonplan.work.api;

import java.util.List;
import java.util.UUID;

/** Read-only work snapshots. Returned lists and every element are immutable. */
public interface WorkQueries {
    TaskFacts get(UUID taskId);
    List<TaskFacts> tasksForPlan(UUID planId);
    List<AssignmentFacts> assignmentsForTask(UUID taskId);
}
