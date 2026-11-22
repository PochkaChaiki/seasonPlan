package com.seasonplan.season.api;

import com.seasonplan.shared.domain.TimeWindow;
import java.util.*;

/** Immutable material input; invalid values are retained for the existing domain validators. */
public record SeasonDraft(String seasonType, List<String> targetTerritories, List<TimeWindow> timeWindows,
                          Map<String, String> naturalPhenomenonParameters, Map<String, Double> targetClimateMetrics) {
    public SeasonDraft {
        targetTerritories = Collections.unmodifiableList(new ArrayList<>(targetTerritories == null ? List.of() : targetTerritories));
        timeWindows = Collections.unmodifiableList(new ArrayList<>(timeWindows == null ? List.of() : timeWindows));
        naturalPhenomenonParameters = Collections.unmodifiableMap(new LinkedHashMap<>(naturalPhenomenonParameters == null ? Map.of() : naturalPhenomenonParameters));
        targetClimateMetrics = Collections.unmodifiableMap(new LinkedHashMap<>(targetClimateMetrics == null ? Map.of() : targetClimateMetrics));
    }
}
