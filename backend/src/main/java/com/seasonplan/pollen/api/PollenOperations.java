package com.seasonplan.pollen.api;

import com.seasonplan.pollen.domain.*;
import com.seasonplan.identity.api.ActorRole;
import java.math.BigDecimal;
import java.util.*;

/** Resource commands and provider-independent resource views; not a local stock authority. */
public interface PollenOperations {
    ReservationFacts requestReservation(UUID planId, int amount, ActorRole role, String actorId);
    ReservationFacts requestReservation(UUID planId, UUID requestId, int amount, ActorRole role, String actorId);
    ReservationFacts confirmReservation(UUID id, int amount, String comment, ActorRole role, String actorId);
    ReservationFacts rejectReservation(UUID id, String comment, ActorRole role, String actorId);
    /** Reconcile the original durable intent using safe reads/local application only. */
    ReservationFacts reconcileOperation(UUID operationId, ActorRole role, String actorId);
    ReservationFacts get(UUID id);
    Optional<ReservationFacts> findLatestBySeasonPlanId(UUID id);
    Optional<ReservationFacts> findAuthoritativeConfirmedBySeasonPlanId(UUID id);
    Optional<PollenUsage> usage(UUID id);
    PollenCapacity capacity();
    PollenUsage recordConsumption(UUID planId, UUID eventId, BigDecimal grams, ActorRole role, String actorId);
    PollenUsage reconcileConsumption(UUID operationId, ActorRole role, String actorId);
}
