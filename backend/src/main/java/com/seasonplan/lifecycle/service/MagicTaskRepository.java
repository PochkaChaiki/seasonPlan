package com.seasonplan.lifecycle.service;

import com.seasonplan.lifecycle.domain.MagicTask;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface MagicTaskRepository {
    MagicTask save(MagicTask task, String actorId);

    Optional<MagicTask> findById(UUID id);

    List<MagicTask> findBySeasonPlanId(UUID seasonPlanId);
}
