package com.seasonplan.lifecycle.service;

import com.seasonplan.lifecycle.domain.AuditAction;
import com.seasonplan.lifecycle.domain.AuditLogEntry;

import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class AuditLogService {
    private final Map<UUID, List<AuditLogEntry>> entriesBySeasonPlan = new ConcurrentHashMap<>();
    private final Clock clock;

    public AuditLogService(Clock clock) {
        this.clock = clock;
    }

    public AuditLogEntry append(UUID seasonPlanId, AuditAction action, String actorId, String comment) {
        AuditLogEntry entry = new AuditLogEntry(UUID.randomUUID(), seasonPlanId, action, actorId, clock.instant(), comment);
        entriesBySeasonPlan.computeIfAbsent(seasonPlanId, ignored -> new ArrayList<>()).add(entry);
        return entry;
    }

    public List<AuditLogEntry> entriesFor(UUID seasonPlanId) {
        return List.copyOf(entriesBySeasonPlan.getOrDefault(seasonPlanId, List.of()));
    }
}
