package com.seasonplan.lifecycle.api;

import com.seasonplan.lifecycle.domain.ActorRole;
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
    public SeasonPlanResponse createPlan(
            @RequestBody SeasonPlanRequest request,
            @RequestHeader("X-Actor-Role") ActorRole role,
            @RequestHeader("X-Actor-Id") String actorId
    ) {
        return toResponse(seasonPlanService.createDraft(request.toDraft(), role, actorId));
    }

    @PatchMapping("/season-plans/{id}")
    public SeasonPlanResponse updatePlan(
            @PathVariable UUID id,
            @RequestBody SeasonPlanRequest request,
            @RequestHeader("X-Actor-Role") ActorRole role,
            @RequestHeader("X-Actor-Id") String actorId
    ) {
        return toResponse(seasonPlanService.updateDraft(id, request.toDraft(), role, actorId));
    }

    @GetMapping("/season-plans")
    public List<SeasonPlanResponse> listPlans() {
        return seasonPlanService.listPlans().stream().map(this::toResponse).toList();
    }

    @PostMapping("/season-plans/{id}/submit")
    public SeasonPlanResponse submit(
            @PathVariable UUID id,
            @RequestHeader("X-Actor-Role") ActorRole role,
            @RequestHeader("X-Actor-Id") String actorId
    ) {
        return toResponse(seasonPlanService.submitForApproval(id, role, actorId));
    }

    @PostMapping("/season-plans/{id}/approve")
    public SeasonPlanResponse approve(
            @PathVariable UUID id,
            @RequestBody DecisionRequest request,
            @RequestHeader("X-Actor-Role") ActorRole role,
            @RequestHeader("X-Actor-Id") String actorId
    ) {
        return toResponse(seasonPlanService.approve(id, request.comment(), role, actorId));
    }

    @PostMapping("/season-plans/{id}/return")
    public SeasonPlanResponse returnForRevision(
            @PathVariable UUID id,
            @RequestBody DecisionRequest request,
            @RequestHeader("X-Actor-Role") ActorRole role,
            @RequestHeader("X-Actor-Id") String actorId
    ) {
        return toResponse(seasonPlanService.returnForRevision(id, request.comment(), role, actorId));
    }

    @GetMapping("/season-plans/{id}")
    public SeasonPlanResponse getPlan(@PathVariable UUID id) {
        return toResponse(seasonPlanService.get(id));
    }

    @GetMapping("/season-plans/{id}/readiness")
    public ReadinessResponse readiness(@PathVariable UUID id) {
        Map<String, List<String>> errors = readinessChecklistService.calculateChecklist(id);
        return toReadinessResponse(errors);
    }

    @PostMapping("/season-plans/{id}/readiness-checklist/{key}/complete")
    public SeasonPlanResponse completeReadinessChecklistItem(
            @PathVariable UUID id,
            @PathVariable String key,
            @RequestHeader("X-Actor-Role") ActorRole role,
            @RequestHeader("X-Actor-Id") String actorId
    ) {
        readinessChecklistService.completeItem(id, key, role, actorId);
        return toResponse(seasonPlanService.get(id));
    }

    @PostMapping("/season-plans/{id}/pollen-reservations")
    public PollenReservationResponse requestReservation(
            @PathVariable UUID id,
            @RequestBody ReservationRequest request,
            @RequestHeader("X-Actor-Role") ActorRole role,
            @RequestHeader("X-Actor-Id") String actorId
    ) {
        return toResponse(pollenReservationService.requestReservation(id, request.requestedAmount(), role, actorId));
    }

    @PostMapping("/pollen-reservations/{id}/confirm")
    public PollenReservationResponse confirmReservation(
            @PathVariable UUID id,
            @RequestBody ReservationDecisionRequest request,
            @RequestHeader("X-Actor-Role") ActorRole role,
            @RequestHeader("X-Actor-Id") String actorId
    ) {
        return toResponse(pollenReservationService.confirmReservation(id, request.confirmedAmount(), request.comment(), role, actorId));
    }

    @PostMapping("/pollen-reservations/{id}/reject")
    public PollenReservationResponse rejectReservation(
            @PathVariable UUID id,
            @RequestBody DecisionRequest request,
            @RequestHeader("X-Actor-Role") ActorRole role,
            @RequestHeader("X-Actor-Id") String actorId
    ) {
        return toResponse(pollenReservationService.rejectReservation(id, request.comment(), role, actorId));
    }

    @PostMapping("/season-plans/{id}/tasks")
    public MagicTaskResponse createTask(
            @PathVariable UUID id,
            @RequestBody MagicTaskRequest request,
            @RequestHeader("X-Actor-Role") ActorRole role,
            @RequestHeader("X-Actor-Id") String actorId
    ) {
        return toResponse(magicTaskService.createTask(id, request.toDraft(), role, actorId));
    }

    @GetMapping("/season-plans/{id}/tasks")
    public List<MagicTaskResponse> listTasks(@PathVariable UUID id) {
        return magicTaskService.listTasksBySeasonPlan(id).stream().map(this::toResponse).toList();
    }

    @PatchMapping("/tasks/{id}")
    public MagicTaskResponse updateTask(
            @PathVariable UUID id,
            @RequestBody MagicTaskRequest request,
            @RequestHeader("X-Actor-Role") ActorRole role,
            @RequestHeader("X-Actor-Id") String actorId
    ) {
        return toResponse(magicTaskService.updateTask(id, request.toDraft(), role, actorId));
    }

    @PostMapping("/tasks/{id}/assignments")
    public FairyAssignmentResponse assignFairy(
            @PathVariable UUID id,
            @RequestBody AssignmentRequest request,
            @RequestHeader("X-Actor-Role") ActorRole role,
            @RequestHeader("X-Actor-Id") String actorId
    ) {
        return toResponse(fairyAssignmentService.assignFairy(id, request.fairyId(), request.role(), request.talent(), request.status(), role, actorId));
    }

    @GetMapping("/tasks/{id}/assignments")
    public List<FairyAssignmentResponse> listAssignments(@PathVariable UUID id) {
        return fairyAssignmentService.assignmentsForTask(id).stream().map(this::toResponse).toList();
    }

    @PostMapping("/season-plans/{id}/mark-ritual-ready")
    public SeasonPlanResponse markRitualReady(
            @PathVariable UUID id,
            @RequestHeader("X-Actor-Role") ActorRole role,
            @RequestHeader("X-Actor-Id") String actorId
    ) {
        return toResponse(seasonPlanService.markRitualReady(id, role, actorId));
    }

    private SeasonPlanResponse toResponse(SeasonPlan plan) {
        return SeasonPlanResponse.from(plan);
    }

    private PollenReservationResponse toResponse(PollenReservation reservation) {
        return PollenReservationResponse.from(reservation);
    }

    private MagicTaskResponse toResponse(MagicTask task) {
        return MagicTaskResponse.from(task);
    }

    private FairyAssignmentResponse toResponse(FairyAssignment assignment) {
        return FairyAssignmentResponse.from(assignment);
    }

    private ReadinessResponse toReadinessResponse(Map<String, List<String>> errors) {
        return new ReadinessResponse(errors.isEmpty(), errors);
    }
}
