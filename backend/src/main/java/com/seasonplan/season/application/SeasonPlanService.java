package com.seasonplan.season.application;

import com.seasonplan.season.api.ResourceEvidenceSource;
import com.seasonplan.season.api.SeasonReadiness;
import com.seasonplan.reference.api.MetricDefinition;
import com.seasonplan.reference.domain.ExecutionRuleSnapshot;
import com.seasonplan.season.api.CommandTransaction;
import com.seasonplan.season.domain.ApprovalCondition;
import com.seasonplan.season.domain.ChecklistItem;
import com.seasonplan.season.domain.SeasonPlan;
import com.seasonplan.season.domain.SeasonPlanStatus;
import com.seasonplan.shared.domain.DomainValidationException;
import com.seasonplan.shared.domain.TimeWindow;
import com.seasonplan.shared.domain.ValidationSupport;

import com.seasonplan.audit.api.AuditAction;
import com.seasonplan.audit.api.AuditTrail;
import com.seasonplan.audit.api.AuditLogEntry;
import com.seasonplan.identity.api.LifecycleAccessPolicy;
import com.seasonplan.identity.api.LifecyclePermission;

import com.seasonplan.season.application.port.SeasonPlanRepository;
import com.seasonplan.notification.api.NotificationOutbox;
import com.seasonplan.reference.api.NaturalCalendarValidation;
import com.seasonplan.reference.api.CalendarRequest;
import com.seasonplan.identity.api.ActorRole;
import com.seasonplan.identity.api.CurrentActor;



import java.time.Clock;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Plan drafting, review and preparation. Domain objects retain lifecycle transition rules. */
public class SeasonPlanService {
    private final SeasonPlanRepository plans;
    private final ResourceEvidenceSource resources;
    private final AuditTrail audit;
    private final CommandTransaction transactions;
    private final NotificationOutbox outbox;
    private final NaturalCalendarValidation calendar;
    private final Clock clock;
    private final java.util.function.Supplier<ExecutionRuleSnapshot> executionRules;
    private final SeasonReadiness readiness;

    public SeasonPlanService(SeasonPlanRepository plans, ResourceEvidenceSource resources,
                             AuditTrail audit, CommandTransaction transactions, NotificationOutbox outbox,
                             NaturalCalendarValidation calendar, Clock clock, java.util.function.Supplier<ExecutionRuleSnapshot> executionRules, SeasonReadiness readiness) {
        this.plans = plans;
        this.resources = resources;
        this.audit = audit;
        this.transactions = transactions;
        this.outbox = outbox;
        this.calendar = calendar;
        this.clock = clock;
        this.executionRules = java.util.Objects.requireNonNull(executionRules);
        this.readiness = java.util.Objects.requireNonNull(readiness);
    }

    public SeasonPlan createDraft(SeasonPlan draft, ActorRole role, String actorId) {
        LifecycleAccessPolicy.require(role, LifecyclePermission.CREATE_DRAFT);
        validatePlan(draft);
        ExecutionRuleSnapshot rules;
        try { rules = executionRules.get(); }
        catch (IllegalStateException ex) { ServiceSupport.reject("executionRules", "Complete versioned execution rules must be configured before creating a plan"); throw ex; }
        com.seasonplan.season.domain.SeasonPlanPolicy.requireCatalogSeason(draft, rules, false);
        SeasonPlan created = SeasonPlan.createDraft(UUID.randomUUID(), draft.seasonType(), draft.targetTerritories(),
                draft.timeWindows(), draft.naturalPhenomenonParameters(), draft.targetClimateMetrics(),
                com.seasonplan.season.domain.SeasonPlanPolicy.initialReadinessChecklist(),
                actorId, clock.instant());
        created.pinExecutionRules(rules);
        return transactions.create(() -> {
            plans.save(created);
            audit.append(created.id(), AuditAction.SEASON_PLAN_CREATED, actorId, null);
            return created;
        });
    }

    public SeasonPlan updateDraft(UUID id, SeasonPlan draft, ActorRole role, String actorId) {
        LifecycleAccessPolicy.require(role, LifecyclePermission.UPDATE_DRAFT);
        return transactions.execute(id, () -> {
            SeasonPlan existing = ServiceSupport.copy(get(id));
            LifecycleAccessPolicy.requireDraftOwner(role, actorId, existing.createdBy());
            validatePlan(draft);
            com.seasonplan.season.domain.SeasonPlanPolicy.requireCatalogSeason(draft, existing.executionRules(), true);
            existing.updateDraftFieldsFrom(draft, actorId, clock.instant());
            audit.append(id, AuditAction.SEASON_PLAN_UPDATED, actorId, null);
            return plans.save(existing);
        });
    }

    public LinkedHashMap<String, List<String>> validatePlan(SeasonPlan plan) {
        return com.seasonplan.season.domain.SeasonPlanPolicy.validate(plan, calendar.validate(new CalendarRequest(plan.seasonType(), plan.targetTerritories(), plan.timeWindows(),
                plan.naturalPhenomenonParameters(), plan.targetClimateMetrics())));
    }

    public void validateForReview(SeasonPlan plan) {
        validatePlan(plan);
        com.seasonplan.season.domain.SeasonPlanPolicy.requireNoWindowConflict(plan, plans.findAll());
    }

