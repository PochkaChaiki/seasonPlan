package com.seasonplan.lifecycle.repository;

import com.seasonplan.lifecycle.domain.SeasonPlan;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SeasonPlanRepository {
    SeasonPlan save(SeasonPlan plan);

    Optional<SeasonPlan> findById(UUID id);

    List<SeasonPlan> findAll();
}
