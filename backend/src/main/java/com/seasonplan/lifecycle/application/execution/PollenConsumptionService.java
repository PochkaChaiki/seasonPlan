package com.seasonplan.lifecycle.application.execution;

import com.seasonplan.lifecycle.application.UseCaseSupport;
import com.seasonplan.lifecycle.application.port.*;
import com.seasonplan.lifecycle.domain.*;
import com.seasonplan.lifecycle.application.shared.*;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.util.Set;
import java.util.UUID;

/** UC-4: externally idempotent pollen consumption and transactional threshold notifications. */
public class PollenConsumptionService {
    private final SeasonPlanRepository plans;
    private final PollenTree tree;
    private final NotificationOutbox outbox;
    private final CommandTransaction transactions;
    private final AuditLogService audit;
    private final Clock clock;
    public PollenConsumptionService(SeasonPlanRepository plans, PollenTree tree, NotificationOutbox outbox,
                                    CommandTransaction transactions, AuditLogService audit, Clock clock) {
        this.plans = plans; this.tree = tree; this.outbox = outbox; this.transactions = transactions; this.audit = audit; this.clock = clock;
    }
    public PollenTree.Summary record(UUID planId, UUID clientEventId, BigDecimal grams, ActorRole role, String actorId) {
        ValidationSupport.requireRole(role, ActorRole.POLLEN_GUARDIAN);
        java.util.Objects.requireNonNull(clientEventId);
        java.util.Objects.requireNonNull(grams);
        BigDecimal amount = grams.setScale(3, RoundingMode.HALF_UP);
        if (amount.signum() <= 0) throw new IllegalArgumentException("consumption grams must be positive after rounding");
        return transactions.execute(planId, () -> {
            UseCaseSupport.plan(plans, planId, SeasonPlanStatus.ACTIVE);
            PollenTree.Summary summary = tree.recordConsumption(planId, clientEventId, amount);
            if (summary.actualGrams().compareTo(summary.confirmedGrams().multiply(new BigDecimal("0.8"))) >= 0) notify(planId, clientEventId, summary, "80", false);
            if (summary.actualGrams().compareTo(summary.confirmedGrams()) >= 0) notify(planId, clientEventId, summary, "100", false);
            if (summary.actualGrams().compareTo(summary.confirmedGrams()) > 0) notify(planId, clientEventId, summary, "OVER_100", true);
            audit.append(planId, AuditAction.POLLEN_CONSUMPTION_RECORDED, actorId, clientEventId.toString());
            return summary;
        });
    }
    private void notify(UUID planId, UUID eventId, PollenTree.Summary summary, String threshold, boolean chief) {
        outbox.enqueue(new NotificationOutbox.Event("pollen:" + summary.reservationId() + ":" + threshold + ":" + eventId,
                planId, "POLLEN_" + threshold, chief ? Set.of("POLLEN_GUARDIAN", "TALENT_COORDINATOR", "CHIEF_GUARDIAN")
                : Set.of("POLLEN_GUARDIAN", "TALENT_COORDINATOR"), Set.of(NotificationOutbox.Channel.IN_APP, NotificationOutbox.Channel.EMAIL),
                "Расход пыльцы: " + summary.actualGrams() + " / " + summary.confirmedGrams() + " g", clock.instant()));
    }
}
