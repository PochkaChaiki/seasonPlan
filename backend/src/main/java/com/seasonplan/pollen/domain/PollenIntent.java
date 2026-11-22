package com.seasonplan.pollen.domain;

import com.seasonplan.shared.domain.DomainValidationException;
import java.time.Instant;
import java.util.*;

/** Immutable intent and monotonic recovery state. Provider dispatch is never replayed from uncertainty. */
public record PollenIntent(UUID id, Payload payload, State state, ReservationReceipt receipt, String denialField, Instant createdAt) {
    public enum Kind { REQUEST, CONFIRM, REJECT }
    public enum State { DISPATCHED, UNKNOWN, RECEIVED, APPLIED, OBSOLETE, DENIED }
    public enum Step { RECONCILE, APPLY, RETURN, OBSOLETE, DENIED }
    public record Payload(Kind kind, UUID planId, long materialRevision, UUID clientKey, UUID providerKey,
                          UUID reservationId, Integer amount, String comment, String actorId) {
        public Payload {
            Objects.requireNonNull(kind); Objects.requireNonNull(planId); Objects.requireNonNull(clientKey);
            if (materialRevision < 0) invalid("materialRevision", "material revision is required");
            if (actorId == null || actorId.isBlank()) invalid("actorId", "operation actor is required");
            if (kind == Kind.REQUEST) Objects.requireNonNull(providerKey);
            else Objects.requireNonNull(reservationId);
            if (kind != Kind.REJECT) {
                if (amount == null) invalid("amount", "amount is required");
                PollenConsumptionPolicy.requirePositive(amount, kind == Kind.REQUEST ? "requestedAmount" : "confirmedAmount");
            } else if (comment == null || comment.isBlank()) invalid("comment", "rejection reason is required");
        }
        public void requireSameInput(Payload other) {
            if (kind != other.kind || !planId.equals(other.planId) || !clientKey.equals(other.clientKey)
                    || !Objects.equals(reservationId, other.reservationId) || !Objects.equals(amount, other.amount)
                    || !Objects.equals(comment, other.comment) || !actorId.equals(other.actorId))
                invalid("requestId", "operation key cannot be reused with a changed payload");
        }
    }
    public PollenIntent {
        Objects.requireNonNull(id); Objects.requireNonNull(payload); Objects.requireNonNull(state); Objects.requireNonNull(createdAt);
        boolean known = state == State.RECEIVED || state == State.APPLIED || state == State.OBSOLETE;
        if (known != (receipt != null)) invalid("operation", "receipt and operation state disagree");
        if ((state == State.DENIED) != (denialField != null && !denialField.isBlank())) invalid("operation", "denial and operation state disagree");
        if (state != State.DENIED && denialField != null) invalid("operation", "only denied operations contain denial evidence");
        if (receipt != null) validateReceipt(payload, receipt);
    }
    public boolean unresolved() { return state == State.DISPATCHED || state == State.UNKNOWN || state == State.RECEIVED; }
    public Step nextStep() {
        return switch (state) {
            case DISPATCHED, UNKNOWN -> Step.RECONCILE;
            case RECEIVED -> Step.APPLY;
            case APPLIED -> Step.RETURN;
            case OBSOLETE -> Step.OBSOLETE;
            case DENIED -> Step.DENIED;
        };
    }
    public PollenIntent uncertain() {
        return unresolved() && receipt == null ? new PollenIntent(id, payload, State.UNKNOWN, null, null, createdAt) : this;
    }
    public PollenIntent received(ReservationReceipt result) {
        validateReceipt(payload, result);
        if (receipt != null) {
            if (!receipt.equals(result)) invalid("operation", "known receipt cannot change");
            return this;
        }
        if (!unresolved()) invalid("operation", "terminal operation cannot acquire another result");
        return new PollenIntent(id, payload, State.RECEIVED, result, null, createdAt);
    }
    public PollenIntent applied(long currentRevision, boolean terminalSeason) {
        if (state != State.RECEIVED) invalid("operation", "receipt must be recorded before local application");
        return new PollenIntent(id, payload, currentRevision == payload.materialRevision && !terminalSeason ? State.APPLIED : State.OBSOLETE,
                receipt, null, createdAt);
    }
    public Optional<com.seasonplan.audit.api.AuditAction> auditAction() {
        if (receipt == null) return Optional.empty();
        return switch (receipt.status()) {
            case CONFIRMED -> Optional.of(com.seasonplan.audit.api.AuditAction.POLLEN_RESERVATION_CONFIRMED);
            case REJECTED -> Optional.of(com.seasonplan.audit.api.AuditAction.POLLEN_RESERVATION_REJECTED);
            case PENDING -> Optional.empty();
        };
    }
    public PollenIntent denied(String field) {
        if (receipt != null || !unresolved()) invalid("operation", "known outcome cannot be replaced with denial");
        return new PollenIntent(id, payload, State.DENIED, null, Objects.requireNonNull(field), createdAt);
    }
    private static void validateReceipt(Payload payload, ReservationReceipt result) {
        if (!payload.planId.equals(result.seasonPlanId()) || (payload.reservationId != null && !payload.reservationId.equals(result.id())))
            invalid("reservation", "provider returned a different agreement identity");
        if (payload.kind == Kind.CONFIRM && result.status() != PollenReservationStatus.CONFIRMED
                || payload.kind == Kind.REJECT && result.status() != PollenReservationStatus.REJECTED)
            invalid("reservation", "provider returned a different decision");
    }
    private static void invalid(String field, String reason) { throw new DomainValidationException(Map.of(field, List.of(reason))); }
}
