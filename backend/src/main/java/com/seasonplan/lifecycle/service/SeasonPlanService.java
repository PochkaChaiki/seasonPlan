package com.seasonplan.lifecycle.service;

import com.seasonplan.lifecycle.application.approval.ApprovalService;
import com.seasonplan.lifecycle.application.port.*;
import com.seasonplan.lifecycle.application.pollen.PollenService;
import com.seasonplan.lifecycle.application.planning.PlanningService;
import com.seasonplan.lifecycle.application.preparation.PreparationService;
import com.seasonplan.lifecycle.application.preparation.ReadinessChecklistService;
import com.seasonplan.lifecycle.domain.ActorRole;
import com.seasonplan.lifecycle.domain.ApprovalCondition;
import com.seasonplan.lifecycle.domain.CurrentActor;
import com.seasonplan.lifecycle.domain.AuditLogEntry;
import com.seasonplan.lifecycle.application.shared.AccessDeniedForRoleException;
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
    private final AuditLogService audit;

    public SeasonPlanService(SeasonPlanRepository plans, PollenService pollen,
                             AuditLogService audit, CommandTransaction transactions, NotificationOutbox outbox,
                             NaturalCalendarService calendar, ReadinessChecklistService readiness, Clock clock) {
        this.audit = audit;
        planning = new PlanningService(plans, audit, calendar, transactions, clock);
        approval = new ApprovalService(plans, pollen, audit, transactions, outbox, planning::validateForReview, clock);
        preparation = new PreparationService(plans, readiness, audit, transactions, clock);
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

    public SeasonPlan verify(UUID id, ActorRole role, String actorId) {
        return approval.verify(id, role, actorId);
    }

    public SeasonPlan approve(UUID id, String comment, ActorRole role, String actorId) {
        return approval.approve(id, comment, role, actorId);
    }

    public SeasonPlan approve(UUID id, String comment, List<ApprovalCondition> conditions, ActorRole role, String actorId) {
        return approval.approve(id, comment, conditions, role, actorId);
    }

    public List<AuditLogEntry> reviewHistory(UUID id, CurrentActor actor) {
        SeasonPlan plan = planning.get(id);
        if (!actor.roles().contains(ActorRole.CHIEF_GUARDIAN)
                && !(actor.roles().contains(ActorRole.FAIRY_PLANNER) && actor.userId().toString().equals(plan.createdBy()))) {
            throw new AccessDeniedForRoleException(null);
        }
        return audit.entriesFor(id);
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
