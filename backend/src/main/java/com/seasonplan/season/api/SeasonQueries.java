package com.seasonplan.season.api;

import java.util.List;
import java.util.UUID;

/** Read-only owner queries. Every result is a detached immutable snapshot of current persisted state. */
public interface SeasonQueries {
    SeasonFacts get(UUID id);
    List<SeasonFacts> listPlans();
    List<com.seasonplan.season.domain.MaterialRevision> materialHistory(UUID id);
}
