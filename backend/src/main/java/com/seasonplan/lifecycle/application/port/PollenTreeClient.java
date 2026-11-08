package com.seasonplan.lifecycle.application.port;

import com.seasonplan.lifecycle.domain.PollenCapacity;
import com.seasonplan.lifecycle.domain.PollenReservation;
import com.seasonplan.lifecycle.domain.PollenUsage;
import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

/**
 * Outbound client contract used only behind PollenService, never directly by other use cases.
 * The provider owns stock, reservations and consumption. SeasonPlan never persists reservation state.
 */
public interface PollenTreeClient {
    record Summary(UUID reservationId, BigDecimal confirmedGrams, BigDecimal actualGrams, String version) {
        public Summary {
            PollenUsage usage = new PollenUsage(reservationId, confirmedGrams, actualGrams, version);
            confirmedGrams = usage.confirmedGrams();
            actualGrams = usage.actualGrams();
        }
        public PollenUsage snapshot() { return new PollenUsage(reservationId, confirmedGrams, actualGrams, version); }
    }

    PollenCapacity capacity();
    Optional<PollenReservation> findById(UUID reservationId);
    Optional<PollenReservation> findLatestBySeasonPlanId(UUID seasonPlanId);

    /** Same requestId/payload reuses the external reservation; changed payloads are rejected. */
    PollenReservation requestReservation(UUID seasonPlanId, UUID requestId, int requestedAmount, String actorId);
    /** Identical terminal decisions replay without another allocation; changed payloads are rejected. */
    PollenReservation confirmReservation(UUID reservationId, int confirmedAmount, String comment, String actorId);
    PollenReservation rejectReservation(UUID reservationId, String comment, String actorId);

    Optional<Summary> summary(UUID planId);
    /**
     * Provider deduplicates by (planId, clientEventId), rejects changed retry amounts and returns
     * the ORIGINAL event's post-consumption snapshot, even after subsequent consumption.
     */
    Summary recordConsumption(UUID planId, UUID clientEventId, BigDecimal grams);
}
