package com.seasonplan.lifecycle.application.port;

import com.seasonplan.lifecycle.domain.AuditLogEntry;

import java.util.List;
import java.util.UUID;

public interface AuditLogRepository {
    /** Same event ID/content is idempotent and preserves the original timestamp; changed content must fail. */
    AuditLogEntry append(AuditLogEntry entry);

    List<AuditLogEntry> entriesFor(UUID seasonPlanId);
}
