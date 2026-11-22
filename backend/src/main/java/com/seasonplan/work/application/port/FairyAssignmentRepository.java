package com.seasonplan.work.application.port;

import com.seasonplan.work.domain.FairyAssignment;

import java.util.List;
import java.util.UUID;

public interface FairyAssignmentRepository {
    /** Hold sorted participant-scope locks until the current command transaction ends. */
    void lockParticipants(List<String> fairyIds);

    FairyAssignment save(FairyAssignment assignment, String actorId);

    List<FairyAssignment> findByMagicTaskId(UUID magicTaskId);

    List<FairyAssignment> findConfirmedByFairyId(String fairyId);
}
