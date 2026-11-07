package com.seasonplan.lifecycle.application.planning;

import com.seasonplan.lifecycle.domain.*;
import com.seasonplan.lifecycle.application.port.*;
import com.seasonplan.lifecycle.application.shared.*;

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
    private final Clock clock;

    public PlanningService(SeasonPlanRepository plans, AuditLogService audit,
                           NaturalCalendarService calendar, Clock clock) {
        this.plans = plans;
        this.audit = audit;
        this.calendar = calendar;
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
        SeasonPlan existing = get(id);
        validatePlan(draft);
        existing.updateDraftFieldsFrom(draft, actorId, clock.instant());
        audit.append(id, AuditAction.SEASON_PLAN_UPDATED, actorId, null);
        return plans.save(existing);
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

    public SeasonPlan submitForApproval(UUID id, ActorRole role, String actorId) {
        ValidationSupport.requireRole(role, ActorRole.FAIRY_PLANNER);
        SeasonPlan plan = get(id);
        validatePlan(plan);
        plan.submit(actorId, clock.instant());
        audit.append(id, AuditAction.SEASON_PLAN_SUBMITTED, actorId, null);
        return plans.save(plan);
    }

    public SeasonPlan get(UUID id) {
        return plans.findById(id).orElseThrow(() -> new NotFoundException("season plan not found: " + id));
    }

    public List<SeasonPlan> listPlans() {
        return plans.findAll();
    }
}
