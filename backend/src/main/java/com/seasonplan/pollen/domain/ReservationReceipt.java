package com.seasonplan.pollen.domain;

import java.util.Objects;
import java.util.UUID;

/** Immutable provider agreement evidence, not an independently authoritative stock balance. */
public record ReservationReceipt(UUID id, UUID seasonPlanId, int requestedAmount, Integer confirmedAmount,
                                 PollenReservationStatus status, String comment, String confirmedBy) {
    public ReservationReceipt {
        Objects.requireNonNull(status);
        PollenReservation value = new PollenReservation(id, seasonPlanId, requestedAmount);
        switch (status) {
            case CONFIRMED -> value.confirm(Objects.requireNonNull(confirmedAmount), comment, confirmedBy);
            case REJECTED -> value.reject(comment, confirmedBy);
            case PENDING -> {
                if (confirmedAmount != null || comment != null || confirmedBy != null) throw new IllegalArgumentException("invalid pending receipt");
            }
        }
    }
    public static ReservationReceipt copyOf(PollenReservation value) {
        return new ReservationReceipt(value.id(), value.seasonPlanId(), value.requestedAmount(), value.confirmedAmount(), value.status(), value.comment(), value.confirmedBy());
    }
    public PollenReservation snapshot() {
        var value = new PollenReservation(id, seasonPlanId, requestedAmount);
        if (status == PollenReservationStatus.CONFIRMED) value.confirm(confirmedAmount, comment, confirmedBy);
        else if (status == PollenReservationStatus.REJECTED) value.reject(comment, confirmedBy);
        return value;
    }
}
