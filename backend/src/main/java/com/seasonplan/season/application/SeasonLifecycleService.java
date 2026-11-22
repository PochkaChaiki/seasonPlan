package com.seasonplan.season.application;

import com.seasonplan.audit.api.AuditAction;
import com.seasonplan.audit.api.AuditTrail;
import com.seasonplan.season.api.CommandTransaction;
import com.seasonplan.season.api.SeasonLifecycle;
import com.seasonplan.season.application.port.SeasonPlanRepository;
import java.time.Clock;
import java.util.UUID;

/** Owns lifecycle state and audit writes inside the season row-lock transaction. */
public final class SeasonLifecycleService implements SeasonLifecycle {
    private final SeasonPlanRepository plans;
    private final CommandTransaction transactions;
    private final AuditTrail audit;
    private final Clock clock;

    public SeasonLifecycleService(SeasonPlanRepository plans, CommandTransaction transactions, AuditTrail audit, Clock clock) {
        this.plans = plans; this.transactions = transactions; this.audit = audit; this.clock = clock;
    }
    @Override public void activate(UUID planId, RitualEvidence evidence, String actorId) {
        transactions.execute(planId, () -> {
            var plan = ServiceSupport.copy(ServiceSupport.plan(plans, planId));
            plan.activate(evidence, actorId, clock.instant());
            plans.save(plan);
            audit.append(planId, AuditAction.SEASON_PLAN_ACTIVATED, actorId, evidence.ritualId().toString());
            return null;
        });
    }
    @Override public void complete(UUID planId, ReportEvidence evidence, String actorId) {
        transactions.execute(planId, () -> {
            var plan = ServiceSupport.copy(ServiceSupport.plan(plans, planId));
            plan.complete(evidence, actorId, clock.instant());
            plans.save(plan);
            audit.append(planId, AuditAction.SEASON_PLAN_COMPLETED, actorId, evidence.reportId().toString());
            return null;
        });
    }
    @Override public void archive(UUID planId, ReportEvidence evidence, String actorId) {
        transactions.execute(planId, () -> {
            var plan = ServiceSupport.copy(ServiceSupport.plan(plans, planId));
            plan.archive(evidence, actorId, clock.instant());
            plans.save(plan);
            audit.append(planId, AuditAction.SEASON_PLAN_ARCHIVED, actorId, evidence.reportId().toString());
            return null;
        });
    }
    @Override public void enterEmergency(UUID planId, String actorId) {
        transactions.execute(planId, () -> {
            var plan = ServiceSupport.copy(ServiceSupport.plan(plans, planId));
            if (plan.enterEmergency(actorId, clock.instant())) {
                plans.save(plan);
                audit.append(planId, AuditAction.SEASON_EMERGENCY_ENTERED, actorId, "CRITICAL incident");
            }
            return null;
        });
    }
}
