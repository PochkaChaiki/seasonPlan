package com.seasonplan.lifecycle.api;

import com.seasonplan.lifecycle.domain.AssignmentStatus;
import com.seasonplan.lifecycle.domain.ChecklistItem;
import com.seasonplan.lifecycle.domain.FairyAssignment;
import com.seasonplan.lifecycle.domain.MagicTask;
import com.seasonplan.lifecycle.domain.MagicTaskStatus;
import com.seasonplan.lifecycle.domain.PollenReservation;
import com.seasonplan.lifecycle.domain.PollenReservationStatus;
import com.seasonplan.lifecycle.domain.SeasonPlan;
import com.seasonplan.lifecycle.domain.SeasonPlanStatus;
import com.seasonplan.lifecycle.domain.TimeWindow;

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
    SeasonPlan toDraft() {
        SeasonPlan draft = new SeasonPlan();
        draft.setSeasonType(seasonType);
        draft.setTargetTerritories(targetTerritories);
        draft.setTimeWindows(timeWindows);
        draft.setNaturalPhenomenonParameters(naturalPhenomenonParameters);
        draft.setTargetClimateMetrics(targetClimateMetrics);
        return draft;
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
        List<ChecklistItemResponse> readinessChecklist,
        String createdBy,
        String updatedBy
) {
    static SeasonPlanResponse from(SeasonPlan plan) {
        return new SeasonPlanResponse(plan.id(), plan.seasonType(), plan.targetTerritories(), plan.timeWindows(),
                plan.naturalPhenomenonParameters(), plan.targetClimateMetrics(), plan.status(),
                plan.approvalComment(), plan.readinessChecklist().stream().map(ChecklistItemResponse::from).toList(),
                plan.createdBy(), plan.updatedBy());
    }
}

record ChecklistItemResponse(String key, boolean complete) {
    static ChecklistItemResponse from(ChecklistItem item) {
        return new ChecklistItemResponse(item.key(), item.complete());
    }
}

record MagicTaskRequest(String title, String description, String requiredTalent, TimeWindow timeWindow, MagicTaskStatus status) {
    MagicTask toDraft() {
        return new MagicTask(title, description, requiredTalent, timeWindow, status);
    }
}

record MagicTaskResponse(UUID id, UUID seasonPlanId, String title, String description, String requiredTalent, TimeWindow timeWindow, MagicTaskStatus status) {
    static MagicTaskResponse from(MagicTask task) {
        return new MagicTaskResponse(task.id(), task.seasonPlanId(), task.title(), task.description(), task.requiredTalent(), task.timeWindow(), task.status());
    }
}

record PollenReservationResponse(UUID id, UUID seasonPlanId, int requestedAmount, Integer confirmedAmount, PollenReservationStatus status, String comment, String confirmedBy) {
    static PollenReservationResponse from(PollenReservation reservation) {
        return new PollenReservationResponse(reservation.id(), reservation.seasonPlanId(), reservation.requestedAmount(),
                reservation.confirmedAmount(), reservation.status(), reservation.comment(), reservation.confirmedBy());
    }
}

record FairyAssignmentResponse(UUID id, UUID magicTaskId, String fairyId, String role, String talent, AssignmentStatus status) {
    static FairyAssignmentResponse from(FairyAssignment assignment) {
        return new FairyAssignmentResponse(assignment.id(), assignment.magicTaskId(), assignment.fairyId(), assignment.role(), assignment.talent(), assignment.status());
    }
}

record DecisionRequest(String comment) {
}

record ReservationRequest(int requestedAmount, UUID requestId) {
}

record ReservationDecisionRequest(int confirmedAmount, String comment) {
}

record AssignmentRequest(String fairyId, String role, String talent, AssignmentStatus status) {
}

record ReadinessResponse(boolean ready, Map<String, List<String>> errors) {
}
