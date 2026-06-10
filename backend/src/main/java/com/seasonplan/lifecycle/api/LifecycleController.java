package com.seasonplan.lifecycle.api;

import com.seasonplan.lifecycle.domain.ActorRole;
import com.seasonplan.lifecycle.domain.AssignmentStatus;
import com.seasonplan.lifecycle.domain.ChecklistItem;
import com.seasonplan.lifecycle.domain.FairyAssignment;
import com.seasonplan.lifecycle.domain.MagicTask;
import com.seasonplan.lifecycle.domain.PollenReservation;
import com.seasonplan.lifecycle.domain.PollenReservationStatus;
import com.seasonplan.lifecycle.domain.SeasonPlan;
import com.seasonplan.lifecycle.domain.SeasonPlanStatus;
import com.seasonplan.lifecycle.domain.TimeWindow;
import com.seasonplan.lifecycle.service.FairyAssignmentService;
import com.seasonplan.lifecycle.service.MagicTaskService;
import com.seasonplan.lifecycle.service.PollenReservationService;
import com.seasonplan.lifecycle.service.ReadinessChecklistService;
import com.seasonplan.lifecycle.service.SeasonPlanService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
public class LifecycleController {
    private final SeasonPlanService seasonPlanService;
    private final PollenReservationService pollenReservationService;
    private final MagicTaskService magicTaskService;
    private final FairyAssignmentService fairyAssignmentService;
    private final ReadinessChecklistService readinessChecklistService;

    public LifecycleController(
            SeasonPlanService seasonPlanService,
            PollenReservationService pollenReservationService,
            MagicTaskService magicTaskService,
            FairyAssignmentService fairyAssignmentService,
            ReadinessChecklistService readinessChecklistService
    ) {
        this.seasonPlanService = seasonPlanService;
        this.pollenReservationService = pollenReservationService;
        this.magicTaskService = magicTaskService;
        this.fairyAssignmentService = fairyAssignmentService;
        this.readinessChecklistService = readinessChecklistService;
    }

    @PostMapping("/season-plans")
    public SeasonPlanResponse createPlan(@RequestBody SeasonPlanRequest request, @RequestHeader("X-Actor-Role") ActorRole role, @RequestHeader("X-Actor-Id") String actorId) {
        return SeasonPlanResponse.from(seasonPlanService.createDraft(request.toDraft(), role, actorId));
    }

    @PatchMapping("/season-plans/{id}")
    public SeasonPlanResponse updatePlan(@PathVariable UUID id, @RequestBody SeasonPlanRequest request, @RequestHeader("X-Actor-Role") ActorRole role, @RequestHeader("X-Actor-Id") String actorId) {
        return SeasonPlanResponse.from(seasonPlanService.updateDraft(id, request.toDraft(), role, actorId));
    }

    @GetMapping("/season-plans")
    public List<SeasonPlanResponse> listPlans() {
        return seasonPlanService.listPlans().stream().map(SeasonPlanResponse::from).toList();
    }

    @PostMapping("/season-plans/{id}/submit")
    public SeasonPlanResponse submit(@PathVariable UUID id, @RequestHeader("X-Actor-Role") ActorRole role, @RequestHeader("X-Actor-Id") String actorId) {
        return SeasonPlanResponse.from(seasonPlanService.submitForApproval(id, role, actorId));
    }

    @PostMapping("/season-plans/{id}/approve")
    public SeasonPlanResponse approve(@PathVariable UUID id, @RequestBody DecisionRequest request, @RequestHeader("X-Actor-Role") ActorRole role, @RequestHeader("X-Actor-Id") String actorId) {
        return SeasonPlanResponse.from(seasonPlanService.approve(id, request.comment(), role, actorId));
    }

    @PostMapping("/season-plans/{id}/return")
    public SeasonPlanResponse returnForRevision(@PathVariable UUID id, @RequestBody DecisionRequest request, @RequestHeader("X-Actor-Role") ActorRole role, @RequestHeader("X-Actor-Id") String actorId) {
        return SeasonPlanResponse.from(seasonPlanService.returnForRevision(id, request.comment(), role, actorId));
    }

    @GetMapping("/season-plans/{id}")
    public SeasonPlanResponse getPlan(@PathVariable UUID id) {
        return SeasonPlanResponse.from(seasonPlanService.get(id));
    }

    @GetMapping("/season-plans/{id}/readiness")
    public ReadinessResponse readiness(@PathVariable UUID id) {
        Map<String, List<String>> errors = readinessChecklistService.calculateChecklist(id);
        return new ReadinessResponse(errors.isEmpty(), errors);
    }

    @PostMapping("/season-plans/{id}/readiness-checklist/{key}/complete")
    public SeasonPlanResponse completeReadinessChecklistItem(@PathVariable UUID id, @PathVariable String key, @RequestHeader("X-Actor-Role") ActorRole role, @RequestHeader("X-Actor-Id") String actorId) {
        readinessChecklistService.completeItem(id, key, role, actorId);
        return SeasonPlanResponse.from(seasonPlanService.get(id));
    }

