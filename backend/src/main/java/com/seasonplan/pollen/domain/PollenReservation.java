package com.seasonplan.pollen.domain;

import java.util.UUID;

public class PollenReservation {
    private UUID id;
    private UUID seasonPlanId;
    private int requestedAmount;
    private Integer confirmedAmount;
    private PollenReservationStatus status;
    private String comment;
    private String confirmedBy;

    public PollenReservation() {
    }

    public PollenReservation(UUID id, UUID seasonPlanId, int requestedAmount) {
        this.id = java.util.Objects.requireNonNull(id);
        this.seasonPlanId = java.util.Objects.requireNonNull(seasonPlanId);
        PollenConsumptionPolicy.requirePositive(requestedAmount, "requestedAmount");
        this.requestedAmount = requestedAmount;
        this.status = PollenReservationStatus.PENDING;
    }

    public UUID id() {
        return id;
    }

    public UUID seasonPlanId() {
        return seasonPlanId;
    }

    public int requestedAmount() {
        return requestedAmount;
    }

    public Integer confirmedAmount() {
        return confirmedAmount;
    }

    public PollenReservationStatus status() {
        return status;
    }

    public String comment() {
        return comment;
    }

    public String confirmedBy() {
        return confirmedBy;
    }

    /** True only for a pending decision; exact terminal replay is accepted without mutation. */
    public boolean requiresDecision(PollenReservationStatus target, Integer amount, String reason, String actor) {
        if (id == null || seasonPlanId == null || status == null) rejectField("reservation", "reservation identity is required");
        if (target == PollenReservationStatus.CONFIRMED) {
            if (amount == null) rejectField("confirmedAmount", "amount must be positive");
            PollenConsumptionPolicy.requirePositive(amount, "confirmedAmount");
        } else if (target == PollenReservationStatus.REJECTED) {
            if (reason == null || reason.isBlank()) rejectField("comment", "rejection reason is required");
        } else {
            rejectField("status", "only confirmation or rejection is a decision");
        }
        if (actor == null || actor.isBlank()) rejectField("confirmedBy", "decision maker is required");
        if (status == PollenReservationStatus.PENDING) return true;
        if (status == target && java.util.Objects.equals(confirmedAmount, amount)
                && java.util.Objects.equals(comment, reason) && java.util.Objects.equals(confirmedBy, actor)) return false;
        rejectField("status", "a completed Pollen Tree decision must not change");
        return false;
    }

    public void confirm(int confirmedAmount, String comment, String confirmedBy) {
        if (!requiresDecision(PollenReservationStatus.CONFIRMED, confirmedAmount, comment, confirmedBy)) return;
        this.confirmedAmount = confirmedAmount;
        this.comment = comment;
        this.confirmedBy = confirmedBy;
        this.status = PollenReservationStatus.CONFIRMED;
    }

    public void reject(String comment, String confirmedBy) {
        if (!requiresDecision(PollenReservationStatus.REJECTED, null, comment, confirmedBy)) return;
        this.confirmedAmount = null;
        this.comment = comment;
        this.confirmedBy = confirmedBy;
        this.status = PollenReservationStatus.REJECTED;
    }

    private static void rejectField(String field, String message) {
        throw new com.seasonplan.shared.domain.DomainValidationException(java.util.Map.of(field, java.util.List.of(message)));
    }
}
