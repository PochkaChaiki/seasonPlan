package com.seasonplan.workflows.application;

import com.seasonplan.pollen.api.PollenOperations;
import com.seasonplan.pollen.api.ReservationFacts;
import com.seasonplan.season.api.ResourceEvidenceSource;
import java.util.Optional;
import java.util.UUID;

/** Collects facts only. Resource acceptance and rejection decisions remain in the season domain. */
public final class PollenResourceEvidenceSource implements ResourceEvidenceSource {
    private final PollenOperations pollen;
    public PollenResourceEvidenceSource(PollenOperations pollen) { this.pollen = pollen; }
    @Override public Optional<Reservation> latestFor(UUID planId) { return pollen.findLatestBySeasonPlanId(planId).map(this::facts); }
    @Override public Optional<Reservation> confirmedFor(UUID planId) { return pollen.findAuthoritativeConfirmedBySeasonPlanId(planId).map(this::facts); }
    private Reservation facts(ReservationFacts source) {
        return new Reservation(source.id(), source.seasonPlanId(), source.status() == null ? null : Status.valueOf(source.status().name()), source.comment(), source.materialRevision(), source.unresolvedOperation());
    }
}
