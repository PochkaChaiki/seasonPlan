package com.seasonplan.lifecycle.api;

import com.seasonplan.lifecycle.authorization.LifecycleAccessPolicy;
import com.seasonplan.lifecycle.authorization.LifecyclePermission;
import com.seasonplan.security.identity.CurrentActor;
import com.seasonplan.lifecycle.domain.FairyAssignment;
import com.seasonplan.lifecycle.domain.MagicTask;
import com.seasonplan.lifecycle.domain.PollenReservation;
import com.seasonplan.lifecycle.domain.SeasonPlan;
import com.seasonplan.lifecycle.service.MagicTaskService;
import com.seasonplan.lifecycle.service.PollenService;
import com.seasonplan.lifecycle.service.SeasonPlanService;
import com.seasonplan.security.identity.CurrentActorProvider;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.security.access.prepost.PreAuthorize;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@PreAuthorize("isAuthenticated()")
public class LifecycleController {
    private final SeasonPlanService seasonPlanService;
    private final PollenService pollenService;
    private final MagicTaskService magicTaskService;
    private final CurrentActorProvider currentActorProvider;

    public LifecycleController(
            SeasonPlanService seasonPlanService,
            PollenService pollenService,
            MagicTaskService magicTaskService,
            CurrentActorProvider currentActorProvider
    ) {
        this.seasonPlanService = seasonPlanService;
        this.pollenService = pollenService;
        this.magicTaskService = magicTaskService;
        this.currentActorProvider = currentActorProvider;
    }

    @PostMapping("/season-plans")
    public SeasonPlanResponse createPlan(@RequestBody SeasonPlanRequest request) {
        CurrentActor actor = actor();
        return toResponse(seasonPlanService.createDraft(request.toDraft(), LifecycleAccessPolicy.roleFor(actor, LifecyclePermission.CREATE_DRAFT), actor.userId().toString()));
    }

    @PatchMapping("/season-plans/{id}")
    public SeasonPlanResponse updatePlan(
            @PathVariable UUID id,
            @RequestBody SeasonPlanRequest request
    ) {
        CurrentActor actor = actor();
        return toResponse(seasonPlanService.updateDraft(id, request.toDraft(), LifecycleAccessPolicy.roleFor(actor, LifecyclePermission.UPDATE_DRAFT), actor.userId().toString()));
    }

    @GetMapping("/season-plans")
    public List<SeasonPlanResponse> listPlans() {
        return seasonPlanService.listPlans().stream().map(this::toResponse).toList();
    }

    @PostMapping("/season-plans/{id}/submit")
    public SeasonPlanResponse submit(@PathVariable UUID id) {
        CurrentActor actor = actor();
        return toResponse(seasonPlanService.submitForApproval(id, LifecycleAccessPolicy.roleFor(actor, LifecyclePermission.SUBMIT_PLAN), actor.userId().toString()));
    }

    @PostMapping("/season-plans/{id}/verify")
    public SeasonPlanResponse verify(@PathVariable UUID id) {
        CurrentActor actor = actor();
        return toResponse(seasonPlanService.verify(id, LifecycleAccessPolicy.roleFor(actor, LifecyclePermission.VERIFY_PLAN), actor.userId().toString()));
    }

    @PostMapping("/season-plans/{id}/approve")
    public SeasonPlanResponse approve(
            @PathVariable UUID id,
            @RequestBody DecisionRequest request
    ) {
        CurrentActor actor = actor();
        return toResponse(seasonPlanService.approve(id, request.comment(), request.conditions(), LifecycleAccessPolicy.roleFor(actor, LifecyclePermission.APPROVE_PLAN), actor.userId().toString()));
    }

    @PostMapping("/season-plans/{id}/return")
    public SeasonPlanResponse returnForRevision(
            @PathVariable UUID id,
            @RequestBody DecisionRequest request
    ) {
        CurrentActor actor = actor();
        return toResponse(seasonPlanService.returnForRevision(id, request.comment(), LifecycleAccessPolicy.roleFor(actor, LifecyclePermission.RETURN_PLAN), actor.userId().toString()));
    }

    @GetMapping("/season-plans/{id}")
    public SeasonPlanResponse getPlan(@PathVariable UUID id) {
        return toResponse(seasonPlanService.get(id));
    }

    @GetMapping("/season-plans/{id}/review-history")
    public List<com.seasonplan.lifecycle.domain.AuditLogEntry> reviewHistory(@PathVariable UUID id) {
        return seasonPlanService.reviewHistory(id, actor());
    }

