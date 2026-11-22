package com.seasonplan.work.api;

import com.seasonplan.work.domain.AssignmentStatus;
import java.time.Instant;
import java.util.UUID;

/** Detached assignment snapshot, including persisted confirmation evidence. */
public record AssignmentFacts(UUID id, UUID magicTaskId, String fairyId, String role, String talent,
                              AssignmentStatus status, Instant confirmedAt) { }
