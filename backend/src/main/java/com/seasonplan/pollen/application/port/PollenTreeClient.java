package com.seasonplan.pollen.application.port;

import com.seasonplan.pollen.domain.PollenCapacity;
import com.seasonplan.pollen.domain.PollenReservation;
import com.seasonplan.pollen.domain.PollenUsage;
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

    /** Latest non-superseded confirmed record; rejected replacements do not supersede it. */
    Optional<PollenReservation> findAuthoritativeConfirmedBySeasonPlanId(UUID seasonPlanId);

    /** Optional read-only correlation lookup. Empty means UNKNOWN, never permission to repeat a mutation. */
    default Optional<PollenReservation> lookupRequest(UUID providerRequestId) { return Optional.empty(); }

    /** Same requestId/payload reuses the external reservation; changed payloads are rejected. */
    PollenReservation requestReservation(UUID seasonPlanId, UUID requestId, int requestedAmount, String actorId);
    /** Identical terminal decisions replay without another allocation; changed payloads are rejected. */
    PollenReservation confirmReservation(UUID reservationId, int confirmedAmount, String comment, String actorId);
    PollenReservation rejectReservation(UUID reservationId, String comment, String actorId);

    enum EventLookupStatus { ACCEPTED, ABSENT, UNKNOWN }
    record EventLookup(EventLookupStatus status, com.seasonplan.pollen.domain.ConsumptionIntent.Receipt receipt) {
        public EventLookup {
            if ((status == EventLookupStatus.ACCEPTED) != (receipt != null)) throw new IllegalArgumentException("lookup evidence mismatch");
        }
    }
    /** Exact event only. ABSENT is never permission to redispatch an existing journal intent. */
    default EventLookup lookupConsumption(UUID planId, UUID clientEventId) {
        return new EventLookup(EventLookupStatus.UNKNOWN, null);
    }
    Optional<Summary> summary(UUID planId);
    /**
     * Provider deduplicates by (planId, clientEventId), rejects changed retry amounts and returns
     * the ORIGINAL event's post-consumption snapshot, even after subsequent consumption.
     */
    Summary recordConsumption(UUID planId, UUID clientEventId, BigDecimal grams);
}
