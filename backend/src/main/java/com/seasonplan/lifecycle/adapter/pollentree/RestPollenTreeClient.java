package com.seasonplan.lifecycle.adapter.pollentree;

import com.seasonplan.lifecycle.application.port.PollenTreeClient;
import com.seasonplan.lifecycle.application.shared.NotFoundException;
import com.seasonplan.lifecycle.application.shared.PollenTreeUnavailableException;
import com.seasonplan.lifecycle.domain.DomainValidationException;
import com.seasonplan.lifecycle.domain.PollenCapacity;
import com.seasonplan.lifecycle.domain.PollenReservation;
import com.seasonplan.lifecycle.domain.PollenReservationStatus;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/** Proposed Tree JSON contract, not a verified provider API. No local authority, fallback or command retries. */
public final class RestPollenTreeClient implements PollenTreeClient {
    private final RestClient client;

    public RestPollenTreeClient(RestClient client) {
        this.client = Objects.requireNonNull(client);
    }

    @Override
    public PollenCapacity capacity() {
        CapacityResponse response = exchange(HttpMethod.GET, "/capacity", null, CapacityResponse.class, false, null);
        require(response != null && response.availableGrams() != null && response.availableGrams().signum() >= 0
                && nonblank(response.version()));
        return new PollenCapacity(response.availableGrams(), response.version());
    }

    @Override
    public Optional<PollenReservation> findById(UUID reservationId) {
        ReservationResponse response = exchange(HttpMethod.GET, "/reservations/" + reservationId, null,
                ReservationResponse.class, true, null);
        if (response == null) return Optional.empty();
        PollenReservation result = reservation(response);
        require(result.id().equals(reservationId));
        return Optional.of(result);
    }

    @Override
    public Optional<PollenReservation> findLatestBySeasonPlanId(UUID seasonPlanId) {
        ReservationResponse response = exchange(HttpMethod.GET, "/season-plans/" + seasonPlanId + "/reservations/latest", null,
                ReservationResponse.class, true, null);
        if (response == null) return Optional.empty();
        PollenReservation result = reservation(response);
        require(result.seasonPlanId().equals(seasonPlanId));
        return Optional.of(result);
    }

    @Override
    public Optional<PollenReservation> findAuthoritativeConfirmedBySeasonPlanId(UUID seasonPlanId) {
        // Proposed provider contract: 200 with authoritativeKnown=true and nullable reservation; 404 is unavailable.
        AuthorityResponse response = exchange(HttpMethod.GET, "/season-plans/" + seasonPlanId + "/reservations/authoritative", null,
                AuthorityResponse.class, false, null);
        require(response != null && Boolean.TRUE.equals(response.authoritativeKnown()));
        if (response.reservation() == null) return Optional.empty();
        PollenReservation result = reservation(response.reservation());
        require(result.seasonPlanId().equals(seasonPlanId) && result.status() == PollenReservationStatus.CONFIRMED);
        return Optional.of(result);
    }

    @Override
    public PollenReservation requestReservation(UUID seasonPlanId, UUID requestId, int requestedAmount, String actorId) {
        ReservationResponse response = exchange(HttpMethod.POST, "/reservations",
                new ReservationRequest(seasonPlanId, requestId, requestedAmount, actorId), ReservationResponse.class, false, "requestedAmount");
        PollenReservation result = reservation(response);
        require(result.seasonPlanId().equals(seasonPlanId) && requestId.equals(response.requestId())
                && result.requestedAmount() == requestedAmount);
        // A stable request retry may return the reservation's now-terminal state.
        return result;
    }

    @Override
    public PollenReservation confirmReservation(UUID reservationId, int confirmedAmount, String comment, String actorId) {
        PollenReservation before = decisionSource(reservationId, PollenReservationStatus.CONFIRMED, confirmedAmount, comment, actorId);
        ReservationResponse response = exchange(HttpMethod.POST, "/reservations/" + reservationId + "/confirm",
                new ConfirmationRequest(confirmedAmount, comment, actorId), ReservationResponse.class, false, "confirmedAmount");
        PollenReservation result = decision(response, before, PollenReservationStatus.CONFIRMED, comment, actorId);
        require(result.confirmedAmount() == confirmedAmount);
        return result;
    }

    @Override
    public PollenReservation rejectReservation(UUID reservationId, String comment, String actorId) {
        PollenReservation before = decisionSource(reservationId, PollenReservationStatus.REJECTED, null, comment, actorId);
        ReservationResponse response = exchange(HttpMethod.POST, "/reservations/" + reservationId + "/reject",
                new RejectionRequest(comment, actorId), ReservationResponse.class, false, "comment");
        return decision(response, before, PollenReservationStatus.REJECTED, comment, actorId);
    }

    @Override
    public Optional<Summary> summary(UUID planId) {
        UsageResponse response = exchange(HttpMethod.GET, "/season-plans/" + planId + "/usage", null,
                UsageResponse.class, true, null);
        return response == null ? Optional.empty() : Optional.of(summary(response, planId));
    }

    @Override
    public Summary recordConsumption(UUID planId, UUID clientEventId, BigDecimal grams) {
        BigDecimal normalized = Objects.requireNonNull(grams).setScale(3, RoundingMode.HALF_UP);
        if (normalized.signum() <= 0) throw new IllegalArgumentException("consumption grams must be positive");
        UsageResponse response = exchange(HttpMethod.POST, "/season-plans/" + planId + "/consumptions",
                new ConsumptionRequest(clientEventId, normalized), UsageResponse.class, false, "grams");
        Summary result = summary(response, planId);
        require(clientEventId.equals(response.clientEventId()) && result.actualGrams().compareTo(normalized) >= 0);
        // The provider must return the original event snapshot on retry, not its current aggregate.
        return result;
    }

