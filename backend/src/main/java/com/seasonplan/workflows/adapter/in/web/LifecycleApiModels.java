package com.seasonplan.workflows.adapter.in.web;

import com.seasonplan.work.domain.AssignmentStatus;
import com.seasonplan.season.domain.ApprovalCondition;
import com.seasonplan.season.api.SeasonFacts.ChecklistFact;
import com.seasonplan.work.api.AssignmentFacts;
import com.seasonplan.work.api.TaskFacts;
import com.seasonplan.work.api.TaskDraft;
import com.seasonplan.work.domain.MagicTaskStatus;
import com.seasonplan.pollen.api.ReservationFacts;
import com.seasonplan.pollen.domain.PollenReservationStatus;
import com.seasonplan.season.api.SeasonFacts;
import com.seasonplan.season.api.SeasonDraft;
import com.seasonplan.season.domain.SeasonPlanStatus;
import com.seasonplan.shared.domain.TimeWindow;

import java.util.List;
import java.util.Map;
import java.util.UUID;

record SeasonPlanRequest(
        String seasonType,
        List<String> targetTerritories,
        List<TimeWindow> timeWindows,
        Map<String, String> naturalPhenomenonParameters,
        Map<String, Double> targetClimateMetrics
) {
    SeasonDraft toDraft() {
        return new SeasonDraft(seasonType, targetTerritories, timeWindows, naturalPhenomenonParameters, targetClimateMetrics);
    }

}

record SeasonPlanResponse(
        UUID id,
        String seasonType,
        List<String> targetTerritories,
        List<TimeWindow> timeWindows,
        Map<String, String> naturalPhenomenonParameters,
        Map<String, Double> targetClimateMetrics,
        SeasonPlanStatus status,
        String approvalComment,
        List<ApprovalCondition> approvalConditions,
        List<ChecklistItemResponse> readinessChecklist,
        String createdBy,
        String updatedBy
) {
    static SeasonPlanResponse from(SeasonFacts plan) {
        return new SeasonPlanResponse(plan.id(), plan.seasonType(), plan.targetTerritories(), plan.timeWindows(),
                plan.naturalPhenomenonParameters(), plan.targetClimateMetrics(), plan.status(),
                plan.approvalComment(), plan.approvalConditions(), plan.readinessChecklist().stream().map(ChecklistItemResponse::from).toList(),
                plan.createdBy(), plan.updatedBy());
    }
}

record ChecklistItemResponse(String key, boolean complete) {
    static ChecklistItemResponse from(ChecklistFact item) {
        return new ChecklistItemResponse(item.key(), item.complete());
    }
}

record MagicTaskRequest(String title, String description, String requiredTalent, TimeWindow timeWindow, MagicTaskStatus status, String category) {
    TaskDraft toDraft() {
        return new TaskDraft(title, description, requiredTalent, timeWindow, status, category);
    }
}

record MagicTaskResponse(UUID id, UUID seasonPlanId, String title, String description, String requiredTalent, TimeWindow timeWindow, MagicTaskStatus status, String category) {
    static MagicTaskResponse from(TaskFacts task) {
        return new MagicTaskResponse(task.id(), task.seasonPlanId(), task.title(), task.description(), task.requiredTalent(), task.timeWindow(), task.status(), task.category());
    }
}

record PollenReservationResponse(UUID id, UUID seasonPlanId, int requestedAmount, Integer confirmedAmount, PollenReservationStatus status, String comment, String confirmedBy) {
    static PollenReservationResponse from(ReservationFacts reservation) {
        return new PollenReservationResponse(reservation.id(), reservation.seasonPlanId(), reservation.requestedAmount(),
                reservation.confirmedAmount(), reservation.status(), reservation.comment(), reservation.confirmedBy());
    }
}

record FairyAssignmentResponse(UUID id, UUID magicTaskId, String fairyId, String role, String talent, AssignmentStatus status) {
    static FairyAssignmentResponse from(AssignmentFacts assignment) {
        return new FairyAssignmentResponse(assignment.id(), assignment.magicTaskId(), assignment.fairyId(), assignment.role(), assignment.talent(), assignment.status());
    }
}

record DecisionRequest(String comment, List<ApprovalCondition> conditions) {
}

record ReservationRequest(int requestedAmount, UUID requestId) {
}

record ReservationDecisionRequest(int confirmedAmount, String comment) {
}

record AssignmentRequest(String fairyId, String role, String talent, AssignmentStatus status) {
}

record ReadinessResponse(boolean ready, Map<String, List<String>> errors) {
}
