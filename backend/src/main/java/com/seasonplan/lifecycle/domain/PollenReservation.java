package com.seasonplan.lifecycle.domain;

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
        this.id = id;
        this.seasonPlanId = seasonPlanId;
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

    public void confirm(int confirmedAmount, String comment, String confirmedBy) {
        this.confirmedAmount = confirmedAmount;
        this.comment = comment;
        this.confirmedBy = confirmedBy;
        this.status = PollenReservationStatus.CONFIRMED;
    }

    public void reject(String comment, String confirmedBy) {
        this.comment = comment;
        this.confirmedBy = confirmedBy;
        this.status = PollenReservationStatus.REJECTED;
    }
}
