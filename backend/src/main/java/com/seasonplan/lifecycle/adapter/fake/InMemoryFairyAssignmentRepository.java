package com.seasonplan.lifecycle.adapter.fake;

import com.seasonplan.lifecycle.domain.FairyAssignment;
import com.seasonplan.lifecycle.domain.AssignmentStatus;
import com.seasonplan.lifecycle.application.port.FairyAssignmentRepository;

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
    public List<FairyAssignment> findConfirmedByFairyId(String fairyId) {
        return assignments.values().stream()
                .filter(assignment -> assignment.fairyId().equals(fairyId))
                .filter(assignment -> assignment.status() == AssignmentStatus.CONFIRMED)
                .toList();
    }
}
