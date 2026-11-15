package com.seasonplan.lifecycle.repository;

import com.seasonplan.lifecycle.domain.RitualSession;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface RitualSessionRepository {
    Optional<RitualSession> find(UUID id);
    List<RitualSession> forPlan(UUID planId);
    void save(RitualSession session);
}
