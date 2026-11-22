package com.seasonplan.work.domain;

import com.seasonplan.reference.domain.ExecutionRuleSnapshot;
import com.seasonplan.season.domain.SeasonPlanStatus;
import com.seasonplan.shared.domain.*;
import java.time.Instant;
import java.util.*;

/** Task, talent, participation and scheduling rules, evaluated only from supplied facts. */
public final class TaskPreparationPolicy {
    private TaskPreparationPolicy() { }

    public static void requirePlanStatus(SeasonPlanStatus status, boolean creating) {
        if (creating && status != SeasonPlanStatus.APPROVED) reject("seasonPlanStatus", "tasks can only be created for APPROVED plans");
        if (!creating && !EnumSet.of(SeasonPlanStatus.APPROVED, SeasonPlanStatus.RITUAL_READY, SeasonPlanStatus.ACTIVE,
                SeasonPlanStatus.CORRECTION, SeasonPlanStatus.EMERGENCY).contains(status)) reject("status", "operation is not allowed in " + status);
    }

    public static void validateCreation(MagicTask task, ExecutionRuleSnapshot rules) {
        validateTask(task);
        if (task.status() == MagicTaskStatus.COMPLETED) reject("status", "a task cannot be created completed before an assignment is confirmed");
        validateCategory(task, rules);
    }

    public static void validateUpdate(MagicTask existing, MagicTask draft, ExecutionRuleSnapshot rules, List<FairyAssignment> assignments) {
        requireMutable(existing);
        validateTask(draft);
        validateCategory(draft, rules);
        if (draft.status() == MagicTaskStatus.COMPLETED && assignments.stream().noneMatch(assignment ->
                assignment.status() == AssignmentStatus.CONFIRMED && assignment.confirmedAt() != null && draft.requiredTalent().equals(assignment.talent()))) {
            reject("assignments", "a confirmed compatible assignment with a confirmation timestamp is required before completion");
        }
    }

    public static void requireAssignmentMutable(MagicTask task, ExecutionRuleSnapshot rules) {
        requireRules(rules);
        requireMutable(task);
    }

    public static FairyAssignment assignment(UUID id, MagicTask task, String fairyId, String role, String talent,
                                              AssignmentStatus status, boolean scheduleConflict, Instant now) {
        var errors = new LinkedHashMap<String, List<String>>();
        if (fairyId == null || fairyId.isBlank()) ValidationSupport.add(errors, "fairyId", "fairy id is required");
        if (role == null || role.isBlank()) ValidationSupport.add(errors, "role", "role is required");
        if (!compatible(task, talent)) ValidationSupport.add(errors, "talent", "fairy talent must match required task talent");
        if (status == AssignmentStatus.CONFIRMED && scheduleConflict) ValidationSupport.add(errors, "fairyId", "fairy has a schedule conflict");
        ValidationSupport.throwIfAny(errors);
        return new FairyAssignment(id, task.id(), fairyId, role, talent, status, status == AssignmentStatus.CONFIRMED ? now : null);
    }

    public static boolean compatible(MagicTask task, String talent) { return task.requiredTalent() != null && task.requiredTalent().equals(talent); }
    public static boolean hasConfirmedAssignment(List<FairyAssignment> assignments) { return assignments.stream().anyMatch(a -> a.status() == AssignmentStatus.CONFIRMED); }
    public static List<String> confirmedParticipants(List<FairyAssignment> assignments) {
        return assignments.stream().filter(a -> a.status() == AssignmentStatus.CONFIRMED).map(FairyAssignment::fairyId).distinct().sorted().toList();
    }
    public static List<String> participantLocks(String fairyId, AssignmentStatus status) {
        return status == AssignmentStatus.CONFIRMED && fairyId != null && !fairyId.isBlank() ? List.of(fairyId) : List.of();
    }
    public static boolean needsScheduleCheck(AssignmentStatus status) { return status == AssignmentStatus.CONFIRMED; }
    public static boolean scheduleConflict(UUID excludedTask, TimeWindow proposed, List<MagicTask> assignedTasks) {
        return assignedTasks.stream().filter(task -> !task.id().equals(excludedTask)).anyMatch(task -> task.timeWindow().overlaps(proposed));
    }
    public static void requireReschedulable(UUID taskId, TimeWindow proposed, List<MagicTask> assignedTasks) {
        if (scheduleConflict(taskId, proposed, assignedTasks)) reject("timeWindow", "a confirmed participant has a schedule conflict");
    }
    public static void initialize(MagicTask draft, UUID taskId, UUID planId) {
        draft.setId(taskId); draft.setSeasonPlanId(planId);
        if (draft.status() == null) draft.updateFrom(new MagicTask(draft.title(), draft.description(), draft.requiredTalent(), draft.timeWindow(), MagicTaskStatus.READY, draft.category()));
    }

    private static void requireMutable(MagicTask task) {
        if (task.status() == MagicTaskStatus.COMPLETED || task.status() == MagicTaskStatus.CANCELLED) reject("task", "terminal task is immutable");
    }
    private static void requireRules(ExecutionRuleSnapshot rules) {
        if (rules == null) reject("executionRules", "Legacy plan has no pinned execution rules; operator remediation required");
    }
    private static void validateCategory(MagicTask task, ExecutionRuleSnapshot rules) {
        requireRules(rules);
        if (task.category() == null || task.category().isBlank()) {
            if (task.status() == MagicTaskStatus.COMPLETED) reject("category", "a catalog category is required before completion");
            return;
        }
        if (rules.seasons().values().stream().noneMatch(season -> season.categories().contains(task.category()))) reject("category", "task category is not in the pinned catalog");
    }
    private static void validateTask(MagicTask task) {
        var errors = new LinkedHashMap<String, List<String>>();
        if (task.status() == MagicTaskStatus.CANCELLED) ValidationSupport.add(errors, "status", "task cancellation requires a reason and is not supported by this command");
        if (task.title() == null || task.title().isBlank()) ValidationSupport.add(errors, "title", "title is required");
        if (task.requiredTalent() == null || task.requiredTalent().isBlank()) ValidationSupport.add(errors, "requiredTalent", "required talent is required");
        if (task.timeWindow() == null || task.timeWindow().start() == null || task.timeWindow().end() == null || !task.timeWindow().start().isBefore(task.timeWindow().end()))
            ValidationSupport.add(errors, "timeWindow", "valid time window is required");
        ValidationSupport.throwIfAny(errors);
    }
    private static void reject(String field, String message) { throw new DomainValidationException(Map.of(field, List.of(message))); }
}
