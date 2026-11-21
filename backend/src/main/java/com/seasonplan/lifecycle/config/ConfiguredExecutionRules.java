package com.seasonplan.lifecycle.config;

import com.seasonplan.lifecycle.domain.ExecutionRuleSnapshot;
import com.seasonplan.lifecycle.domain.MetricDefinition;
import org.springframework.core.env.Environment;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/** Reads the active deployment catalog; no execution values are defaulted. */
public final class ConfiguredExecutionRules {
    private static final String PREFIX = "seasonplan.execution-rules.";
    private final Environment environment;
    public ConfiguredExecutionRules(Environment environment) { this.environment = environment; }
    public ExecutionRuleSnapshot snapshot() {
        try {
            String version = required("version");
            ConfiguredIncidentRuleProvider incidentRules = new ConfiguredIncidentRuleProvider(environment);
            for (var type : com.seasonplan.lifecycle.domain.MagicIncident.Type.values()) incidentRules.severityFor(type);
            Set<String> events = Arrays.stream(required("required-events").split(",", -1)).map(String::trim).collect(Collectors.toUnmodifiableSet());
            Map<String, ExecutionRuleSnapshot.MetricSettings> metrics = new LinkedHashMap<>();
            for (MetricDefinition metric : MetricDefinition.values()) {
                String prefix = "metrics." + metric.key() + ".";
                metrics.put(metric.key(), new ExecutionRuleSnapshot.MetricSettings(new BigDecimal(required(prefix + "tolerance")), required(prefix + "owner"), Long.parseLong(required(prefix + "response-seconds"))));
            }
            Map<String, ExecutionRuleSnapshot.SeasonRequirements> seasons = Map.of(
                    "SPRING", season("WEATHER,POLLEN", "RITUAL_LEAD,WEATHER_WEAVER"),
                    "SUMMER", season("WEATHER,WATER", "RITUAL_LEAD,WATER_KEEPER"),
                    "AUTUMN", season("HARVEST,WEATHER", "RITUAL_LEAD,HARVEST_WARDEN"),
                    "WINTER", season("FROST,SHELTER", "RITUAL_LEAD,FROST_WARDEN"));
            return new ExecutionRuleSnapshot(version, metrics, events, seasons);
        } catch (RuntimeException ex) {
            throw new IllegalStateException("Complete valid versioned seasonplan.execution-rules configuration is required before creating a plan", ex);
        }
    }
    private String required(String key) {
        String value = environment.getProperty(PREFIX + key);
        if (value == null || value.isBlank()) throw new IllegalArgumentException("missing " + PREFIX + key);
        return value.trim();
    }
    private ExecutionRuleSnapshot.SeasonRequirements season(String categories, String roles) {
        return new ExecutionRuleSnapshot.SeasonRequirements(Set.of(categories.split(",")), Set.of(roles.split(",")));
    }
}
