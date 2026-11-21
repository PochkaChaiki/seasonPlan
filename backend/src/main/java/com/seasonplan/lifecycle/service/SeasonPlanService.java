package com.seasonplan.lifecycle.service;

import com.seasonplan.lifecycle.authorization.LifecycleAccessPolicy;
import com.seasonplan.lifecycle.authorization.LifecyclePermission;

import com.seasonplan.lifecycle.repository.SeasonPlanRepository;
import com.seasonplan.lifecycle.repository.MagicTaskRepository;
import com.seasonplan.lifecycle.repository.FairyAssignmentRepository;
import com.seasonplan.lifecycle.notification.NotificationOutbox;
import com.seasonplan.lifecycle.integration.calendar.NaturalCalendarClient;
import com.seasonplan.security.identity.ActorRole;
import com.seasonplan.security.identity.CurrentActor;



import com.seasonplan.lifecycle.domain.*;

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
    private final PollenService pollen;
    private final MagicTaskRepository magicTasks;
    private final FairyAssignmentRepository fairyAssignments;
    private final AuditLogService audit;
    private final CommandTransaction transactions;
    private final NotificationOutbox outbox;
    private final NaturalCalendarClient calendar;
    private final Clock clock;
    private final java.util.function.Supplier<ExecutionRuleSnapshot> executionRules;
    private final RitualReadinessChecker readiness;

    public SeasonPlanService(SeasonPlanRepository plans, PollenService pollen,
                             MagicTaskRepository magicTasks, FairyAssignmentRepository fairyAssignments,
                             AuditLogService audit, CommandTransaction transactions, NotificationOutbox outbox,
                             NaturalCalendarClient calendar, Clock clock, java.util.function.Supplier<ExecutionRuleSnapshot> executionRules, RitualReadinessChecker readiness) {
        this.plans = plans;
        this.pollen = pollen;
        this.magicTasks = magicTasks;
        this.fairyAssignments = fairyAssignments;
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
        if (!rules.seasons().containsKey(draft.seasonType())) ServiceSupport.reject("seasonType", "season type is not present in the execution catalog");
        SeasonPlan created = SeasonPlan.createDraft(UUID.randomUUID(), draft.seasonType(), draft.targetTerritories(),
                draft.timeWindows(), draft.naturalPhenomenonParameters(), draft.targetClimateMetrics(),
                List.of(new ChecklistItem("calendar-reviewed", false), new ChecklistItem("roles-reviewed", false)),
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
            SeasonPlan existing = get(id);
            LifecycleAccessPolicy.requireDraftOwner(role, actorId, existing);
            validatePlan(draft);
            if (existing.executionRules() == null) ServiceSupport.reject("executionRules", "Legacy plan has no pinned execution rules; operator remediation required");
            if (!existing.executionRules().seasons().containsKey(draft.seasonType())) ServiceSupport.reject("seasonType", "season type is not present in pinned execution catalog");
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
        for (MetricDefinition metric : MetricDefinition.values()) {
            if (!plan.targetClimateMetrics().containsKey(metric.key())) ValidationSupport.add(errors, "targetClimateMetrics", "target is required for " + metric.key());
        }
        for (var entry : plan.targetClimateMetrics().entrySet()) {
            boolean known = java.util.Arrays.stream(MetricDefinition.values()).anyMatch(metric -> metric.key().equals(entry.getKey()));
            if (!known || entry.getValue() == null || !Double.isFinite(entry.getValue())) ValidationSupport.add(errors, "targetClimateMetrics", "metric targets must use catalog keys and finite values");
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
        LifecycleAccessPolicy.require(role, LifecyclePermission.SUBMIT_PLAN);
        return transactions.execute(id, () -> {
            SeasonPlan plan = get(id);
            LifecycleAccessPolicy.requireDraftOwner(role, actorId, plan);
            validatePlan(plan);
            plan.submit(actorId, clock.instant());
            audit.append(id, AuditAction.SEASON_PLAN_SUBMITTED, actorId, null);
            return plans.save(plan);
        });
    }

    public SeasonPlan verify(UUID id, ActorRole role, String actorId) {
        LifecycleAccessPolicy.require(role, LifecyclePermission.VERIFY_PLAN);
        return transactions.execute(id, () -> {
            SeasonPlan plan = get(id);
            if (plan.status() != SeasonPlanStatus.SUBMITTED) ServiceSupport.reject("status", "only SUBMITTED plans can be verified");
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
            if (plan.status() != SeasonPlanStatus.VERIFIED) ServiceSupport.reject("status", "only VERIFIED plans can be approved");
            var latest = pollen.findLatestBySeasonPlanId(id);
            if (latest.isEmpty() || !id.equals(latest.get().seasonPlanId())) throw invalidPollen();
            var confirmed = pollen.findAuthoritativeConfirmedBySeasonPlanId(id);
            if (confirmed.isEmpty()) {
                if (latest.get().status() == PollenReservationStatus.REJECTED) {
                    String reason = latest.get().comment();
                    if (reason == null || reason.isBlank()) throw invalidPollen();
                    String returnComment = "Pollen reservation rejected: " + reason;
                    plan.returnForRevision(returnComment, actorId, clock.instant());
                    return commitReview(plan, AuditAction.SEASON_PLAN_RETURNED, actorId, returnComment);
                }
                throw invalidPollen();
            }
            if (confirmed.get().status() != PollenReservationStatus.CONFIRMED
                    || !id.equals(confirmed.get().seasonPlanId())) throw invalidPollen();
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
        LifecycleAccessPolicy.requireReviewHistory(actor, plan);
        return audit.entriesFor(id);
    }

    public Map<String, List<String>> calculateChecklist(UUID seasonPlanId) {
        LinkedHashMap<String, List<String>> errors = new LinkedHashMap<>();
        for (String blocker : readiness.inspect(seasonPlanId).blockers()) {
            ValidationSupport.add(errors, blocker, "readiness requirement is not satisfied");
        }
        return errors;
    }

    public void completeItem(UUID seasonPlanId, String key, ActorRole role, String actorId) {
        LifecycleAccessPolicy.require(role, LifecyclePermission.COMPLETE_CHECKLIST);
        transactions.execute(seasonPlanId, () -> {
            SeasonPlan plan = get(seasonPlanId);
            plan.completeReadinessChecklistItem(key, actorId, clock.instant());
            plans.save(plan);
            return null;
        });
    }

    public SeasonPlan markRitualReady(UUID id, ActorRole role, String actorId) {
        LifecycleAccessPolicy.require(role, LifecyclePermission.MARK_RITUAL_READY);
        return transactions.execute(id, () -> {
            SeasonPlan plan = get(id);
            ValidationSupport.throwIfAny(new LinkedHashMap<>(calculateChecklist(id)));
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
                Set.of(plan.createdBy()), Set.of(NotificationOutbox.Channel.IN_APP, NotificationOutbox.Channel.EMAIL),
                comment == null ? "" : comment, clock.instant()));
        return saved;
    }

    private DomainValidationException invalidPollen() {
        return new DomainValidationException(Map.of("pollenReservation", List.of("confirmed pollen reservation is required")));
    }
}
