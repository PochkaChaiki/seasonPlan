package com.seasonplan.lifecycle.service;

import com.seasonplan.lifecycle.domain.SeasonPlan;

import java.util.Optional;
import java.util.UUID;

public interface SeasonPlanRepository {
    SeasonPlan save(SeasonPlan plan);

    Optional<SeasonPlan> findById(UUID id);
}
