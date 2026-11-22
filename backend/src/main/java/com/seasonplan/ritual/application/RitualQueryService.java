package com.seasonplan.ritual.application;

import com.seasonplan.ritual.api.RitualQueries;
import com.seasonplan.ritual.application.port.RitualSessionRepository;
import com.seasonplan.ritual.domain.RitualSession;
import java.util.List;
import java.util.UUID;

public final class RitualQueryService implements RitualQueries {
    private final RitualSessionRepository records;
    public RitualQueryService(RitualSessionRepository records) { this.records = records; }
    @Override public List<RitualSession> forPlan(UUID planId) { return List.copyOf(records.forPlan(planId)); }
}
