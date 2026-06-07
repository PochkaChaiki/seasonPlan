package com.seasonplan.lifecycle.service;

import com.seasonplan.lifecycle.domain.ActorRole;
import com.seasonplan.lifecycle.domain.AuditAction;
import com.seasonplan.lifecycle.domain.ChecklistItem;
import com.seasonplan.lifecycle.domain.PollenReservationStatus;
import com.seasonplan.lifecycle.domain.SeasonPlan;
import com.seasonplan.lifecycle.domain.SeasonPlanStatus;
import com.seasonplan.lifecycle.domain.TimeWindow;

import java.time.Clock;
import java.time.Instant;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.UUID;

public class SeasonPlanService {
    private final SeasonPlanRepository plans;
    private final PollenReservationRepository pollenReservations;
    private final AuditLogService auditLogService;
    private final NotificationService notificationService;
    private final NaturalCalendarService naturalCalendarService;
    private final ReadinessChecklistService readinessChecklistService;
    private final Clock clock;

    public SeasonPlanService(
            SeasonPlanRepository plans,
            PollenReservationRepository pollenReservations,
            AuditLogService auditLogService,
            NotificationService notificationService,
            NaturalCalendarService naturalCalendarService,
            ReadinessChecklistService readinessChecklistService,
            Clock clock
    ) {
        this.plans = plans;
        this.pollenReservations = pollenReservations;
        this.auditLogService = auditLogService;
        this.notificationService = notificationService;
        this.naturalCalendarService = naturalCalendarService;
        this.readinessChecklistService = readinessChecklistService;
        this.clock = clock;
    }

    public SeasonPlan createDraft(SeasonPlan draft, ActorRole actorRole, String actorId) {
        ValidationSupport.requireRole(actorRole, ActorRole.FAIRY_PLANNER);
        validatePlan(draft);
        Instant now = clock.instant();
        draft.setId(UUID.randomUUID());
        draft.setStatus(SeasonPlanStatus.DRAFT);
        draft.setCreatedBy(actorId);
        draft.setUpdatedBy(actorId);
        draft.setCreatedAt(now);
        draft.setUpdatedAt(now);
        draft.setReadinessChecklist(List.of(
                new ChecklistItem("calendar-reviewed", false),
                new ChecklistItem("roles-reviewed", false)
        ));
        plans.save(draft);
        auditLogService.append(draft.id(), AuditAction.SEASON_PLAN_CREATED, actorId, null);
        return draft;
    }

    public SeasonPlan updateDraft(UUID id, SeasonPlan draft, ActorRole actorRole, String actorId) {
        ValidationSupport.requireRole(actorRole, ActorRole.FAIRY_PLANNER);
        SeasonPlan existing = get(id);
        if (existing.status() != SeasonPlanStatus.DRAFT && existing.status() != SeasonPlanStatus.VERIFIED) {
            throw new DomainValidationException(new LinkedHashMap<>() {{
                put("status", List.of("only DRAFT or VERIFIED plans can be updated"));
            }});
        }
        validatePlan(draft);
        existing.updateDraftFieldsFrom(draft, actorId, clock.instant());
        auditLogService.append(existing.id(), AuditAction.SEASON_PLAN_UPDATED, actorId, null);
        return plans.save(existing);
    }

    public LinkedHashMap<String, List<String>> validatePlan(SeasonPlan plan) {
        LinkedHashMap<String, List<String>> errors = collectPlanValidationErrors(plan);
        ValidationSupport.throwIfAny(errors);
        return errors;
    }

    public SeasonPlan submitForApproval(UUID id, ActorRole actorRole, String actorId) {
        ValidationSupport.requireRole(actorRole, ActorRole.FAIRY_PLANNER);
        SeasonPlan plan = get(id);
        if (plan.status() != SeasonPlanStatus.DRAFT) {
            throw new DomainValidationException(new LinkedHashMap<>() {{
                put("status", List.of("only DRAFT plans can be submitted"));
            }});
        }
        validatePlan(plan);
        plan.setStatus(SeasonPlanStatus.SUBMITTED);
        plan.setUpdatedBy(actorId);
        plan.setUpdatedAt(clock.instant());
        auditLogService.append(plan.id(), AuditAction.SEASON_PLAN_SUBMITTED, actorId, null);
        return plans.save(plan);
    }

