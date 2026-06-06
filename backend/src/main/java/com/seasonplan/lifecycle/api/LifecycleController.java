package com.seasonplan.lifecycle.api;

import com.seasonplan.lifecycle.domain.ActorRole;
import com.seasonplan.lifecycle.domain.AssignmentStatus;
import com.seasonplan.lifecycle.domain.FairyAssignment;
import com.seasonplan.lifecycle.domain.MagicTask;
import com.seasonplan.lifecycle.domain.PollenReservation;
import com.seasonplan.lifecycle.domain.SeasonPlan;
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
    public SeasonPlan createPlan(@RequestBody SeasonPlan draft, @RequestHeader("X-Actor-Role") ActorRole role, @RequestHeader("X-Actor-Id") String actorId) {
        return seasonPlanService.createDraft(draft, role, actorId);
    }

    @PatchMapping("/season-plans/{id}")
    public SeasonPlan updatePlan(@PathVariable UUID id, @RequestBody SeasonPlan draft, @RequestHeader("X-Actor-Role") ActorRole role, @RequestHeader("X-Actor-Id") String actorId) {
        return seasonPlanService.updateDraft(id, draft, role, actorId);
    }

    @PostMapping("/season-plans/{id}/submit")
    public SeasonPlan submit(@PathVariable UUID id, @RequestHeader("X-Actor-Role") ActorRole role, @RequestHeader("X-Actor-Id") String actorId) {
        return seasonPlanService.submitForApproval(id, role, actorId);
    }

    @PostMapping("/season-plans/{id}/approve")
    public SeasonPlan approve(@PathVariable UUID id, @RequestBody DecisionRequest request, @RequestHeader("X-Actor-Role") ActorRole role, @RequestHeader("X-Actor-Id") String actorId) {
        return seasonPlanService.approve(id, request.comment(), role, actorId);
    }

    @PostMapping("/season-plans/{id}/return")
    public SeasonPlan returnForRevision(@PathVariable UUID id, @RequestBody DecisionRequest request, @RequestHeader("X-Actor-Role") ActorRole role, @RequestHeader("X-Actor-Id") String actorId) {
        return seasonPlanService.returnForRevision(id, request.comment(), role, actorId);
    }

    @GetMapping("/season-plans/{id}")
    public SeasonPlan getPlan(@PathVariable UUID id) {
        return seasonPlanService.get(id);
    }

    @GetMapping("/season-plans/{id}/readiness")
    public ReadinessResponse readiness(@PathVariable UUID id) {
        Map<String, List<String>> errors = readinessChecklistService.calculateChecklist(id);
        return new ReadinessResponse(errors.isEmpty(), errors);
    }

    @PostMapping("/season-plans/{id}/pollen-reservations")
    public PollenReservation requestReservation(@PathVariable UUID id, @RequestBody ReservationRequest request, @RequestHeader("X-Actor-Role") ActorRole role, @RequestHeader("X-Actor-Id") String actorId) {
        return pollenReservationService.requestReservation(id, request.requestedAmount(), role, actorId);
    }

    @PostMapping("/pollen-reservations/{id}/confirm")
    public PollenReservation confirmReservation(@PathVariable UUID id, @RequestBody ReservationDecisionRequest request, @RequestHeader("X-Actor-Role") ActorRole role, @RequestHeader("X-Actor-Id") String actorId) {
        return pollenReservationService.confirmReservation(id, request.confirmedAmount(), request.comment(), role, actorId);
    }

    @PostMapping("/pollen-reservations/{id}/reject")
    public PollenReservation rejectReservation(@PathVariable UUID id, @RequestBody DecisionRequest request, @RequestHeader("X-Actor-Role") ActorRole role, @RequestHeader("X-Actor-Id") String actorId) {
        return pollenReservationService.rejectReservation(id, request.comment(), role, actorId);
    }

    @PostMapping("/season-plans/{id}/tasks")
    public MagicTask createTask(@PathVariable UUID id, @RequestBody MagicTask draft, @RequestHeader("X-Actor-Role") ActorRole role, @RequestHeader("X-Actor-Id") String actorId) {
        return magicTaskService.createTask(id, draft, role, actorId);
    }

    @PatchMapping("/tasks/{id}")
    public MagicTask updateTask(@PathVariable UUID id, @RequestBody MagicTask draft, @RequestHeader("X-Actor-Role") ActorRole role, @RequestHeader("X-Actor-Id") String actorId) {
        return magicTaskService.updateTask(id, draft, role, actorId);
    }

    @PostMapping("/tasks/{id}/assignments")
    public FairyAssignment assignFairy(@PathVariable UUID id, @RequestBody AssignmentRequest request, @RequestHeader("X-Actor-Role") ActorRole role, @RequestHeader("X-Actor-Id") String actorId) {
        return fairyAssignmentService.assignFairy(id, request.fairyId(), request.role(), request.talent(), request.status(), role, actorId);
    }

    @PostMapping("/season-plans/{id}/mark-ritual-ready")
    public SeasonPlan markRitualReady(@PathVariable UUID id, @RequestHeader("X-Actor-Role") ActorRole role, @RequestHeader("X-Actor-Id") String actorId) {
        return seasonPlanService.markRitualReady(id, role, actorId);
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
