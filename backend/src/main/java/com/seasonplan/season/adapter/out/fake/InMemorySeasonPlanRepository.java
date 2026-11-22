package com.seasonplan.season.adapter.out.fake;

import com.seasonplan.season.domain.SeasonPlan;
import com.seasonplan.season.application.port.SeasonPlanRepository;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class InMemorySeasonPlanRepository implements SeasonPlanRepository {
    private final Map<UUID, SeasonPlan> plans = new ConcurrentHashMap<>();
    private final Map<UUID, java.util.NavigableMap<Long, com.seasonplan.season.domain.MaterialRevision>> history = new ConcurrentHashMap<>();

    /** Single-threaded educational adapter; concurrent commands require the JDBC adapter. */
    @Override
    public void lockSeasonWindows() { }

    @Override
    public SeasonPlan save(SeasonPlan plan) {
        var entries = history.computeIfAbsent(plan.id(), key -> new java.util.TreeMap<>());
        var revision = plan.currentMaterialRevision();
        revision.requireConsistentWith(entries.isEmpty() ? null : entries.lastEntry().getValue());
        entries.putIfAbsent(plan.materialRevision(), revision);
        plans.put(plan.id(), copy(plan, entries.get(plan.materialRevision()).stamp()));
        return plan;
    }

    @Override
    public Optional<SeasonPlan> findById(UUID id) {
        return Optional.ofNullable(plans.get(id)).map(InMemorySeasonPlanRepository::copy);
    }

    @Override
    public List<SeasonPlan> findAll() {
        return plans.values().stream().map(InMemorySeasonPlanRepository::copy)
                .sorted(Comparator.comparing(SeasonPlan::updatedAt, Comparator.nullsLast(Comparator.naturalOrder())).reversed())
                .toList();
    }
    @Override public List<com.seasonplan.season.domain.MaterialRevision> materialHistory(UUID id) {
        var entries = history.get(id);
        return entries == null ? List.of() : List.copyOf(entries.values());
    }
    private static SeasonPlan copy(SeasonPlan plan) { return copy(plan, plan.materialRevisionStamp()); }
    private static SeasonPlan copy(SeasonPlan plan, com.seasonplan.season.domain.MaterialRevisionStamp stamp) {
        var copy = SeasonPlan.rehydrate(plan.id(), plan.seasonType(), plan.targetTerritories(), plan.timeWindows(), plan.naturalPhenomenonParameters(),
                plan.targetClimateMetrics(), plan.status(), plan.approvalComment(), plan.readinessChecklist(), plan.createdBy(), plan.updatedBy(), plan.createdAt(), plan.updatedAt(), plan.approvalConditions(), stamp);
        if (plan.executionRules() != null) copy.pinExecutionRules(plan.executionRules());
        copy.setReadinessChecklist(plan.readinessChecklist().stream().map(item -> new com.seasonplan.season.domain.ChecklistItem(item.key(), item.complete())).toList());
        return copy;
    }
}
