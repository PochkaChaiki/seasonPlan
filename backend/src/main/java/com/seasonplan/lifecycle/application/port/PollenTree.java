package com.seasonplan.lifecycle.application.port;

import com.seasonplan.lifecycle.domain.PollenUsage;
import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

/** Sole authority for actual consumption; the application must never write a local pollen table. */
public interface PollenTree {
    record Summary(UUID reservationId, BigDecimal confirmedGrams, BigDecimal actualGrams, String version) {
        public Summary {
            PollenUsage usage = new PollenUsage(reservationId, confirmedGrams, actualGrams, version);
            confirmedGrams = usage.confirmedGrams(); actualGrams = usage.actualGrams();
        }
        public PollenUsage snapshot() { return new PollenUsage(reservationId, confirmedGrams, actualGrams, version); }
    }
    Optional<Summary> summary(UUID planId);
    /**
     * The external operation MUST be idempotent by (planId, clientEventId), reject changed retry payloads,
     * and return the original event's post-consumption snapshot on retry. Network timeouts may be retried.
     */
    Summary recordConsumption(UUID planId, UUID clientEventId, BigDecimal grams);
}
