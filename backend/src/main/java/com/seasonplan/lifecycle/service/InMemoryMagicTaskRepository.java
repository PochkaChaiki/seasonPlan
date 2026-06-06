package com.seasonplan.lifecycle.service;

import com.seasonplan.lifecycle.domain.MagicTask;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class InMemoryMagicTaskRepository {
    private final Map<UUID, MagicTask> tasks = new ConcurrentHashMap<>();

    public MagicTask save(MagicTask task) {
        tasks.put(task.id(), task);
        return task;
    }

    public Optional<MagicTask> findById(UUID id) {
        return Optional.ofNullable(tasks.get(id));
    }

    public List<MagicTask> findBySeasonPlanId(UUID seasonPlanId) {
        return tasks.values().stream()
                .filter(task -> task.seasonPlanId().equals(seasonPlanId))
                .toList();
    }
}
