package com.seasonplan.lifecycle.adapter.fake;

import com.seasonplan.lifecycle.domain.AuditLogEntry;
import com.seasonplan.lifecycle.service.AuditLogRepository;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class InMemoryAuditLogRepository implements AuditLogRepository {
    private final Map<UUID, List<AuditLogEntry>> entriesBySeasonPlan = new ConcurrentHashMap<>();

    @Override
    public AuditLogEntry append(AuditLogEntry entry) {
        entriesBySeasonPlan.computeIfAbsent(entry.seasonPlanId(), ignored -> new ArrayList<>()).add(entry);
        return entry;
    }

    @Override
    public List<AuditLogEntry> entriesFor(UUID seasonPlanId) {
        return List.copyOf(entriesBySeasonPlan.getOrDefault(seasonPlanId, List.of()));
    }
}
