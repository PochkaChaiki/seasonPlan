package com.seasonplan.lifecycle.adapter.fake;

import com.seasonplan.lifecycle.domain.MagicTask;
import com.seasonplan.lifecycle.application.port.MagicTaskRepository;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class InMemoryMagicTaskRepository implements MagicTaskRepository {
    private final Map<UUID, MagicTask> tasks = new ConcurrentHashMap<>();

    @Override
    public MagicTask save(MagicTask task, String actorId) {
        tasks.put(task.id(), task);
        return task;
    }

    @Override
    public Optional<MagicTask> findById(UUID id) {
        return Optional.ofNullable(tasks.get(id));
    }

    @Override
    public List<MagicTask> findBySeasonPlanId(UUID seasonPlanId) {
        return tasks.values().stream()
                .filter(task -> task.seasonPlanId().equals(seasonPlanId))
                .toList();
    }
}
