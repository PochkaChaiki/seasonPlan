package com.seasonplan.lifecycle.service;

import com.seasonplan.lifecycle.domain.PollenReservation;

import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class InMemoryPollenReservationRepository {
    private final Map<UUID, PollenReservation> reservations = new ConcurrentHashMap<>();

    public PollenReservation save(PollenReservation reservation) {
        reservations.put(reservation.id(), reservation);
        return reservation;
    }

    public Optional<PollenReservation> findById(UUID id) {
        return Optional.ofNullable(reservations.get(id));
    }

    public Optional<PollenReservation> findLatestBySeasonPlanId(UUID seasonPlanId) {
        return reservations.values().stream()
                .filter(reservation -> reservation.seasonPlanId().equals(seasonPlanId))
                .reduce((first, second) -> second);
    }

    public Collection<PollenReservation> all() {
        return reservations.values();
    }
}
