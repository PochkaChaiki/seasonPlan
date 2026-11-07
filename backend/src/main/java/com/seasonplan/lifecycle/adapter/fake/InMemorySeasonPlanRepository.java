package com.seasonplan.lifecycle.adapter.fake;

import com.seasonplan.lifecycle.domain.SeasonPlan;
import com.seasonplan.lifecycle.application.port.SeasonPlanRepository;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class InMemorySeasonPlanRepository implements SeasonPlanRepository {
    private final Map<UUID, SeasonPlan> plans = new ConcurrentHashMap<>();

    @Override
    public SeasonPlan save(SeasonPlan plan) {
        plans.put(plan.id(), plan);
        return plan;
    }

    @Override
    public Optional<SeasonPlan> findById(UUID id) {
        return Optional.ofNullable(plans.get(id));
    }

    @Override
    public List<SeasonPlan> findAll() {
        return plans.values().stream()
                .sorted(Comparator.comparing(SeasonPlan::updatedAt, Comparator.nullsLast(Comparator.naturalOrder())).reversed())
                .toList();
    }
}
