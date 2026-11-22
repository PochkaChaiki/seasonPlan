package com.seasonplan.response.adapter.out.configuration;

import com.seasonplan.response.domain.MagicIncident;
import com.seasonplan.response.application.port.IncidentRuleProvider;
import org.springframework.core.env.Environment;

import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;

/** Resolves the active deployment catalog; previously registered incidents retain their severity. */
public final class ConfiguredIncidentRuleProvider implements IncidentRuleProvider {
    private static final String PREFIX = "seasonplan.incident-rules.";
    private final Environment environment;

    public ConfiguredIncidentRuleProvider(Environment environment) {
        this.environment = Objects.requireNonNull(environment);
    }

    @Override
    public MagicIncident.Severity severityFor(MagicIncident.Type type) {
        Objects.requireNonNull(type);
        String version = environment.getProperty(PREFIX + "version");
        if (version == null || version.isBlank()) {
            throw new IllegalStateException("Configure a nonblank " + PREFIX + "version before registering incidents");
        }
        Map<MagicIncident.Type, MagicIncident.Severity> severities = new EnumMap<>(MagicIncident.Type.class);
        for (MagicIncident.Type incidentType : MagicIncident.Type.values()) {
            String key = PREFIX + "severity." + incidentType.name();
            String configured = environment.getProperty(key);
            try {
                severities.put(incidentType, MagicIncident.Severity.valueOf(configured == null ? "" : configured.trim()));
            } catch (IllegalArgumentException ex) {
                throw new IllegalStateException("Configure a valid " + key + " before registering incidents", ex);
            }
        }
        return severities.get(type);
    }
}
