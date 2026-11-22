package com.seasonplan.pollen.domain;

import com.seasonplan.shared.domain.DomainValidationException;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;

/** Immutable exact-event evidence. Quarantine retains external facts without settling resource uncertainty. */
public record ConsumptionIntent(UUID id, UUID planId, long materialRevision, UUID clientKey, BigDecimal amount,
        String actorId, UUID expectedReservationId, State state, Receipt receipt, Instant createdAt) {
    public enum State { DISPATCHED, UNKNOWN, RECEIVED, APPLIED, QUARANTINED, DENIED }
    public record Receipt(UUID planId, UUID clientKey, BigDecimal amount, UUID reservationId,
                          BigDecimal confirmedGrams, BigDecimal actualGrams, String version) {
        public Receipt {
            Objects.requireNonNull(planId); Objects.requireNonNull(clientKey);
            amount = PollenConsumptionPolicy.normalize(amount);
            var usage = new PollenUsage(reservationId, confirmedGrams, actualGrams, version);
            confirmedGrams = usage.confirmedGrams(); actualGrams = usage.actualGrams();
        }
        public PollenUsage snapshot() { return new PollenUsage(reservationId, confirmedGrams, actualGrams, version); }
        public void requireHistoricalAmount(UUID plan, UUID key, BigDecimal requested) {
            if (planId.equals(plan) && clientKey.equals(key) && amount.compareTo(requested) != 0) invalid();
        }
    }
    public ConsumptionIntent {
        Objects.requireNonNull(id); Objects.requireNonNull(planId); Objects.requireNonNull(clientKey);
        Objects.requireNonNull(state); Objects.requireNonNull(createdAt);
        amount = PollenConsumptionPolicy.normalize(amount);
        if (materialRevision < 0 || actorId == null || actorId.isBlank()) invalid();
        if ((state == State.RECEIVED || state == State.APPLIED || state == State.QUARANTINED) != (receipt != null)) invalid();
        if (state == State.APPLIED && !matches(planId, clientKey, amount, expectedReservationId, receipt)) invalid();
    }
    public void requireSame(UUID plan, BigDecimal grams, String actor) {
        if (!planId.equals(plan) || amount.compareTo(grams) != 0 || !actorId.equals(actor)) invalid();
    }
    public boolean unresolved() { return state != State.APPLIED && state != State.DENIED; }
    public ConsumptionIntent uncertain() { return unresolved() && receipt == null ? copy(State.UNKNOWN, null) : this; }
    public ConsumptionIntent denied() {
        if (receipt != null || !unresolved()) invalid();
        return copy(State.DENIED, null);
    }
    public ConsumptionIntent received(Receipt value) {
        Objects.requireNonNull(value);
        if (receipt != null) { if (!receipt.equals(value)) invalid(); return this; }
        if (!unresolved()) invalid();
        return copy(State.RECEIVED, value);
    }
    public ConsumptionIntent apply(long revision, boolean eligible) {
        if (state != State.RECEIVED) invalid();
        return copy(revision == materialRevision && eligible && matches(planId, clientKey, amount, expectedReservationId, receipt)
                ? State.APPLIED : State.QUARANTINED, receipt);
    }
    private static boolean matches(UUID plan, UUID key, BigDecimal amount, UUID agreement, Receipt receipt) {
        return receipt != null && plan.equals(receipt.planId()) && key.equals(receipt.clientKey()) && amount.compareTo(receipt.amount()) == 0
                && (agreement == null || agreement.equals(receipt.reservationId()));
    }
    private ConsumptionIntent copy(State next, Receipt result) {
        return new ConsumptionIntent(id, planId, materialRevision, clientKey, amount, actorId, expectedReservationId, next, result, createdAt);
    }
    private static void invalid() { throw new DomainValidationException(Map.of("clientEventId", List.of("consumption identity, evidence or state mismatch"))); }
}
