package com.seasonplan.lifecycle.service;

import com.seasonplan.lifecycle.domain.FairyAssignment;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class InMemoryFairyAssignmentRepository {
    private final Map<UUID, FairyAssignment> assignments = new ConcurrentHashMap<>();

    public FairyAssignment save(FairyAssignment assignment) {
        assignments.put(assignment.id(), assignment);
        return assignment;
    }

    public List<FairyAssignment> findByMagicTaskId(UUID magicTaskId) {
        return assignments.values().stream()
                .filter(assignment -> assignment.magicTaskId().equals(magicTaskId))
                .toList();
    }

    public List<FairyAssignment> all() {
        return List.copyOf(assignments.values());
    }
}
