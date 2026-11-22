package com.seasonplan.work.application;

import com.seasonplan.work.api.*;
import com.seasonplan.work.domain.MagicTask;
import com.seasonplan.work.domain.FairyAssignment;

final class WorkFactMapper {
    private WorkFactMapper() { }
    static MagicTask draft(TaskDraft input) { return new MagicTask(input.title(), input.description(), input.requiredTalent(), input.timeWindow(), input.status(), input.category()); }
    static TaskFacts task(MagicTask task) { return new TaskFacts(task.id(), task.seasonPlanId(), task.title(), task.description(), task.requiredTalent(), task.timeWindow(), task.status(), task.category()); }
    static AssignmentFacts assignment(FairyAssignment value) { return new AssignmentFacts(value.id(), value.magicTaskId(), value.fairyId(), value.role(), value.talent(), value.status(), value.confirmedAt()); }
}