    @GetMapping("/season-plans/{id}/readiness")
    public ReadinessResponse readiness(@PathVariable UUID id) {
        Map<String, List<String>> errors = seasonPlanService.calculateChecklist(id);
        return toReadinessResponse(errors);
    }

    @PostMapping("/season-plans/{id}/readiness-checklist/{key}/complete")
    public SeasonPlanResponse completeReadinessChecklistItem(
            @PathVariable UUID id,
            @PathVariable String key
    ) {
        CurrentActor actor = actor();
        seasonPlanService.completeItem(id, key, LifecycleAccessPolicy.roleFor(actor, LifecyclePermission.COMPLETE_CHECKLIST), actor.userId().toString());
        return toResponse(seasonPlanService.get(id));
    }

    @PostMapping("/season-plans/{id}/pollen-reservations")
    public PollenReservationResponse requestReservation(
            @PathVariable UUID id,
            @RequestBody ReservationRequest request
    ) {
        CurrentActor actor = actor();
        return toResponse(pollenService.requestReservation(id,
                request.requestId() == null ? UUID.randomUUID() : request.requestId(),
                request.requestedAmount(), LifecycleAccessPolicy.roleFor(actor, LifecyclePermission.REQUEST_POLLEN), actor.userId().toString()));
    }

    @PostMapping("/pollen-reservations/{id}/confirm")
    public PollenReservationResponse confirmReservation(
            @PathVariable UUID id,
            @RequestBody ReservationDecisionRequest request
    ) {
        CurrentActor actor = actor();
        return toResponse(pollenService.confirmReservation(id, request.confirmedAmount(), request.comment(), LifecycleAccessPolicy.roleFor(actor, LifecyclePermission.CONFIRM_POLLEN), actor.userId().toString()));
    }

    @PostMapping("/pollen-reservations/{id}/reject")
    public PollenReservationResponse rejectReservation(
            @PathVariable UUID id,
            @RequestBody DecisionRequest request
    ) {
        CurrentActor actor = actor();
        return toResponse(pollenService.rejectReservation(id, request.comment(), LifecycleAccessPolicy.roleFor(actor, LifecyclePermission.REJECT_POLLEN), actor.userId().toString()));
    }

    @PostMapping("/season-plans/{id}/tasks")
    public MagicTaskResponse createTask(
            @PathVariable UUID id,
            @RequestBody MagicTaskRequest request
    ) {
        CurrentActor actor = actor();
        return toResponse(magicTaskService.createTask(id, request.toDraft(), LifecycleAccessPolicy.roleFor(actor, LifecyclePermission.CREATE_TASK), actor.userId().toString()));
    }

    @GetMapping("/season-plans/{id}/tasks")
    public List<MagicTaskResponse> listTasks(@PathVariable UUID id) {
        return magicTaskService.listTasksBySeasonPlan(id).stream().map(this::toResponse).toList();
    }

    @PatchMapping("/tasks/{id}")
    public MagicTaskResponse updateTask(
            @PathVariable UUID id,
            @RequestBody MagicTaskRequest request
    ) {
        CurrentActor actor = actor();
        return toResponse(magicTaskService.updateTask(id, request.toDraft(), LifecycleAccessPolicy.roleFor(actor, LifecyclePermission.UPDATE_TASK), actor.userId().toString()));
    }

    @PostMapping("/tasks/{id}/assignments")
    public FairyAssignmentResponse assignFairy(
            @PathVariable UUID id,
            @RequestBody AssignmentRequest request
    ) {
        CurrentActor actor = actor();
        return toResponse(magicTaskService.assignFairy(id, request.fairyId(), request.role(), request.talent(), request.status(), LifecycleAccessPolicy.roleFor(actor, LifecyclePermission.ASSIGN_FAIRY), actor.userId().toString()));
    }

    @GetMapping("/tasks/{id}/assignments")
    public List<FairyAssignmentResponse> listAssignments(@PathVariable UUID id) {
        return magicTaskService.assignmentsForTask(id).stream().map(this::toResponse).toList();
    }

    @PostMapping("/season-plans/{id}/mark-ritual-ready")
    public SeasonPlanResponse markRitualReady(@PathVariable UUID id) {
        CurrentActor actor = actor();
        return toResponse(seasonPlanService.markRitualReady(id, LifecycleAccessPolicy.roleFor(actor, LifecyclePermission.MARK_RITUAL_READY), actor.userId().toString()));
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

    private CurrentActor actor() { return currentActorProvider.currentActor(); }
}
