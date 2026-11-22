package com.seasonplan.pollen.adapter.out.fake;

import com.seasonplan.pollen.application.port.PollenIntentRepository;
import com.seasonplan.pollen.domain.*;
import java.util.*;

/** Deterministic test store. Production uses PostgreSQL; records contain no mutable owner aliases. */
public final class InMemoryPollenIntentRepository implements PollenIntentRepository {
    private final Map<UUID, ConsumptionIntent> consumption = new LinkedHashMap<>();
    @Override public synchronized ConsumptionClaim insertConsumption(ConsumptionIntent intent) {
        var old = consumption.putIfAbsent(intent.id(), intent);
        return new ConsumptionClaim(old == null, old == null ? intent : old);
    }
    @Override public synchronized Optional<ConsumptionIntent> findConsumption(UUID id) { return Optional.ofNullable(consumption.get(id)); }
    @Override public synchronized void updateConsumption(ConsumptionIntent value) {
        var old = Objects.requireNonNull(consumption.get(value.id()));
        old.requireSame(value.planId(), value.amount(), value.actorId());
        if (old.receipt() != null && !old.receipt().equals(value.receipt())) throw new IllegalStateException("known receipt changed");
        consumption.put(value.id(), value);
    }
    @Override public synchronized Optional<UUID> unresolvedConsumption(UUID planId) {
        return consumption.values().stream().filter(i -> i.planId().equals(planId) && i.unresolved()).map(ConsumptionIntent::id).findFirst();
    }
    private final Map<UUID, PollenIntent> intents = new LinkedHashMap<>();
    private final Map<UUID, ReservationBinding> bindings = new HashMap<>();
    @Override public synchronized Claim insert(PollenIntent intent) {
        PollenIntent old = intents.putIfAbsent(intent.id(), intent);
        return new Claim(old == null, old == null ? intent : old);
    }
    @Override public synchronized Optional<PollenIntent> find(UUID id) { return Optional.ofNullable(intents.get(id)); }
    @Override public synchronized void update(PollenIntent intent) {
        var old = Objects.requireNonNull(intents.get(intent.id()));
        if (!old.payload().equals(intent.payload()) || !old.createdAt().equals(intent.createdAt())
                || old.receipt() != null && !old.receipt().equals(intent.receipt())) throw new IllegalStateException("immutable pollen intent changed");
        intents.put(intent.id(), intent);
    }
    @Override public synchronized Optional<ReservationBinding> binding(UUID id) { return Optional.ofNullable(bindings.get(id)); }
    @Override public synchronized void bind(ReservationBinding binding) {
        binding.requireRequestEvidence(find(binding.requestOperationId()).orElseThrow());
        var old = bindings.putIfAbsent(binding.reservationId(), binding);
        if (old != null && !old.equals(binding)) throw new IllegalStateException("reservation binding cannot change");
    }
    @Override public synchronized Optional<UUID> unresolvedOperation(UUID planId) {
        return intents.values().stream().filter(intent -> intent.payload().planId().equals(planId) && intent.unresolved()).map(PollenIntent::id).findFirst();
    }
}
