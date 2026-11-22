package com.seasonplan.ritual.api;

import com.seasonplan.ritual.domain.RitualSession;
import java.util.List;
import java.util.UUID;

/** Frozen lists of supported immutable RitualSession records, including nested immutable values. */
public interface RitualQueries {
    List<RitualSession> forPlan(UUID planId);
}
