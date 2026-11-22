package com.seasonplan.season.domain;
import com.seasonplan.shared.domain.TimeWindow;
import java.util.*;
/** Canonical material identity: order is not persisted, but exact values are significant. */
public record MaterialParameters(String seasonType, List<String> targetTerritories, List<TimeWindow> timeWindows,
                                 Map<String, String> naturalPhenomenonParameters, Map<String, Double> targetClimateMetrics) {
    public MaterialParameters {
        var territories = new ArrayList<>(targetTerritories);
        territories.sort(Comparator.nullsFirst(Comparator.naturalOrder()));
        targetTerritories = Collections.unmodifiableList(territories);
        var windows = new ArrayList<>(timeWindows);
        windows.sort(Comparator.nullsFirst(Comparator.comparing(TimeWindow::start, Comparator.nullsFirst(Comparator.naturalOrder()))
                .thenComparing(TimeWindow::end, Comparator.nullsFirst(Comparator.naturalOrder()))));
        timeWindows = Collections.unmodifiableList(windows);
        naturalPhenomenonParameters = Collections.unmodifiableMap(new LinkedHashMap<>(naturalPhenomenonParameters));
        targetClimateMetrics = Collections.unmodifiableMap(new LinkedHashMap<>(targetClimateMetrics));
    }
}
