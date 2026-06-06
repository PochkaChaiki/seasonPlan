package com.seasonplan.lifecycle.domain;

import java.time.Instant;
import java.util.UUID;

public record AuditLogEntry(
        UUID id,
        UUID seasonPlanId,
        AuditAction action,
        String actorId,
        Instant createdAt,
        String comment
) {
}
