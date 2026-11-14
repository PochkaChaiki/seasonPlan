package com.seasonplan.lifecycle.application.preparation;

import com.seasonplan.lifecycle.domain.*;
import com.seasonplan.lifecycle.application.port.*;
import com.seasonplan.lifecycle.application.shared.*;
import com.seasonplan.lifecycle.service.AuditLogService;

import java.time.Clock;
import java.util.UUID;

/** UC-3: transition an approved plan after its readiness checks pass. */
public class PreparationService {
    private final SeasonPlanRepository plans;
    private final ReadinessChecklistService readiness;
    private final AuditLogService audit;
    private final Clock clock;
    private final CommandTransaction transactions;

    public PreparationService(SeasonPlanRepository plans, ReadinessChecklistService readiness,
                              AuditLogService audit, CommandTransaction transactions, Clock clock) {
        this.plans = plans;
        this.readiness = readiness;
        this.audit = audit;
        this.transactions = transactions;
        this.clock = clock;
    }

    public SeasonPlan markRitualReady(UUID id, ActorRole role, String actorId) {
        ValidationSupport.requireAnyRole(role, ActorRole.TALENT_COORDINATOR, ActorRole.POLLEN_GUARDIAN);
        return transactions.execute(id, () -> {
            SeasonPlan plan = plans.findById(id).orElseThrow(() -> new NotFoundException("season plan not found: " + id));
            readiness.assertReadyForRitual(id);
            plan.markRitualReady(actorId, clock.instant());
            audit.append(id, AuditAction.SEASON_MARKED_RITUAL_READY, actorId, null);
            return plans.save(plan);
        });
    }
}
