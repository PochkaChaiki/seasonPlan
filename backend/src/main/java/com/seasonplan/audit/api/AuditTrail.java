package com.seasonplan.audit.api;

import java.util.List;
import java.util.UUID;

public interface AuditTrail {
    AuditLogEntry append(UUID seasonPlanId, AuditAction action, String actorId, String comment);
    AuditLogEntry appendOnce(UUID eventId, UUID seasonPlanId, AuditAction action, String actorId, String comment);
    List<AuditLogEntry> entriesFor(UUID seasonPlanId);
}
