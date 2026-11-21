package com.seasonplan.lifecycle.service;

import com.seasonplan.lifecycle.domain.RitualSession;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public interface RitualReadinessChecker {
    public record Readiness(List<String> blockers, List<RitualSession.Participant> participants, Set<String> requiredEvents) {
        public Readiness {
            blockers = List.copyOf(blockers); participants = List.copyOf(participants); requiredEvents = Set.copyOf(requiredEvents);
            if (requiredEvents.isEmpty()) throw new IllegalArgumentException("ritual completion events must be configured");
        }
    }
    Readiness inspect(UUID planId);
}
