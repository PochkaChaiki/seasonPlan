package com.seasonplan.lifecycle.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record CorrectionAction(UUID id, UUID seasonPlanId, UUID incidentId, String deduplicationKey,
                               String description, String owner, Instant deadline, Status status, String cancellationReason) {
    public enum Status { OPEN, IN_PROGRESS, DONE, CANCELLED }
    public CorrectionAction {
        Objects.requireNonNull(id); Objects.requireNonNull(seasonPlanId); Objects.requireNonNull(deadline); Objects.requireNonNull(status);
        if (deduplicationKey == null || deduplicationKey.isBlank() || description == null || description.isBlank()
                || owner == null || owner.isBlank()) throw new IllegalArgumentException("action details are required");
        if (status == Status.CANCELLED && (cancellationReason == null || cancellationReason.isBlank())) {
            throw new IllegalArgumentException("cancellation requires a reason");
        }
    }
    public static CorrectionAction open(UUID id, UUID planId, UUID incidentId, String key, String description, String owner, Instant deadline) {
        return new CorrectionAction(id, planId, incidentId, key, description, owner, deadline, Status.OPEN, null);
    }
    public boolean terminal() { return status == Status.DONE || status == Status.CANCELLED; }
    public boolean overdue(Instant now) { return !terminal() && now.isAfter(deadline); }
    public CorrectionAction transition(Status target, String reason) {
        boolean allowed = (status == Status.OPEN && (target == Status.IN_PROGRESS || target == Status.CANCELLED))
                || (status == Status.IN_PROGRESS && (target == Status.OPEN || target == Status.DONE || target == Status.CANCELLED));
        if (!allowed) throw new IllegalArgumentException("forbidden correction transition: " + status + " -> " + target);
        return new CorrectionAction(id, seasonPlanId, incidentId, deduplicationKey, description, owner, deadline, target, reason);
    }
}
