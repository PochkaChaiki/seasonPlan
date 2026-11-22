package com.seasonplan.work.application;

import com.seasonplan.work.api.TaskFacts;
import com.seasonplan.work.api.AssignmentFacts;
import com.seasonplan.work.api.WorkQueries;
import com.seasonplan.work.application.port.MagicTaskRepository;
import com.seasonplan.work.application.port.FairyAssignmentRepository;
import java.util.List;
import java.util.UUID;

/** Copies owner records without exposing mutable task or assignment objects. */

public final class WorkQueryService implements WorkQueries {
    private final MagicTaskRepository tasks;
    private final FairyAssignmentRepository assignments;
    public WorkQueryService(MagicTaskRepository tasks, FairyAssignmentRepository assignments) {
        this.tasks = tasks; this.assignments = assignments;
    }
    @Override public TaskFacts get(UUID taskId) {
        return WorkFactMapper.task(tasks.findById(taskId).orElseThrow(() -> new com.seasonplan.shared.application.NotFoundException("magic task not found: " + taskId)));
    }
    @Override public List<TaskFacts> tasksForPlan(UUID planId) { return tasks.findBySeasonPlanId(planId).stream().map(WorkFactMapper::task).toList(); }
    @Override public List<AssignmentFacts> assignmentsForTask(UUID taskId) { return assignments.findByMagicTaskId(taskId).stream().map(WorkFactMapper::assignment).toList(); }
}
