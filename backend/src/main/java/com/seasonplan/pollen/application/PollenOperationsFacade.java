package com.seasonplan.pollen.application;

import com.seasonplan.pollen.api.*;
import com.seasonplan.pollen.domain.*;
import com.seasonplan.identity.api.ActorRole;
import java.math.BigDecimal;
import java.util.*;

/** Isolates immutable public inputs and snapshots command results directly, without rereading state. */
public final class PollenOperationsFacade implements PollenOperations {
    private final PollenService service;
    public PollenOperationsFacade(PollenService service) { this.service = service; }
    private ReservationFacts facts(PollenReservation value) { return new ReservationFacts(value.id(), value.seasonPlanId(), value.requestedAmount(), value.confirmedAmount(), value.status(), value.comment(), value.confirmedBy(), service.bindingRevision(value.id(), value.seasonPlanId()), service.unresolved(value.seasonPlanId())); }
    @Override public ReservationFacts requestReservation(UUID planId, int amount, ActorRole role, String actorId) { return facts(service.requestReservation(planId, amount, role, actorId)); }
    @Override public ReservationFacts requestReservation(UUID planId, UUID requestId, int amount, ActorRole role, String actorId) { return facts(service.requestReservation(planId, requestId, amount, role, actorId)); }
    @Override public ReservationFacts confirmReservation(UUID id, int amount, String comment, ActorRole role, String actorId) { return facts(service.confirmReservation(id, amount, comment, role, actorId)); }
    @Override public ReservationFacts rejectReservation(UUID id, String comment, ActorRole role, String actorId) { return facts(service.rejectReservation(id, comment, role, actorId)); }
    @Override public ReservationFacts reconcileOperation(UUID operationId, ActorRole role, String actorId) { return facts(service.reconcileOperation(operationId, role, actorId)); }
    @Override public ReservationFacts get(UUID id) { return facts(service.get(id)); }
    @Override public Optional<ReservationFacts> findLatestBySeasonPlanId(UUID id) { return service.findLatestBySeasonPlanId(id).map(this::facts); }
    @Override public Optional<ReservationFacts> findAuthoritativeConfirmedBySeasonPlanId(UUID id) { return service.findAuthoritativeConfirmedBySeasonPlanId(id).map(this::facts); }
    @Override public Optional<PollenUsage> usage(UUID id) { return service.usage(id); }
    @Override public PollenCapacity capacity() { return service.capacity(); }
    @Override public PollenUsage recordConsumption(UUID planId, UUID eventId, BigDecimal grams, ActorRole role, String actorId) { return service.recordConsumption(planId, eventId, grams, role, actorId); }
    @Override public PollenUsage reconcileConsumption(UUID operationId, ActorRole role, String actorId) { return service.reconcileConsumption(operationId, role, actorId); }
}
