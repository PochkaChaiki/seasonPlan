package com.seasonplan.reference.domain;

import java.util.Map;
import java.util.Set;

/** Existing educational requirements, moved unchanged from configuration. Selected rules are pinned in each plan. */
public final class SeasonalRequirementsCatalog {
    public static final String POLICY_VERSION = "legacy-preparation-v1";
    private SeasonalRequirementsCatalog() { }
    public static Map<String, ExecutionRuleSnapshot.SeasonRequirements> requirements() {
        return Map.of("SPRING", season(Set.of("WEATHER", "POLLEN"), Set.of("RITUAL_LEAD", "WEATHER_WEAVER")),
                "SUMMER", season(Set.of("WEATHER", "WATER"), Set.of("RITUAL_LEAD", "WATER_KEEPER")),
                "AUTUMN", season(Set.of("HARVEST", "WEATHER"), Set.of("RITUAL_LEAD", "HARVEST_WARDEN")),
                "WINTER", season(Set.of("FROST", "SHELTER"), Set.of("RITUAL_LEAD", "FROST_WARDEN")));
    }
    private static ExecutionRuleSnapshot.SeasonRequirements season(Set<String> categories, Set<String> roles) {
        return new ExecutionRuleSnapshot.SeasonRequirements(categories, roles);
    }
}
