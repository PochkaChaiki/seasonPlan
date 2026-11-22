package com.seasonplan.audit.application;

import com.seasonplan.audit.application.port.AuditLogRepository;

import com.seasonplan.audit.api.AuditAction;
import com.seasonplan.audit.api.AuditLogEntry;

import java.time.Clock;
import java.util.List;
import java.util.UUID;

public class AuditLogService implements com.seasonplan.audit.api.AuditTrail {
    private final AuditLogRepository auditLogRepository;
    private final Clock clock;

    public AuditLogService(AuditLogRepository auditLogRepository, Clock clock) {
        this.auditLogRepository = auditLogRepository;
        this.clock = clock;
    }

    public AuditLogEntry append(UUID seasonPlanId, AuditAction action, String actorId, String comment) {
        AuditLogEntry entry = new AuditLogEntry(UUID.randomUUID(), seasonPlanId, action, actorId, clock.instant(), comment);
        return auditLogRepository.append(entry);
    }

    /** Replays preserve the originally appended record, including its timestamp. */
    public AuditLogEntry appendOnce(UUID eventId, UUID seasonPlanId, AuditAction action, String actorId, String comment) {
        return auditLogRepository.append(new AuditLogEntry(eventId, seasonPlanId, action, actorId, clock.instant(), comment));
    }

    public List<AuditLogEntry> entriesFor(UUID seasonPlanId) {
        return auditLogRepository.entriesFor(seasonPlanId);
    }
}
