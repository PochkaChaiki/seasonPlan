package com.seasonplan.pollen.application.port;

import com.seasonplan.pollen.domain.*;
import java.util.Optional;
import java.util.UUID;

public interface PollenIntentRepository {
    record ConsumptionClaim(boolean inserted, ConsumptionIntent value) { }
    ConsumptionClaim insertConsumption(ConsumptionIntent intent);
    Optional<ConsumptionIntent> findConsumption(UUID id);
    void updateConsumption(ConsumptionIntent intent);
    Optional<UUID> unresolvedConsumption(UUID planId);
    record Claim(boolean inserted, PollenIntent value) { }
    Claim insert(PollenIntent intent);
    Optional<PollenIntent> find(UUID id);
    void update(PollenIntent intent);
    Optional<ReservationBinding> binding(UUID reservationId);
    void bind(ReservationBinding binding);
    Optional<UUID> unresolvedOperation(UUID planId);
    default boolean hasUnresolved(UUID planId) { return unresolvedOperation(planId).isPresent(); }
}
