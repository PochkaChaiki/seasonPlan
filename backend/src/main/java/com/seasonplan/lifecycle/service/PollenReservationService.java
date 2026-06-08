package com.seasonplan.lifecycle.service;

import com.seasonplan.lifecycle.domain.ActorRole;
import com.seasonplan.lifecycle.domain.AuditAction;
import com.seasonplan.lifecycle.domain.DomainValidationException;
import com.seasonplan.lifecycle.domain.PollenReservation;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.UUID;

public class PollenReservationService {
    private static final int MAX_CONFIRMABLE_AMOUNT = 1_000;

    private final PollenReservationRepository reservations;
    private final SeasonPlanRepository plans;
    private final AuditLogService auditLogService;

    public PollenReservationService(PollenReservationRepository reservations, SeasonPlanRepository plans, AuditLogService auditLogService) {
        this.reservations = reservations;
        this.plans = plans;
        this.auditLogService = auditLogService;
    }

    public PollenReservation requestReservation(UUID seasonPlanId, int requestedAmount, ActorRole actorRole, String actorId) {
        ValidationSupport.requireRole(actorRole, ActorRole.POLLEN_GUARDIAN);
        plans.findById(seasonPlanId).orElseThrow(() -> new NotFoundException("season plan not found: " + seasonPlanId));
        if (requestedAmount <= 0) {
            throw new DomainValidationException(new LinkedHashMap<>() {{
                put("requestedAmount", List.of("requested amount must be positive"));
            }});
        }
        return reservations.save(new PollenReservation(UUID.randomUUID(), seasonPlanId, requestedAmount));
    }

    public PollenReservation confirmReservation(UUID id, int confirmedAmount, String comment, ActorRole actorRole, String actorId) {
        ValidationSupport.requireRole(actorRole, ActorRole.POLLEN_GUARDIAN);
        PollenReservation reservation = get(id);
        validateLimits(confirmedAmount);
        reservation.confirm(confirmedAmount, comment, actorId);
        auditLogService.append(reservation.seasonPlanId(), AuditAction.POLLEN_RESERVATION_CONFIRMED, actorId, comment);
        return reservations.save(reservation);
    }

    public PollenReservation rejectReservation(UUID id, String comment, ActorRole actorRole, String actorId) {
        ValidationSupport.requireRole(actorRole, ActorRole.POLLEN_GUARDIAN);
        PollenReservation reservation = get(id);
        reservation.reject(comment, actorId);
        auditLogService.append(reservation.seasonPlanId(), AuditAction.POLLEN_RESERVATION_REJECTED, actorId, comment);
        return reservations.save(reservation);
    }

    public void validateLimits(int confirmedAmount) {
        if (confirmedAmount <= 0 || confirmedAmount > MAX_CONFIRMABLE_AMOUNT) {
            throw new DomainValidationException(new LinkedHashMap<>() {{
                put("confirmedAmount", List.of("confirmed amount must be between 1 and " + MAX_CONFIRMABLE_AMOUNT));
            }});
        }
    }

    public PollenReservation get(UUID id) {
        return reservations.findById(id).orElseThrow(() -> new NotFoundException("pollen reservation not found: " + id));
    }
}
