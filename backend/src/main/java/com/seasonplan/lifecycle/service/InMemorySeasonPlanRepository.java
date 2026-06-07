package com.seasonplan.lifecycle.service;

import com.seasonplan.lifecycle.domain.SeasonPlan;

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
}
