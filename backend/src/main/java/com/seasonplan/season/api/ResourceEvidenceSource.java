package com.seasonplan.season.api;

import java.util.Optional;
import java.util.UUID;

/** Season-owned read port; workflows obtain external resource facts through the resource owner's API. */
public interface ResourceEvidenceSource {
    enum Status { PENDING, CONFIRMED, REJECTED }
    record Reservation(UUID id, UUID seasonPlanId, Status status, String comment, Long materialRevision, boolean unresolvedOperation) { }
    Optional<Reservation> latestFor(UUID planId);
    Optional<Reservation> confirmedFor(UUID planId);
}
