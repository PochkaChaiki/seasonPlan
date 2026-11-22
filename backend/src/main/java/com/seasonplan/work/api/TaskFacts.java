package com.seasonplan.work.api;

import com.seasonplan.work.domain.MagicTaskStatus;
import com.seasonplan.shared.domain.TimeWindow;
import java.util.UUID;

/** Detached task snapshot; enum, window and scalar components are immutable values. */
public record TaskFacts(UUID id, UUID seasonPlanId, String title, String description, String requiredTalent,
                        TimeWindow timeWindow, MagicTaskStatus status, String category) { }