    public SeasonPlan submitForApproval(UUID id, ActorRole role, String actorId) {
        LifecycleAccessPolicy.require(role, LifecyclePermission.SUBMIT_PLAN);
        return transactions.execute(id, () -> {
            SeasonPlan plan = get(id);
            LifecycleAccessPolicy.requireDraftOwner(role, actorId, plan.createdBy());
            plans.lockSeasonWindows();
            validateForReview(plan);
            plan.submit(actorId, clock.instant());
            audit.append(id, AuditAction.SEASON_PLAN_SUBMITTED, actorId, null);
            return plans.save(plan);
        });
    }

    public SeasonPlan verify(UUID id, ActorRole role, String actorId) {
        LifecycleAccessPolicy.require(role, LifecyclePermission.VERIFY_PLAN);
        return transactions.execute(id, () -> {
            SeasonPlan plan = get(id);
            com.seasonplan.season.domain.SeasonPlanPolicy.requireVerifiable(plan);
            plans.lockSeasonWindows();
            validateForReview(plan);
            plan.verify(actorId, clock.instant());
            return commitReview(plan, AuditAction.SEASON_PLAN_VERIFIED, actorId, null);
        });
    }

    public SeasonPlan approve(UUID id, String comment, ActorRole role, String actorId) {
        return approve(id, comment, List.of(), role, actorId);
    }

    public SeasonPlan approve(UUID id, String comment, List<ApprovalCondition> conditions, ActorRole role, String actorId) {
        LifecycleAccessPolicy.require(role, LifecyclePermission.APPROVE_PLAN);
        return transactions.execute(id, () -> {
            SeasonPlan plan = get(id);
            com.seasonplan.season.domain.SeasonPlanPolicy.requireApprovable(plan);
            plans.lockSeasonWindows();
            var latest = com.seasonplan.season.domain.SeasonPlanPolicy.requireReservation(id, resources.latestFor(id));
            var decision = com.seasonplan.season.domain.SeasonPlanPolicy.reviewResources(id, plan.materialRevision(), latest, resources.confirmedFor(id));
            if (decision.returnForRevision()) {
                plan.returnForRevision(decision.comment(), actorId, clock.instant());
                return commitReview(plan, AuditAction.SEASON_PLAN_RETURNED, actorId, decision.comment());
            }
            validateForReview(plan);
            plan.approve(comment, conditions, actorId, clock.instant());
            return commitReview(plan, AuditAction.SEASON_PLAN_APPROVED, actorId, comment);
        });
    }

    public SeasonPlan returnForRevision(UUID id, String comment, ActorRole role, String actorId) {
        LifecycleAccessPolicy.require(role, LifecyclePermission.RETURN_PLAN);
        return transactions.execute(id, () -> {
            SeasonPlan plan = get(id);
            plan.returnForRevision(comment, actorId, clock.instant());
            return commitReview(plan, AuditAction.SEASON_PLAN_RETURNED, actorId, comment);
        });
    }

    public List<AuditLogEntry> reviewHistory(UUID id, CurrentActor actor) {
        SeasonPlan plan = get(id);
        LifecycleAccessPolicy.requireReviewHistory(actor, plan.createdBy());
        return audit.entriesFor(id);
    }

    public Map<String, List<String>> calculateChecklist(UUID seasonPlanId) {
        return com.seasonplan.season.domain.SeasonPlanPolicy.readinessErrors(readiness.blockers(seasonPlanId));
    }

    public SeasonPlan completeItem(UUID seasonPlanId, String key, ActorRole role, String actorId) {
        LifecycleAccessPolicy.require(role, LifecyclePermission.COMPLETE_CHECKLIST);
        return transactions.execute(seasonPlanId, () -> {
            SeasonPlan plan = get(seasonPlanId);
            plan.completeReadinessChecklistItem(key, actorId, clock.instant());
            return plans.save(plan);
        });
    }

    public SeasonPlan markRitualReady(UUID id, ActorRole role, String actorId) {
        LifecycleAccessPolicy.require(role, LifecyclePermission.MARK_RITUAL_READY);
        return transactions.execute(id, () -> {
            SeasonPlan plan = get(id);
            com.seasonplan.season.domain.SeasonPlanPolicy.requireReady(calculateChecklist(id));
            plan.markRitualReady(actorId, clock.instant());
            audit.append(id, AuditAction.SEASON_MARKED_RITUAL_READY, actorId, null);
            return plans.save(plan);
        });
    }

    public SeasonPlan get(UUID id) {
        return ServiceSupport.plan(plans, id);
    }

    public List<SeasonPlan> listPlans() {
        return plans.findAll();
    }

    /** Review decisions retain their atomic plan/audit/outbox transaction. */
    private SeasonPlan commitReview(SeasonPlan plan, AuditAction action, String actorId, String comment) {
        SeasonPlan saved = plans.save(plan);
        AuditLogEntry entry = audit.append(plan.id(), action, actorId, comment);
        outbox.enqueue(new NotificationOutbox.Event(entry.id().toString(), plan.id(), action.name(),
                com.seasonplan.season.domain.SeasonPlanPolicy.reviewRecipients(plan), Set.of(NotificationOutbox.Channel.IN_APP, NotificationOutbox.Channel.EMAIL),
                comment == null ? "" : comment, clock.instant()));
        return saved;
    }

}
