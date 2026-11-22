package com.seasonplan.reference.api;

import com.seasonplan.shared.domain.TimeWindow;
import java.util.*;

/** Reference-owned calendar input. Invalid draft values remain available for domain validation. */
public record CalendarRequest(String seasonType, List<String> targetTerritories, List<TimeWindow> timeWindows,
                              Map<String, String> naturalPhenomenonParameters, Map<String, Double> targetClimateMetrics) {
    public CalendarRequest {
        targetTerritories = Collections.unmodifiableList(new ArrayList<>(targetTerritories == null ? List.of() : targetTerritories));
        timeWindows = Collections.unmodifiableList(new ArrayList<>(timeWindows == null ? List.of() : timeWindows));
        naturalPhenomenonParameters = Collections.unmodifiableMap(new LinkedHashMap<>(naturalPhenomenonParameters == null ? Map.of() : naturalPhenomenonParameters));
        targetClimateMetrics = Collections.unmodifiableMap(new LinkedHashMap<>(targetClimateMetrics == null ? Map.of() : targetClimateMetrics));
    }
}
