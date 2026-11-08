package com.seasonplan.lifecycle.application.pollen;

import com.seasonplan.lifecycle.application.UseCaseSupport;
import com.seasonplan.lifecycle.application.port.*;
import com.seasonplan.lifecycle.application.shared.*;
import com.seasonplan.lifecycle.domain.*;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;

/** The sole application entry point for pollen reads/commands; only this service calls the external client. */
public class PollenService {
    private final SeasonPlanRepository plans;
    private final PollenTreeClient client;
    private final Supplier<NotificationOutbox> outbox;
    private final CommandTransaction transactions;
    private final AuditLogService audit;
    private final Clock clock;

    public PollenService(SeasonPlanRepository plans, PollenTreeClient client, Supplier<NotificationOutbox> outbox,
                         CommandTransaction transactions, AuditLogService audit, Clock clock) {
        this.plans = plans;
        this.client = client;
        this.outbox = outbox;
        this.transactions = transactions;
        this.audit = audit;
        this.clock = clock;
    }

    public PollenReservation requestReservation(UUID planId, int requestedAmount, ActorRole role, String actorId) {
        return requestReservation(planId, UUID.randomUUID(), requestedAmount, role, actorId);
    }

    public PollenReservation requestReservation(UUID planId, UUID requestId, int requestedAmount, ActorRole role, String actorId) {
        ValidationSupport.requireRole(role, ActorRole.POLLEN_GUARDIAN);
        UseCaseSupport.plan(plans, planId);
        requirePositive(requestedAmount, "requestedAmount");
        return client.requestReservation(planId, requestId, requestedAmount, actorId);
    }

    public PollenReservation confirmReservation(UUID id, int confirmedAmount, String comment, ActorRole role, String actorId) {
        ValidationSupport.requireRole(role, ActorRole.POLLEN_GUARDIAN);
        requirePositive(confirmedAmount, "confirmedAmount");
        PollenReservation result = client.confirmReservation(id, confirmedAmount, comment, actorId);
        return auditDecision(result, AuditAction.POLLEN_RESERVATION_CONFIRMED, actorId, comment);
    }

    public PollenReservation rejectReservation(UUID id, String comment, ActorRole role, String actorId) {
        ValidationSupport.requireRole(role, ActorRole.POLLEN_GUARDIAN);
        PollenReservation result = client.rejectReservation(id, comment, actorId);
        return auditDecision(result, AuditAction.POLLEN_RESERVATION_REJECTED, actorId, comment);
    }

    public PollenReservation get(UUID id) {
        return client.findById(id).orElseThrow(() -> new NotFoundException("pollen reservation not found: " + id));
    }

    public Optional<PollenReservation> findLatestBySeasonPlanId(UUID planId) {
        return client.findLatestBySeasonPlanId(planId);
    }

    public Optional<PollenUsage> usage(UUID planId) {
        return client.summary(planId).map(PollenTreeClient.Summary::snapshot);
    }

    public PollenCapacity capacity() { return client.capacity(); }

    public PollenUsage recordConsumption(UUID planId, UUID clientEventId, BigDecimal grams, ActorRole role, String actorId) {
        ValidationSupport.requireRole(role, ActorRole.POLLEN_GUARDIAN);
        Objects.requireNonNull(clientEventId);
        BigDecimal amount = Objects.requireNonNull(grams).setScale(3, RoundingMode.HALF_UP);
        if (amount.signum() <= 0) throw new IllegalArgumentException("consumption grams must be positive after rounding");
        NotificationOutbox notifications = outbox.get(); // Fail before spending if UC-4 delivery is not configured.
        return transactions.execute(planId, () -> {
            UseCaseSupport.plan(plans, planId, SeasonPlanStatus.ACTIVE);
            PollenTreeClient.Summary summary = client.recordConsumption(planId, clientEventId, amount);
            if (summary.actualGrams().compareTo(summary.confirmedGrams().multiply(new BigDecimal("0.8"))) >= 0)
                notify(notifications, planId, clientEventId, summary, "80", false);
            if (summary.actualGrams().compareTo(summary.confirmedGrams()) >= 0)
                notify(notifications, planId, clientEventId, summary, "100", false);
            if (summary.actualGrams().compareTo(summary.confirmedGrams()) > 0)
                notify(notifications, planId, clientEventId, summary, "OVER_100", true);
            audit.appendOnce(eventId("pollen-consumption:" + planId + ":" + clientEventId), planId,
                    AuditAction.POLLEN_CONSUMPTION_RECORDED, actorId, clientEventId.toString());
            return summary.snapshot();
        });
    }

    private PollenReservation auditDecision(PollenReservation result, AuditAction action, String actorId, String comment) {
        // Only our immutable audit is stored locally. The provider retains the reservation itself.
        String auditComment = "reservationId=" + result.id() + "; " + Objects.toString(comment, "");
        audit.appendOnce(eventId("pollen-decision:" + result.id() + ":" + action.name()), result.seasonPlanId(), action, actorId, auditComment);
        return result;
    }

    private UUID eventId(String key) { return UUID.nameUUIDFromBytes(key.getBytes(StandardCharsets.UTF_8)); }

    private void requirePositive(int amount, String field) {
        if (amount <= 0) throw new DomainValidationException(Map.of(field, List.of("amount must be positive")));
    }

    private void notify(NotificationOutbox notifications, UUID planId, UUID eventId, PollenTreeClient.Summary summary, String threshold, boolean chief) {
        notifications.enqueue(new NotificationOutbox.Event("pollen:" + summary.reservationId() + ":" + threshold + ":" + eventId,
                planId, "POLLEN_" + threshold, chief ? Set.of("POLLEN_GUARDIAN", "TALENT_COORDINATOR", "CHIEF_GUARDIAN")
                : Set.of("POLLEN_GUARDIAN", "TALENT_COORDINATOR"), Set.of(NotificationOutbox.Channel.IN_APP, NotificationOutbox.Channel.EMAIL),
                "Расход пыльцы: " + summary.actualGrams() + " / " + summary.confirmedGrams() + " g", clock.instant()));
    }
}
