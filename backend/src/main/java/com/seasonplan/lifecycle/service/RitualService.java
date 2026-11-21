package com.seasonplan.lifecycle.service;

import com.seasonplan.lifecycle.authorization.LifecycleAccessPolicy;
import com.seasonplan.lifecycle.authorization.LifecyclePermission;

import com.seasonplan.lifecycle.exception.NotFoundException;
import com.seasonplan.lifecycle.repository.RitualSessionRepository;
import com.seasonplan.lifecycle.repository.MagicIncidentRepository;
import com.seasonplan.lifecycle.service.CommandTransaction;
import com.seasonplan.lifecycle.domain.*;
import com.seasonplan.security.identity.ActorRole;
import com.seasonplan.lifecycle.repository.SeasonPlanRepository;


import java.time.Clock;
import java.util.UUID;

/** Serialised ritual attempts, immutable snapshots and guarded plan activation. */
public class RitualService {
    private final SeasonPlanRepository plans;
    private final RitualSessionRepository rituals;
    private final MagicIncidentRepository incidents;
    private final RitualReadinessChecker readiness;
    private final CommandTransaction transactions;
    private final AuditLogService audit;
    private final Clock clock;

    public RitualService(SeasonPlanRepository plans, RitualSessionRepository rituals, MagicIncidentRepository incidents,
                         RitualReadinessChecker readiness, CommandTransaction transactions, AuditLogService audit, Clock clock) {
        this.plans = plans; this.rituals = rituals; this.incidents = incidents; this.readiness = readiness;
        this.transactions = transactions; this.audit = audit; this.clock = clock;
    }
    public RitualSession start(UUID planId, ActorRole role, String actorId) {
        LifecycleAccessPolicy.require(role, LifecyclePermission.START_RITUAL);
        return transactions.execute(planId, () -> {
            ServiceSupport.plan(plans, planId, SeasonPlanStatus.RITUAL_READY);
            var previous = rituals.forPlan(planId);
            if (previous.stream().anyMatch(session -> session.inProgress() || session.result() == RitualSession.Result.SUCCESS)) {
                ServiceSupport.reject("ritualSession", "an attempt is already running or successful");
            }
            var check = readiness.inspect(planId);
            if (!check.blockers().isEmpty() || incidents.forPlan(planId).stream().anyMatch(MagicIncident::critical)) {
                ServiceSupport.reject("readiness", "ritual readiness failed: " + check.blockers());
            }
            int attempt = previous.stream().mapToInt(RitualSession::attempt).max().orElse(0) + 1;
            RitualSession session = RitualSession.start(UUID.randomUUID(), planId, attempt, actorId, clock.instant(), check.participants());
            rituals.save(session);
            audit.append(planId, AuditAction.RITUAL_SESSION_STARTED, actorId, session.id().toString());
            return session;
        });
    }
    public RitualSession recordEvent(UUID id, String type, String payload, ActorRole role, String actorId) {
        LifecycleAccessPolicy.require(role, LifecyclePermission.RECORD_RITUAL_EVENT);
        return transactions.execute(get(id).seasonPlanId(), () -> {
            RitualSession session = get(id);
            ServiceSupport.plan(plans, session.seasonPlanId(), SeasonPlanStatus.RITUAL_READY);
            RitualSession updated = session.addEvent(new RitualSession.Event(clock.instant(), type, payload));
            rituals.save(updated);
            audit.append(session.seasonPlanId(), AuditAction.RITUAL_EVENT_RECORDED, actorId, type);
            return updated;
        });
    }
    public RitualSession finish(UUID id, String failureCode, String failureMessage, ActorRole role, String actorId) {
        LifecycleAccessPolicy.require(role, LifecyclePermission.FINISH_RITUAL);
        if ((failureCode == null) != (failureMessage == null)) throw new IllegalArgumentException("failure code and message must be supplied together");
        return transactions.execute(get(id).seasonPlanId(), () -> {
            RitualSession session = get(id);
            SeasonPlan plan = ServiceSupport.copy(ServiceSupport.plan(plans, session.seasonPlanId(), SeasonPlanStatus.RITUAL_READY));
            var check = readiness.inspect(plan.id());
            String code = failureCode;
            String message = failureMessage;
            if (code == null && (!check.blockers().isEmpty() || incidents.forPlan(plan.id()).stream().anyMatch(MagicIncident::critical)
                    || !new java.util.HashSet<>(check.participants()).equals(new java.util.HashSet<>(session.participants())))) {
                code = "READINESS_BLOCKED"; message = "Проверка готовности не пройдена";
            }
            if (code == null && !session.events().stream().map(RitualSession.Event::type).toList().containsAll(check.requiredEvents())) {
                code = "MISSING_EVENTS"; message = "Не завершены обязательные события ритуала";
            }
            RitualSession completed = code == null ? session.succeed(clock.instant()) : session.fail(code, message, clock.instant());
            if (completed.result() == RitualSession.Result.SUCCESS) plan.activate(completed, actorId, clock.instant());
            rituals.save(completed);
            audit.append(plan.id(), AuditAction.RITUAL_SESSION_FINISHED, actorId, completed.result().name());
            if (completed.result() == RitualSession.Result.SUCCESS) {
                plans.save(plan);
                audit.append(plan.id(), AuditAction.SEASON_PLAN_ACTIVATED, actorId, completed.id().toString());
            }
            return completed;
        });
    }
    private RitualSession get(UUID id) {
        return rituals.find(id).orElseThrow(() -> new NotFoundException("ritual session not found: " + id));
    }
}
