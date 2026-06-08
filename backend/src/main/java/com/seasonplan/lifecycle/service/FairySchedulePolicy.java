package com.seasonplan.lifecycle.service;

import com.seasonplan.lifecycle.domain.MagicTask;

public class FairySchedulePolicy {
    private final FairyAssignmentRepository assignments;
    private final MagicTaskRepository magicTasks;

    public FairySchedulePolicy(FairyAssignmentRepository assignments, MagicTaskRepository magicTasks) {
        this.assignments = assignments;
        this.magicTasks = magicTasks;
    }

    public boolean hasScheduleConflict(MagicTask task, String fairyId) {
        return assignments.findConfirmedByFairyId(fairyId).stream()
                .map(assignment -> magicTasks.findById(assignment.magicTaskId())
                        .orElseThrow(() -> new NotFoundException("magic task not found: " + assignment.magicTaskId())))
                .filter(existingTask -> existingTask.seasonPlanId().equals(task.seasonPlanId()))
                .anyMatch(existingTask -> existingTask.timeWindow().overlaps(task.timeWindow()));
    }
}
