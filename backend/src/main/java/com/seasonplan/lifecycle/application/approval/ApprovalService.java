package com.seasonplan.lifecycle.application.approval;

import com.seasonplan.lifecycle.domain.*;
import com.seasonplan.lifecycle.application.port.CommandTransaction;
import com.seasonplan.lifecycle.application.port.NotificationOutbox;
import com.seasonplan.lifecycle.application.port.SeasonPlanRepository;
import com.seasonplan.lifecycle.application.pollen.PollenService;
import com.seasonplan.lifecycle.application.shared.*;
import com.seasonplan.lifecycle.service.AuditLogService;

import java.time.Clock;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;

/** UC-2 review commands commit plan, audit and outbox in one transaction. */
public class ApprovalService {
    private final SeasonPlanRepository plans;
    private final PollenService pollen;
    private final AuditLogService audit;
    private final CommandTransaction transactions;
    private final NotificationOutbox outbox;
    private final Consumer<SeasonPlan> validatePlan;
    private final Clock clock;

    public ApprovalService(SeasonPlanRepository plans, PollenService pollen, AuditLogService audit,
                           CommandTransaction transactions, NotificationOutbox outbox,
                           Consumer<SeasonPlan> validatePlan, Clock clock) {
        this.plans = plans;
        this.pollen = pollen;
        this.audit = audit;
        this.transactions = transactions;
        this.outbox = outbox;
        this.validatePlan = validatePlan;
        this.clock = clock;
    }

    public SeasonPlan verify(UUID id, ActorRole role, String actorId) {
        ValidationSupport.requireRole(role, ActorRole.CHIEF_GUARDIAN);
        return transactions.execute(id, () -> {
            SeasonPlan plan = get(id);
            if (plan.status() != SeasonPlanStatus.SUBMITTED) throw invalidStatus("only SUBMITTED plans can be verified");
            validatePlan.accept(plan);
            plan.verify(actorId, clock.instant());
            return commit(plan, AuditAction.SEASON_PLAN_VERIFIED, actorId, null);
        });
    }

    public SeasonPlan approve(UUID id, String comment, ActorRole role, String actorId) {
        return approve(id, comment, List.of(), role, actorId);
    }

    public SeasonPlan approve(UUID id, String comment, List<ApprovalCondition> conditions, ActorRole role, String actorId) {
        ValidationSupport.requireRole(role, ActorRole.CHIEF_GUARDIAN);
        return transactions.execute(id, () -> {
            SeasonPlan plan = get(id);
            if (plan.status() != SeasonPlanStatus.VERIFIED) throw invalidStatus("only VERIFIED plans can be approved");
            var latest = pollen.findLatestBySeasonPlanId(id);
            if (latest.isEmpty() || !id.equals(latest.get().seasonPlanId())) throw invalidPollen();
            var confirmed = pollen.findAuthoritativeConfirmedBySeasonPlanId(id);
            if (confirmed.isEmpty()) {
                if (latest.get().status() == PollenReservationStatus.REJECTED) {
                    String reason = latest.get().comment();
                    if (reason == null || reason.isBlank()) throw invalidPollen();
                    String returnComment = "Pollen reservation rejected: " + reason;
                    plan.returnForRevision(returnComment, actorId, clock.instant());
                    return commit(plan, AuditAction.SEASON_PLAN_RETURNED, actorId, returnComment);
                }
                throw invalidPollen();
            }
            if (confirmed.get().status() != PollenReservationStatus.CONFIRMED
                    || !id.equals(confirmed.get().seasonPlanId())) throw invalidPollen();
            validatePlan.accept(plan);
            plan.approve(comment, conditions, actorId, clock.instant());
            return commit(plan, AuditAction.SEASON_PLAN_APPROVED, actorId, comment);
        });
    }

    public SeasonPlan returnForRevision(UUID id, String comment, ActorRole role, String actorId) {
        ValidationSupport.requireRole(role, ActorRole.CHIEF_GUARDIAN);
        return transactions.execute(id, () -> {
            SeasonPlan plan = get(id);
            plan.returnForRevision(comment, actorId, clock.instant());
            return commit(plan, AuditAction.SEASON_PLAN_RETURNED, actorId, comment);
        });
    }

    private SeasonPlan commit(SeasonPlan plan, AuditAction action, String actorId, String comment) {
        SeasonPlan saved = plans.save(plan);
        AuditLogEntry entry = audit.append(plan.id(), action, actorId, comment);
        outbox.enqueue(new NotificationOutbox.Event(entry.id().toString(), plan.id(), action.name(),
                Set.of(plan.createdBy()), Set.of(NotificationOutbox.Channel.IN_APP, NotificationOutbox.Channel.EMAIL),
                comment == null ? "" : comment, clock.instant()));
        return saved;
    }

    private SeasonPlan get(UUID id) {
        return plans.findById(id).orElseThrow(() -> new NotFoundException("season plan not found: " + id));
    }

    private DomainValidationException invalidPollen() {
        return new DomainValidationException(java.util.Map.of("pollenReservation", List.of("confirmed pollen reservation is required")));
    }

    private DomainValidationException invalidStatus(String message) {
        return new DomainValidationException(new LinkedHashMap<>() {{ put("status", List.of(message)); }});
    }
}
