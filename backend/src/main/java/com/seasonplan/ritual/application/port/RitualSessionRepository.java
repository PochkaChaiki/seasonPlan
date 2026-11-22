package com.seasonplan.ritual.application.port;

import com.seasonplan.ritual.domain.RitualSession;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface RitualSessionRepository {
    Optional<RitualSession> find(UUID id);
    List<RitualSession> forPlan(UUID planId);
    void save(RitualSession session);
}