    @PostMapping("/season-plans/{id}/pollen-reservations")
    public PollenReservationResponse requestReservation(@PathVariable UUID id, @RequestBody ReservationRequest request, @RequestHeader("X-Actor-Role") ActorRole role, @RequestHeader("X-Actor-Id") String actorId) {
        return PollenReservationResponse.from(pollenReservationService.requestReservation(id, request.requestedAmount(), role, actorId));
    }

    @PostMapping("/pollen-reservations/{id}/confirm")
    public PollenReservationResponse confirmReservation(@PathVariable UUID id, @RequestBody ReservationDecisionRequest request, @RequestHeader("X-Actor-Role") ActorRole role, @RequestHeader("X-Actor-Id") String actorId) {
        return PollenReservationResponse.from(pollenReservationService.confirmReservation(id, request.confirmedAmount(), request.comment(), role, actorId));
    }

    @PostMapping("/pollen-reservations/{id}/reject")
    public PollenReservationResponse rejectReservation(@PathVariable UUID id, @RequestBody DecisionRequest request, @RequestHeader("X-Actor-Role") ActorRole role, @RequestHeader("X-Actor-Id") String actorId) {
        return PollenReservationResponse.from(pollenReservationService.rejectReservation(id, request.comment(), role, actorId));
    }

    @PostMapping("/season-plans/{id}/tasks")
    public MagicTaskResponse createTask(@PathVariable UUID id, @RequestBody MagicTaskRequest request, @RequestHeader("X-Actor-Role") ActorRole role, @RequestHeader("X-Actor-Id") String actorId) {
        return MagicTaskResponse.from(magicTaskService.createTask(id, request.toDraft(), role, actorId));
    }

    @PatchMapping("/tasks/{id}")
    public MagicTaskResponse updateTask(@PathVariable UUID id, @RequestBody MagicTaskRequest request, @RequestHeader("X-Actor-Role") ActorRole role, @RequestHeader("X-Actor-Id") String actorId) {
        return MagicTaskResponse.from(magicTaskService.updateTask(id, request.toDraft(), role, actorId));
    }

    @PostMapping("/tasks/{id}/assignments")
    public FairyAssignmentResponse assignFairy(@PathVariable UUID id, @RequestBody AssignmentRequest request, @RequestHeader("X-Actor-Role") ActorRole role, @RequestHeader("X-Actor-Id") String actorId) {
        return FairyAssignmentResponse.from(fairyAssignmentService.assignFairy(id, request.fairyId(), request.role(), request.talent(), request.status(), role, actorId));
    }

    @PostMapping("/season-plans/{id}/mark-ritual-ready")
    public SeasonPlanResponse markRitualReady(@PathVariable UUID id, @RequestHeader("X-Actor-Role") ActorRole role, @RequestHeader("X-Actor-Id") String actorId) {
        return SeasonPlanResponse.from(seasonPlanService.markRitualReady(id, role, actorId));
    }

    public record SeasonPlanRequest(
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

    public record SeasonPlanResponse(
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

    public record ChecklistItemResponse(String key, boolean complete) {
        static ChecklistItemResponse from(ChecklistItem item) {
            return new ChecklistItemResponse(item.key(), item.complete());
        }
    }

    public record MagicTaskRequest(String title, String description, String requiredTalent, TimeWindow timeWindow, com.seasonplan.lifecycle.domain.MagicTaskStatus status) {
        MagicTask toDraft() {
            return new MagicTask(title, description, requiredTalent, timeWindow, status);
        }
    }

    public record MagicTaskResponse(UUID id, UUID seasonPlanId, String title, String description, String requiredTalent, TimeWindow timeWindow, com.seasonplan.lifecycle.domain.MagicTaskStatus status) {
        static MagicTaskResponse from(MagicTask task) {
            return new MagicTaskResponse(task.id(), task.seasonPlanId(), task.title(), task.description(), task.requiredTalent(), task.timeWindow(), task.status());
        }
    }

    public record PollenReservationResponse(UUID id, UUID seasonPlanId, int requestedAmount, Integer confirmedAmount, PollenReservationStatus status, String comment, String confirmedBy) {
        static PollenReservationResponse from(PollenReservation reservation) {
            return new PollenReservationResponse(reservation.id(), reservation.seasonPlanId(), reservation.requestedAmount(),
                    reservation.confirmedAmount(), reservation.status(), reservation.comment(), reservation.confirmedBy());
        }
    }

    public record FairyAssignmentResponse(UUID id, UUID magicTaskId, String fairyId, String role, String talent, AssignmentStatus status) {
        static FairyAssignmentResponse from(FairyAssignment assignment) {
            return new FairyAssignmentResponse(assignment.id(), assignment.magicTaskId(), assignment.fairyId(), assignment.role(), assignment.talent(), assignment.status());
        }
    }

    public record DecisionRequest(String comment) {
    }

    public record ReservationRequest(int requestedAmount) {
    }

    public record ReservationDecisionRequest(int confirmedAmount, String comment) {
    }

    public record AssignmentRequest(String fairyId, String role, String talent, AssignmentStatus status) {
    }

    public record ReadinessResponse(boolean ready, Map<String, List<String>> errors) {
    }
}
