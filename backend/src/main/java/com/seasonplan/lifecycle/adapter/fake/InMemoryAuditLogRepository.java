package com.seasonplan.lifecycle.adapter.fake;

import com.seasonplan.lifecycle.domain.AuditLogEntry;
import com.seasonplan.lifecycle.application.port.AuditLogRepository;

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
            if (!java.util.Objects.equals(existing.seasonPlanId(), entry.seasonPlanId()) || existing.action() != entry.action()
                    || !java.util.Objects.equals(existing.actorId(), entry.actorId())
                    || !java.util.Objects.equals(existing.comment(), entry.comment())) {
                throw new IllegalStateException("audit event identity cannot be reused with different content");
            }
            return existing;
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
