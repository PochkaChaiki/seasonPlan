package com.seasonplan.lifecycle.application.execution;

import com.seasonplan.lifecycle.application.UseCaseSupport;
import com.seasonplan.lifecycle.application.port.CommandTransaction;
import com.seasonplan.lifecycle.application.port.ExecutionPorts;
import com.seasonplan.lifecycle.domain.*;
import com.seasonplan.lifecycle.application.port.SeasonPlanRepository;
import com.seasonplan.lifecycle.application.shared.*;
import java.time.Clock;
import java.util.UUID;

/** UC-4: serialised ritual attempts, immutable snapshots and guarded activation. */
public class RitualService {
    private final SeasonPlanRepository plans;
    private final ExecutionPorts.Rituals rituals;
    private final ExecutionPorts.Incidents incidents;
    private final ExecutionPorts.LaunchReadiness readiness;
    private final CommandTransaction transactions;
    private final AuditLogService audit;
    private final Clock clock;

    public RitualService(SeasonPlanRepository plans, ExecutionPorts.Rituals rituals, ExecutionPorts.Incidents incidents,
                         ExecutionPorts.LaunchReadiness readiness, CommandTransaction transactions, AuditLogService audit, Clock clock) {
        this.plans = plans; this.rituals = rituals; this.incidents = incidents; this.readiness = readiness;
        this.transactions = transactions; this.audit = audit; this.clock = clock;
    }
    public RitualSession start(UUID planId, ActorRole role, String actorId) {
        ValidationSupport.requireRole(role, ActorRole.TALENT_COORDINATOR);
        return transactions.execute(planId, () -> {
            UseCaseSupport.plan(plans, planId, SeasonPlanStatus.RITUAL_READY);
            var previous = rituals.forPlan(planId);
            if (previous.stream().anyMatch(session -> session.inProgress() || session.result() == RitualSession.Result.SUCCESS)) {
                UseCaseSupport.reject("ritualSession", "an attempt is already running or successful");
            }
            var check = readiness.inspect(planId);
            if (!check.blockers().isEmpty() || incidents.forPlan(planId).stream().anyMatch(MagicIncident::critical)) {
                UseCaseSupport.reject("readiness", "ritual readiness failed: " + check.blockers());
            }
            int attempt = previous.stream().mapToInt(RitualSession::attempt).max().orElse(0) + 1;
            RitualSession session = RitualSession.start(UUID.randomUUID(), planId, attempt, actorId, clock.instant(), check.participants());
            rituals.save(session);
            audit.append(planId, AuditAction.RITUAL_SESSION_STARTED, actorId, session.id().toString());
            return session;
        });
    }
    public RitualSession recordEvent(UUID id, String type, String payload, ActorRole role, String actorId) {
        ValidationSupport.requireRole(role, ActorRole.TALENT_COORDINATOR);
        return transactions.execute(get(id).seasonPlanId(), () -> {
            RitualSession session = get(id);
            UseCaseSupport.plan(plans, session.seasonPlanId(), SeasonPlanStatus.RITUAL_READY);
            RitualSession updated = session.addEvent(new RitualSession.Event(clock.instant(), type, payload));
            rituals.save(updated);
            audit.append(session.seasonPlanId(), AuditAction.RITUAL_EVENT_RECORDED, actorId, type);
            return updated;
        });
    }
    public RitualSession finish(UUID id, String failureCode, String failureMessage, ActorRole role, String actorId) {
        ValidationSupport.requireRole(role, ActorRole.TALENT_COORDINATOR);
        if ((failureCode == null) != (failureMessage == null)) throw new IllegalArgumentException("failure code and message must be supplied together");
        return transactions.execute(get(id).seasonPlanId(), () -> {
            RitualSession session = get(id);
            SeasonPlan plan = UseCaseSupport.copy(UseCaseSupport.plan(plans, session.seasonPlanId(), SeasonPlanStatus.RITUAL_READY));
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
