package com.seasonplan.lifecycle.adapter.fake;

import com.seasonplan.lifecycle.application.port.PollenTreeClient;
import com.seasonplan.lifecycle.domain.PollenCapacity;
import com.seasonplan.lifecycle.application.shared.NotFoundException;
import com.seasonplan.lifecycle.domain.DomainValidationException;
import com.seasonplan.lifecycle.domain.PollenReservation;
import com.seasonplan.lifecycle.domain.PollenReservationStatus;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * Deterministic in-memory stand-in for the external Tree, shared across all plans in one instance.
 * Owns simulated capacity, reservation decisions and consumption; never writes application storage.
 */
public class FakePollenTreeClient implements PollenTreeClient {
    private record Request(UUID planId, int amount, String actorId) {}
    private record Requested(Request payload, UUID reservationId) {}
    private record ConsumptionKey(UUID planId, UUID eventId) {}
    private record Consumed(BigDecimal grams, Summary result) {}

    private final BigDecimal capacityGrams;
    private final Map<UUID, PollenReservation> reservations = new LinkedHashMap<>();
    private final Map<UUID, UUID> latestByPlan = new HashMap<>();
    private final Map<UUID, Requested> requests = new HashMap<>();
    private final Map<ConsumptionKey, Consumed> consumption = new HashMap<>();
    private final Map<UUID, BigDecimal> actualByReservation = new HashMap<>();
    private long capacityVersion;
    private long consumptionVersion;

    public FakePollenTreeClient(BigDecimal capacityGrams) {
        Objects.requireNonNull(capacityGrams);
        if (capacityGrams.signum() < 0) throw new IllegalArgumentException("capacity must not be negative");
        this.capacityGrams = capacityGrams;
    }

    @Override
    public synchronized PollenCapacity capacity() {
        BigDecimal reserved = reservations.values().stream()
                .filter(reservation -> reservation.status() == PollenReservationStatus.CONFIRMED)
                .map(reservation -> BigDecimal.valueOf(reservation.confirmedAmount()))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        return new PollenCapacity(capacityGrams.subtract(reserved), "capacity-" + capacityVersion);
    }

    @Override
    public synchronized Optional<PollenReservation> findById(UUID reservationId) {
        return Optional.ofNullable(reservations.get(reservationId)).map(this::snapshot);
    }

    @Override
    public synchronized Optional<PollenReservation> findLatestBySeasonPlanId(UUID seasonPlanId) {
        return findById(latestByPlan.get(seasonPlanId));
    }

    @Override
    public synchronized Optional<PollenReservation> findAuthoritativeConfirmedBySeasonPlanId(UUID seasonPlanId) {
        return reservations.values().stream()
                .filter(reservation -> reservation.seasonPlanId().equals(seasonPlanId)
                        && reservation.status() == PollenReservationStatus.CONFIRMED)
                .reduce((older, newer) -> newer).map(this::snapshot);
    }

    @Override
    public synchronized PollenReservation requestReservation(UUID seasonPlanId, UUID requestId, int requestedAmount, String actorId) {
        Objects.requireNonNull(seasonPlanId);
        Objects.requireNonNull(requestId);
        Request payload = new Request(seasonPlanId, requestedAmount, actorId);
        Requested existing = requests.get(requestId);
        if (existing != null) {
            if (!existing.payload().equals(payload)) throw invalid("requestId", "retry payload must not change");
            return snapshot(reservations.get(existing.reservationId()));
        }
        requireCapacity(requestedAmount, "requestedAmount");
        UUID id = UUID.nameUUIDFromBytes(("pollen-reservation:" + seasonPlanId + ":" + requestId).getBytes(StandardCharsets.UTF_8));
        PollenReservation reservation = new PollenReservation(id, seasonPlanId, requestedAmount);
        reservations.put(id, reservation);
        requests.put(requestId, new Requested(payload, id));
        latestByPlan.put(seasonPlanId, id);
        return snapshot(reservation);
    }