    public SeasonPlan approve(UUID id, String comment, ActorRole actorRole, String actorId) {
        ValidationSupport.requireRole(actorRole, ActorRole.CHIEF_GUARDIAN);
        SeasonPlan plan = requireSubmitted(id);
        pollenReservations.findLatestBySeasonPlanId(id)
                .filter(reservation -> reservation.status() == PollenReservationStatus.REJECTED)
                .ifPresent(reservation -> {
                    throw new DomainValidationException(new LinkedHashMap<>() {{
                        put("pollenReservation", List.of("rejected pollen reservation blocks approval"));
                    }});
                });
        plan.setStatus(SeasonPlanStatus.APPROVED);
        plan.setApprovalComment(comment);
        plan.setUpdatedBy(actorId);
        plan.setUpdatedAt(clock.instant());
        auditLogService.append(id, AuditAction.SEASON_PLAN_APPROVED, actorId, comment);
        notificationService.notifyPlannerApprovalResult(plan.createdBy(), "APPROVED", comment);
        return plans.save(plan);
    }

    public SeasonPlan returnForRevision(UUID id, String comment, ActorRole actorRole, String actorId) {
        ValidationSupport.requireRole(actorRole, ActorRole.CHIEF_GUARDIAN);
        SeasonPlan plan = requireSubmitted(id);
        if (comment == null || comment.isBlank()) {
            throw new DomainValidationException(new LinkedHashMap<>() {{
                put("comment", List.of("comment is required when returning a plan"));
            }});
        }
        plan.setStatus(SeasonPlanStatus.DRAFT);
        plan.setApprovalComment(comment);
        plan.setUpdatedBy(actorId);
        plan.setUpdatedAt(clock.instant());
        auditLogService.append(id, AuditAction.SEASON_PLAN_RETURNED, actorId, comment);
        notificationService.notifyPlannerApprovalResult(plan.createdBy(), "RETURNED", comment);
        return plans.save(plan);
    }

    public SeasonPlan markRitualReady(UUID id, ActorRole actorRole, String actorId) {
        ValidationSupport.requireAnyRole(actorRole, ActorRole.TALENT_COORDINATOR, ActorRole.POLLEN_GUARDIAN);
        SeasonPlan plan = get(id);
        if (plan.status() != SeasonPlanStatus.APPROVED) {
            throw new DomainValidationException(new LinkedHashMap<>() {{
                put("status", List.of("only APPROVED plans can be marked RITUAL_READY"));
            }});
        }
        readinessChecklistService.assertReadyForRitual(id);
        plan.setStatus(SeasonPlanStatus.RITUAL_READY);
        plan.setUpdatedBy(actorId);
        plan.setUpdatedAt(clock.instant());
        auditLogService.append(id, AuditAction.SEASON_MARKED_RITUAL_READY, actorId, null);
        return plans.save(plan);
    }

    public SeasonPlan get(UUID id) {
        return plans.findById(id).orElseThrow(() -> new NotFoundException("season plan not found: " + id));
    }

    private SeasonPlan requireSubmitted(UUID id) {
        SeasonPlan plan = get(id);
        if (plan.status() != SeasonPlanStatus.SUBMITTED) {
            throw new DomainValidationException(new LinkedHashMap<>() {{
                put("status", List.of("only SUBMITTED plans can be reviewed"));
            }});
        }
        return plan;
    }

    private LinkedHashMap<String, List<String>> collectPlanValidationErrors(SeasonPlan plan) {
        LinkedHashMap<String, List<String>> errors = new LinkedHashMap<>();
        if (plan.seasonType() == null || plan.seasonType().isBlank()) {
            ValidationSupport.add(errors, "seasonType", "season type is required");
        }
        if (plan.timeWindows().isEmpty()) {
            ValidationSupport.add(errors, "timeWindows", "at least one time window is required");
        }
        if (plan.targetTerritories().isEmpty()) {
            ValidationSupport.add(errors, "targetTerritories", "at least one target territory is required");
        }
        if (plan.naturalPhenomenonParameters().isEmpty()) {
            ValidationSupport.add(errors, "naturalPhenomenonParameters", "natural phenomenon parameters are required");
        }
        if (plan.targetClimateMetrics().isEmpty()) {
            ValidationSupport.add(errors, "targetClimateMetrics", "target climate metrics are required");
        }
        for (TimeWindow window : plan.timeWindows()) {
            if (window.start() == null || window.end() == null || !window.start().isBefore(window.end())) {
                ValidationSupport.add(errors, "timeWindows", "each time window must have start before end");
                break;
            }
        }
        if (hasOverlappingWindows(plan.timeWindows())) {
            ValidationSupport.add(errors, "timeWindows", "time windows must not overlap");
        }
        for (String calendarError : naturalCalendarService.validate(plan)) {
            ValidationSupport.add(errors, "naturalCalendar", calendarError);
        }
        return errors;
    }

    private boolean hasOverlappingWindows(List<TimeWindow> windows) {
        List<TimeWindow> sorted = windows.stream()
                .filter(window -> window.start() != null && window.end() != null)
                .sorted(Comparator.comparing(TimeWindow::start))
                .toList();
        for (int index = 1; index < sorted.size(); index++) {
            if (sorted.get(index - 1).overlaps(sorted.get(index))) {
                return true;
            }
        }
        return false;
    }
}
