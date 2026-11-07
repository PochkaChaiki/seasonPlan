package com.seasonplan.lifecycle.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record MagicIncident(UUID id, UUID seasonPlanId, Type type, Severity severity, Status status,
                            String cause, String consequences, String openedBy, Instant openedAt) {
    public enum Type { RITUAL, POLLEN, CLIMATE, ASSIGNMENT, SECURITY }
    public enum Severity { LOW, MEDIUM, HIGH, CRITICAL }
    public enum Status { OPEN, IN_PROGRESS, RESOLVED, CLOSED }
    public MagicIncident {
        Objects.requireNonNull(id); Objects.requireNonNull(seasonPlanId); Objects.requireNonNull(type);
        Objects.requireNonNull(severity); Objects.requireNonNull(status); Objects.requireNonNull(openedAt);
        if (cause == null || cause.isBlank() || consequences == null || consequences.isBlank()
                || openedBy == null || openedBy.isBlank()) throw new IllegalArgumentException("incident details are required");
    }
    public boolean operational() { return status == Status.OPEN || status == Status.IN_PROGRESS; }
    public boolean critical() { return operational() && severity == Severity.CRITICAL; }
    public MagicIncident resolve() {
        if (!operational()) throw new IllegalArgumentException("only operational incidents can be resolved");
        return withStatus(Status.RESOLVED);
    }
    public MagicIncident reopen() {
        if (operational()) throw new IllegalArgumentException("incident is already operational");
        return withStatus(Status.OPEN);
    }
    public MagicIncident withSeverity(Severity value) {
        return new MagicIncident(id, seasonPlanId, type, value, status, cause, consequences, openedBy, openedAt);
    }
    private MagicIncident withStatus(Status value) {
        return new MagicIncident(id, seasonPlanId, type, severity, value, cause, consequences, openedBy, openedAt);
    }
}
