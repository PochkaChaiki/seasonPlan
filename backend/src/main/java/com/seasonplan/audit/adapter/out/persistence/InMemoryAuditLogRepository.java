package com.seasonplan.audit.adapter.out.persistence;

import com.seasonplan.audit.api.AuditLogEntry;
import com.seasonplan.audit.application.port.AuditLogRepository;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class InMemoryAuditLogRepository implements AuditLogRepository {
    private final Map<UUID, List<AuditLogEntry>> entriesBySeasonPlan = new ConcurrentHashMap<>();

    private final Map<UUID, AuditLogEntry> entriesById = new ConcurrentHashMap<>();

    @Override
    public synchronized AuditLogEntry append(AuditLogEntry entry) {
        AuditLogEntry existing = entriesById.get(entry.id());
        if (existing != null) {
            return com.seasonplan.audit.domain.AuditReplayIdentity.preserveOriginal(existing, entry);
        }
        entriesBySeasonPlan.computeIfAbsent(entry.seasonPlanId(), ignored -> new ArrayList<>()).add(entry);
        entriesById.put(entry.id(), entry);
        return entry;
    }

    @Override
    public synchronized List<AuditLogEntry> entriesFor(UUID seasonPlanId) {
        return List.copyOf(entriesBySeasonPlan.getOrDefault(seasonPlanId, List.of()));
    }
}
