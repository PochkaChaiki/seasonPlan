package com.seasonplan.season.application.port;

import com.seasonplan.season.domain.SeasonPlan;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SeasonPlanRepository {
    /** Serialize window-conflict decisions across plans until the command transaction ends. */
    void lockSeasonWindows();

    SeasonPlan save(SeasonPlan plan);

    Optional<SeasonPlan> findById(UUID id);

    List<SeasonPlan> findAll();
    List<com.seasonplan.season.domain.MaterialRevision> materialHistory(UUID id);
}
