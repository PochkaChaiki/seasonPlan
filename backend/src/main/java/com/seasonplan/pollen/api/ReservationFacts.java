package com.seasonplan.pollen.api;

import com.seasonplan.pollen.domain.PollenReservationStatus;
import java.util.UUID;

/** Immutable provider reservation view; no local stock authority or mutable provider object is exposed. */
public record ReservationFacts(UUID id, UUID seasonPlanId, int requestedAmount, Integer confirmedAmount,
                               PollenReservationStatus status, String comment, String confirmedBy,
                               Long materialRevision, boolean unresolvedOperation) { }
