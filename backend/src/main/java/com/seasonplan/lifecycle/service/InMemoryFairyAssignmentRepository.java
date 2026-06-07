package com.seasonplan.lifecycle.service;

import com.seasonplan.lifecycle.domain.FairyAssignment;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class InMemoryFairyAssignmentRepository implements FairyAssignmentRepository {
    private final Map<UUID, FairyAssignment> assignments = new ConcurrentHashMap<>();

    @Override
    public FairyAssignment save(FairyAssignment assignment, String actorId) {
        assignments.put(assignment.id(), assignment);
        return assignment;
    }

    @Override
    public List<FairyAssignment> findByMagicTaskId(UUID magicTaskId) {
        return assignments.values().stream()
                .filter(assignment -> assignment.magicTaskId().equals(magicTaskId))
                .toList();
    }

    @Override
    public List<FairyAssignment> all() {
        return List.copyOf(assignments.values());
    }
}
