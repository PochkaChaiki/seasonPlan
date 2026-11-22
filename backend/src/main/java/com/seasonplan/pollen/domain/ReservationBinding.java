package com.seasonplan.pollen.domain;

import java.util.Objects;
import java.util.UUID;

/** Captured before dispatch, linked to the immutable accepted request; never inferred on provider GET. */
public record ReservationBinding(UUID reservationId, UUID planId, long materialRevision, UUID requestOperationId) {
    public ReservationBinding {
        Objects.requireNonNull(reservationId); Objects.requireNonNull(planId); Objects.requireNonNull(requestOperationId);
        if (materialRevision < 0) throw new IllegalArgumentException("material revision must not be negative");
    }
    public void requireRequestEvidence(PollenIntent intent) {
        if (!requestOperationId.equals(intent.id()) || intent.payload().kind() != PollenIntent.Kind.REQUEST
                || !planId.equals(intent.payload().planId()) || materialRevision != intent.payload().materialRevision()
                || intent.receipt() == null || !reservationId.equals(intent.receipt().id()))
            throw new IllegalStateException("binding must reference matching accepted request evidence");
    }
}
