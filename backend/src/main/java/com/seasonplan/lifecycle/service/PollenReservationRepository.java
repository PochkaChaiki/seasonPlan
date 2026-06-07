package com.seasonplan.lifecycle.service;

import com.seasonplan.lifecycle.domain.PollenReservation;

import java.util.Collection;
import java.util.Optional;
import java.util.UUID;

public interface PollenReservationRepository {
    PollenReservation save(PollenReservation reservation);

    Optional<PollenReservation> findById(UUID id);

    Optional<PollenReservation> findLatestBySeasonPlanId(UUID seasonPlanId);

    Collection<PollenReservation> all();
}
