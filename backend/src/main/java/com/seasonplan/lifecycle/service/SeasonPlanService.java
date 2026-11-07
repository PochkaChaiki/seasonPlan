package com.seasonplan.lifecycle.service;

import com.seasonplan.lifecycle.application.approval.ApprovalService;
import com.seasonplan.lifecycle.application.port.*;
import com.seasonplan.lifecycle.application.shared.AuditLogService;
import com.seasonplan.lifecycle.application.planning.PlanningService;
import com.seasonplan.lifecycle.application.preparation.PreparationService;
import com.seasonplan.lifecycle.application.preparation.ReadinessChecklistService;
import com.seasonplan.lifecycle.domain.ActorRole;
import com.seasonplan.lifecycle.domain.SeasonPlan;

import java.time.Clock;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.UUID;

/** Compatibility entry point for the existing REST adapter; business logic lives in named use cases. */
public class SeasonPlanService {
    private final PlanningService planning;
    private final ApprovalService approval;
    private final PreparationService preparation;

    public SeasonPlanService(SeasonPlanRepository plans, PollenReservationRepository pollen,
                             AuditLogService audit, PlannerNotifications notifications,
                             NaturalCalendarService calendar, ReadinessChecklistService readiness, Clock clock) {
        planning = new PlanningService(plans, audit, calendar, clock);
        approval = new ApprovalService(plans, pollen, audit, notifications, clock);
        preparation = new PreparationService(plans, readiness, audit, clock);
    }

    public SeasonPlan createDraft(SeasonPlan draft, ActorRole role, String actorId) {
        return planning.createDraft(draft, role, actorId);
    }

    public SeasonPlan updateDraft(UUID id, SeasonPlan draft, ActorRole role, String actorId) {
        return planning.updateDraft(id, draft, role, actorId);
    }

    public LinkedHashMap<String, List<String>> validatePlan(SeasonPlan plan) {
        return planning.validatePlan(plan);
    }

    public SeasonPlan submitForApproval(UUID id, ActorRole role, String actorId) {
        return planning.submitForApproval(id, role, actorId);
    }

    public SeasonPlan approve(UUID id, String comment, ActorRole role, String actorId) {
        return approval.approve(id, comment, role, actorId);
    }

    public SeasonPlan returnForRevision(UUID id, String comment, ActorRole role, String actorId) {
        return approval.returnForRevision(id, comment, role, actorId);
    }

    public SeasonPlan markRitualReady(UUID id, ActorRole role, String actorId) {
        return preparation.markRitualReady(id, role, actorId);
    }

    public SeasonPlan get(UUID id) { return planning.get(id); }

    public List<SeasonPlan> listPlans() { return planning.listPlans(); }
}
