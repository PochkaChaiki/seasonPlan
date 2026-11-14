package com.seasonplan.lifecycle.application.planning;

import com.seasonplan.lifecycle.domain.*;
import com.seasonplan.lifecycle.application.port.*;
import com.seasonplan.lifecycle.application.shared.*;
import com.seasonplan.lifecycle.service.AuditLogService;

import java.time.Clock;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.UUID;

/** UC-1: create, edit, validate, submit and read seasonal plans. */
public class PlanningService {
    private final SeasonPlanRepository plans;
    private final AuditLogService audit;
    private final NaturalCalendarService calendar;
    private final CommandTransaction transactions;
    private final Clock clock;

    public PlanningService(SeasonPlanRepository plans, AuditLogService audit,
                           NaturalCalendarService calendar, CommandTransaction transactions, Clock clock) {
        this.plans = plans;
        this.audit = audit;
        this.calendar = calendar;
        this.transactions = transactions;
        this.clock = clock;
    }

    public SeasonPlan createDraft(SeasonPlan draft, ActorRole role, String actorId) {
        ValidationSupport.requireRole(role, ActorRole.FAIRY_PLANNER);
        validatePlan(draft);
        SeasonPlan created = SeasonPlan.createDraft(UUID.randomUUID(), draft.seasonType(), draft.targetTerritories(),
                draft.timeWindows(), draft.naturalPhenomenonParameters(), draft.targetClimateMetrics(),
                List.of(new ChecklistItem("calendar-reviewed", false), new ChecklistItem("roles-reviewed", false)),
                actorId, clock.instant());
        plans.save(created);
        audit.append(created.id(), AuditAction.SEASON_PLAN_CREATED, actorId, null);
        return created;
    }

    public SeasonPlan updateDraft(UUID id, SeasonPlan draft, ActorRole role, String actorId) {
        ValidationSupport.requireRole(role, ActorRole.FAIRY_PLANNER);
        return transactions.execute(id, () -> {
            SeasonPlan existing = get(id);
            validatePlan(draft);
            existing.updateDraftFieldsFrom(draft, actorId, clock.instant());
            audit.append(id, AuditAction.SEASON_PLAN_UPDATED, actorId, null);
            return plans.save(existing);
        });
    }

    public LinkedHashMap<String, List<String>> validatePlan(SeasonPlan plan) {
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
        List<TimeWindow> sorted = plan.timeWindows().stream()
                .filter(window -> window.start() != null && window.end() != null)
                .sorted(Comparator.comparing(TimeWindow::start)).toList();
        for (int index = 1; index < sorted.size(); index++) {
            if (sorted.get(index - 1).overlaps(sorted.get(index))) {
                ValidationSupport.add(errors, "timeWindows", "time windows must not overlap");
                break;
            }
        }
        for (String error : calendar.validate(plan)) {
            ValidationSupport.add(errors, "naturalCalendar", error);
        }
        ValidationSupport.throwIfAny(errors);
        return errors;
    }

    public void validateForReview(SeasonPlan plan) {
        validatePlan(plan);
        var applicable = java.util.EnumSet.of(SeasonPlanStatus.SUBMITTED, SeasonPlanStatus.VERIFIED,
                SeasonPlanStatus.APPROVED, SeasonPlanStatus.RITUAL_READY, SeasonPlanStatus.ACTIVE);
        var errors = new LinkedHashMap<String, List<String>>();
        for (SeasonPlan other : plans.findAll()) {
            if (plan.id().equals(other.id()) || !applicable.contains(other.status())) continue;
            for (String territory : plan.targetTerritories()) {
                if (territory == null) continue;
                String normalized = territory.trim().toLowerCase(java.util.Locale.ROOT);
                boolean shared = other.targetTerritories().stream().filter(java.util.Objects::nonNull)
                        .anyMatch(value -> value.trim().toLowerCase(java.util.Locale.ROOT).equals(normalized));
                if (!shared) continue;
                for (TimeWindow window : plan.timeWindows()) {
                    for (TimeWindow otherWindow : other.timeWindows()) {
                        if (window.overlaps(otherWindow)) {
                            ValidationSupport.add(errors, "targetTerritories", "territory " + normalized
                                    + " conflicts with plan " + other.id() + " window " + otherWindow);
                        }
                    }
                }
            }
        }
        ValidationSupport.throwIfAny(errors);
    }

    public SeasonPlan submitForApproval(UUID id, ActorRole role, String actorId) {
        ValidationSupport.requireRole(role, ActorRole.FAIRY_PLANNER);
        return transactions.execute(id, () -> {
            SeasonPlan plan = get(id);
            validatePlan(plan);
            plan.submit(actorId, clock.instant());
            audit.append(id, AuditAction.SEASON_PLAN_SUBMITTED, actorId, null);
            return plans.save(plan);
        });
    }

    public SeasonPlan get(UUID id) {
        return plans.findById(id).orElseThrow(() -> new NotFoundException("season plan not found: " + id));
    }

    public List<SeasonPlan> listPlans() {
        return plans.findAll();
    }
}
