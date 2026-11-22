package com.seasonplan.ritual.application;

import com.seasonplan.response.domain.MagicIncident;
import com.seasonplan.ritual.api.RitualReadinessChecker;
import com.seasonplan.ritual.domain.RitualSession;
import com.seasonplan.ritual.domain.RitualPolicy;
import com.seasonplan.season.api.SeasonLifecycle;
import com.seasonplan.season.api.SeasonFacts;
import com.seasonplan.season.domain.SeasonPlanStatus;

import com.seasonplan.audit.api.AuditAction;
import com.seasonplan.audit.api.AuditTrail;
import com.seasonplan.identity.api.LifecycleAccessPolicy;
import com.seasonplan.identity.api.LifecyclePermission;

import com.seasonplan.shared.application.NotFoundException;
import com.seasonplan.ritual.application.port.RitualSessionRepository;
import com.seasonplan.response.api.ResponseQueries;
import com.seasonplan.season.api.CommandTransaction;
import com.seasonplan.identity.api.ActorRole;
import com.seasonplan.season.api.SeasonQueries;


import java.time.Clock;
import java.util.UUID;

/** Serialised ritual attempts, immutable snapshots and guarded plan activation. */
public class RitualService {
    private final SeasonQueries seasons;
    private final SeasonLifecycle lifecycle;
    private final RitualSessionRepository rituals;
    private final ResponseQueries response;
    private final RitualReadinessChecker readiness;
    private final CommandTransaction transactions;
    private final AuditTrail audit;
    private final Clock clock;

    public RitualService(SeasonQueries seasons, SeasonLifecycle lifecycle, RitualSessionRepository rituals, ResponseQueries response,
                         RitualReadinessChecker readiness, CommandTransaction transactions, AuditTrail audit, Clock clock) {
        this.seasons = seasons; this.lifecycle = lifecycle; this.rituals = rituals; this.response = response; this.readiness = readiness;
        this.transactions = transactions; this.audit = audit; this.clock = clock;
    }
    public RitualSession start(UUID planId, ActorRole role, String actorId) {
        LifecycleAccessPolicy.require(role, LifecyclePermission.START_RITUAL);
        return transactions.execute(planId, () -> {
            RitualPolicy.requireStartStatus(seasons.get(planId).status());
            var previous = rituals.forPlan(planId);
            RitualPolicy.requireNoRunningOrSuccessfulAttempt(previous);
            var check = readiness.inspect(planId);
            RitualSession session = RitualPolicy.start(UUID.randomUUID(), planId, previous, check,
                    response.incidentsForPlan(planId).stream().anyMatch(MagicIncident::critical), actorId, clock.instant());
            rituals.save(session);
            audit.append(planId, AuditAction.RITUAL_SESSION_STARTED, actorId, session.id().toString());
            return session;
        });
    }
    public RitualSession recordEvent(UUID id, String type, String payload, ActorRole role, String actorId) {
        LifecycleAccessPolicy.require(role, LifecyclePermission.RECORD_RITUAL_EVENT);
        return transactions.execute(get(id).seasonPlanId(), () -> {
            RitualSession session = get(id);
            RitualPolicy.requireDiagnosticStatus(seasons.get(session.seasonPlanId()).status());
            RitualSession updated = session.addEvent(new RitualSession.Event(clock.instant(), type, payload));
            rituals.save(updated);
            audit.append(session.seasonPlanId(), AuditAction.RITUAL_EVENT_RECORDED, actorId, type);
            return updated;
        });
    }
    public RitualSession finish(UUID id, String failureCode, String failureMessage, ActorRole role, String actorId) {
        LifecycleAccessPolicy.require(role, LifecyclePermission.FINISH_RITUAL);
        RitualPolicy.validateFailure(failureCode, failureMessage);
        return transactions.execute(get(id).seasonPlanId(), () -> {
            RitualSession session = get(id);
            SeasonFacts plan = seasons.get(session.seasonPlanId());
            RitualPolicy.requireFinishable(plan.status(), failureCode);
            var check = RitualPolicy.needsReadiness(failureCode) ? readiness.inspect(plan.id()) : null;
            boolean critical = check != null && check.blockers().isEmpty() && response.incidentsForPlan(plan.id()).stream().anyMatch(MagicIncident::critical);
            RitualSession completed = RitualPolicy.finish(session, plan.status(), failureCode, failureMessage, check, critical, clock.instant());
            rituals.save(completed);
            audit.append(plan.id(), AuditAction.RITUAL_SESSION_FINISHED, actorId, completed.result().name());
            if (completed.result() == RitualSession.Result.SUCCESS) {
                lifecycle.activate(plan.id(), new SeasonLifecycle.RitualEvidence(completed.id(), completed.seasonPlanId(),
                        completed.result() == RitualSession.Result.SUCCESS), actorId);
            }
            return completed;
        });
    }
    private RitualSession get(UUID id) {
        return rituals.find(id).orElseThrow(() -> new NotFoundException("ritual session not found: " + id));
    }
}
