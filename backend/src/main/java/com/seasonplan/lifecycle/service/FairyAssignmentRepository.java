package com.seasonplan.lifecycle.service;

import com.seasonplan.lifecycle.domain.FairyAssignment;

import java.util.List;
import java.util.UUID;

public interface FairyAssignmentRepository {
    FairyAssignment save(FairyAssignment assignment, String actorId);

    List<FairyAssignment> findByMagicTaskId(UUID magicTaskId);

    List<FairyAssignment> findConfirmedByFairyId(String fairyId);
}