    @Override
    public synchronized PollenReservation confirmReservation(UUID reservationId, int confirmedAmount, String comment, String actorId) {
        PollenReservation reservation = reservations.get(reservationId);
        if (reservation != null && reservation.status() == PollenReservationStatus.CONFIRMED
                && Objects.equals(reservation.confirmedAmount(), confirmedAmount)
                && Objects.equals(reservation.comment(), comment) && Objects.equals(reservation.confirmedBy(), actorId)) {
            return snapshot(reservation);
        }
        reservation = requirePending(reservationId);
        requireCapacity(confirmedAmount, "confirmedAmount");
        reservation.confirm(confirmedAmount, comment, actorId);
        capacityVersion++;
        return snapshot(reservation);
    }

    @Override
    public synchronized PollenReservation rejectReservation(UUID reservationId, String comment, String actorId) {
        PollenReservation reservation = reservations.get(reservationId);
        if (reservation != null && reservation.status() == PollenReservationStatus.REJECTED
                && Objects.equals(reservation.comment(), comment) && Objects.equals(reservation.confirmedBy(), actorId)) {
            return snapshot(reservation);
        }
        reservation = requirePending(reservationId);
        if (comment == null || comment.isBlank()) throw invalid("comment", "rejection reason is required");
        reservation.reject(comment, actorId);
        return snapshot(reservation);
    }

    @Override
    public synchronized Optional<Summary> summary(UUID planId) {
        return findLatestBySeasonPlanId(planId)
                .filter(reservation -> reservation.status() == PollenReservationStatus.CONFIRMED)
                .map(reservation -> new Summary(reservation.id(), BigDecimal.valueOf(reservation.confirmedAmount()),
                        actualByReservation.getOrDefault(reservation.id(), BigDecimal.ZERO), "consumption-" + consumptionVersion));
    }

    @Override
    public synchronized Summary recordConsumption(UUID planId, UUID clientEventId, BigDecimal grams) {
        Objects.requireNonNull(planId);
        Objects.requireNonNull(clientEventId);
        Objects.requireNonNull(grams);
        BigDecimal normalized = grams.setScale(3, RoundingMode.HALF_UP);
        if (normalized.signum() <= 0) throw new IllegalArgumentException("consumption grams must be positive");
        ConsumptionKey key = new ConsumptionKey(planId, clientEventId);
        Consumed existing = consumption.get(key);
        if (existing != null) {
            if (existing.grams().compareTo(normalized) != 0) throw invalid("clientEventId", "retry payload must not change");
            return existing.result();
        }
        Summary current = summary(planId).orElseThrow(() -> invalid("pollenReservation", "confirmed pollen reservation is required"));
        BigDecimal actual = current.actualGrams().add(normalized);
        consumptionVersion++;
        Summary result = new Summary(current.reservationId(), current.confirmedGrams(), actual, "consumption-" + consumptionVersion);
        actualByReservation.put(current.reservationId(), actual);
        consumption.put(key, new Consumed(normalized, result));
        return result;
    }

    private void requireCapacity(int amount, String field) {
        if (amount <= 0) throw invalid(field, "amount must be positive");
        if (BigDecimal.valueOf(amount).compareTo(capacity().availableGrams()) > 0) {
            throw invalid(field, "insufficient Pollen Tree capacity");
        }
    }

    private PollenReservation requirePending(UUID id) {
        PollenReservation reservation = reservations.get(id);
        if (reservation == null) throw new NotFoundException("pollen reservation not found: " + id);
        if (reservation.status() != PollenReservationStatus.PENDING) {
            throw invalid("status", "only PENDING reservations can be confirmed or rejected");
        }
        return reservation;
    }

    private PollenReservation snapshot(PollenReservation reservation) {
        PollenReservation copy = new PollenReservation(reservation.id(), reservation.seasonPlanId(), reservation.requestedAmount());
        if (reservation.status() == PollenReservationStatus.CONFIRMED) {
            copy.confirm(reservation.confirmedAmount(), reservation.comment(), reservation.confirmedBy());
        } else if (reservation.status() == PollenReservationStatus.REJECTED) {
            copy.reject(reservation.comment(), reservation.confirmedBy());
        }
        return copy;
    }

    private DomainValidationException invalid(String field, String message) {
        return new DomainValidationException(Map.of(field, List.of(message)));
    }
}