    private PollenReservation decisionSource(UUID id, PollenReservationStatus status, Integer confirmedAmount,
                                             String comment, String actorId) {
        PollenReservation result = findById(id).orElseThrow(() -> new NotFoundException("pollen reservation not found"));
        if (result.status() == PollenReservationStatus.PENDING) return result;
        // Exact terminal replay repairs local completion after an external success; never invent a new decision.
        if (result.status() != status || !Objects.equals(result.confirmedAmount(), confirmedAmount)
                || !Objects.equals(result.comment(), comment) || !Objects.equals(result.confirmedBy(), actorId)) {
            throw invalid("status", "a completed Pollen Tree decision must not change");
        }
        return result;
    }

    private PollenReservation decision(ReservationResponse response, PollenReservation before,
                                       PollenReservationStatus status, String comment, String actorId) {
        PollenReservation result = reservation(response);
        require(result.id().equals(before.id()) && result.seasonPlanId().equals(before.seasonPlanId())
                && result.requestedAmount() == before.requestedAmount() && result.status() == status
                && Objects.equals(result.comment(), comment) && Objects.equals(result.confirmedBy(), actorId));
        return result;
    }

    private PollenReservation reservation(ReservationResponse response) {
        require(response != null && response.id() != null && response.seasonPlanId() != null && response.status() != null);
        PollenReservation result = new PollenReservation(response.id(), response.seasonPlanId(), integerGrams(response.requestedAmount()));
        switch (response.status()) {
            case "DRAFT", "PENDING" -> require(response.confirmedAmount() == null
                    && response.comment() == null && response.confirmedBy() == null);
            case "CONFIRMED" -> {
                require(nonblank(response.confirmedBy()));
                result.confirm(integerGrams(response.confirmedAmount()), response.comment(), response.confirmedBy());
            }
            case "REJECTED" -> {
                require(response.confirmedAmount() == null && nonblank(response.comment()) && nonblank(response.confirmedBy()));
                result.reject(response.comment(), response.confirmedBy());
            }
            default -> throw unavailable();
        }
        return result;
    }

    private int integerGrams(BigDecimal amount) {
        require(amount != null && amount.signum() > 0);
        try {
            return amount.intValueExact();
        } catch (ArithmeticException error) {
            throw unavailable();
        }
    }

    private Summary summary(UsageResponse response, UUID planId) {
        require(response != null && planId.equals(response.seasonPlanId()) && response.reservationId() != null
                && response.confirmedGrams() != null && response.actualGrams() != null && nonblank(response.version()));
        try {
            return new Summary(response.reservationId(), response.confirmedGrams(), response.actualGrams(), response.version());
        } catch (IllegalArgumentException error) {
            throw unavailable();
        }
    }

    private <T> T exchange(HttpMethod method, String path, Object body, Class<T> type, boolean lookup, String denialField) {
        try {
            RestClient.RequestBodySpec request = client.method(method).uri(path).accept(MediaType.APPLICATION_JSON);
            if (body != null) request.contentType(MediaType.APPLICATION_JSON).body(body);
            return request.exchange((outbound, response) -> {
                int status = response.getStatusCode().value();
                if (status == 404) {
                    if (lookup) return null;
                    if (denialField != null) throw new NotFoundException("Pollen Tree resource not found");
                }
                if (denialField != null && (status == 400 || status == 409 || status == 422)) {
                    throw invalid(denialField, "Pollen Tree rejected the operation");
                }
                require(status == 200 || (method == HttpMethod.POST && status == 201));
                T result = response.bodyTo(type);
                require(result != null);
                return result;
            });
        } catch (RestClientException error) {
            // Do not retain response bodies or credential-bearing transport diagnostics.
            throw unavailable();
        }
    }

    private static void require(boolean valid) {
        if (!valid) throw unavailable();
    }

    private static boolean nonblank(String value) {
        return value != null && !value.isBlank();
    }

    private static PollenTreeUnavailableException unavailable() {
        return new PollenTreeUnavailableException("Pollen Tree is unavailable");
    }

    private static DomainValidationException invalid(String field, String message) {
        return new DomainValidationException(Map.of(field, List.of(message)));
    }

    public record AuthorityResponse(Boolean authoritativeKnown, ReservationResponse reservation) {}
    public record ReservationRequest(UUID seasonPlanId, UUID requestId, int requestedAmount, String actorId) {}
    public record ConfirmationRequest(int confirmedAmount, String comment, String actorId) {}
    public record RejectionRequest(String comment, String actorId) {}
    public record ConsumptionRequest(UUID clientEventId, BigDecimal grams) {}
    public record CapacityResponse(BigDecimal availableGrams, String version) {}
    public record ReservationResponse(UUID id, UUID seasonPlanId, UUID requestId, BigDecimal requestedAmount,
                                      BigDecimal confirmedAmount, String status, String comment, String confirmedBy) {}
    public record UsageResponse(UUID seasonPlanId, UUID reservationId, BigDecimal confirmedGrams,
                                BigDecimal actualGrams, String version, UUID clientEventId) {}
}
