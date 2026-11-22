package com.seasonplan.audit.application.port;

import com.seasonplan.audit.api.AuditLogEntry;

import java.util.List;
import java.util.UUID;

public interface AuditLogRepository {
    /** Same event ID/content is idempotent and preserves the original timestamp; changed content must fail. */
    AuditLogEntry append(AuditLogEntry entry);

    List<AuditLogEntry> entriesFor(UUID seasonPlanId);
}
