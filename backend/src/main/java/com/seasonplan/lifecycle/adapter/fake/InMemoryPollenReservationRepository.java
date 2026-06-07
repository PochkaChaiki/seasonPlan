package com.seasonplan.lifecycle.adapter.fake;

import com.seasonplan.lifecycle.domain.PollenReservation;
import com.seasonplan.lifecycle.service.PollenReservationRepository;

import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class InMemoryPollenReservationRepository implements PollenReservationRepository {
    private final Map<UUID, PollenReservation> reservations = new ConcurrentHashMap<>();

    @Override
    public PollenReservation save(PollenReservation reservation) {
        reservations.put(reservation.id(), reservation);
        return reservation;
    }

    @Override
    public Optional<PollenReservation> findById(UUID id) {
        return Optional.ofNullable(reservations.get(id));
    }

    @Override
    public Optional<PollenReservation> findLatestBySeasonPlanId(UUID seasonPlanId) {
        return reservations.values().stream()
                .filter(reservation -> reservation.seasonPlanId().equals(seasonPlanId))
                .reduce((first, second) -> second);
    }

    @Override
    public Collection<PollenReservation> all() {
        return reservations.values();
    }
}
