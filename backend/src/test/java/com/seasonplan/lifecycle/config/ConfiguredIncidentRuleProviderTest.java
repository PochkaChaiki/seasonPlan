package com.seasonplan.bootstrap;

import com.seasonplan.bootstrap.LifecycleConfiguration;
import com.seasonplan.response.adapter.out.configuration.ConfiguredIncidentRuleProvider;

import com.seasonplan.response.domain.MagicIncident;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ConfiguredIncidentRuleProviderTest {
    private MockEnvironment configured() {
        MockEnvironment env = new MockEnvironment().withProperty("seasonplan.incident-rules.version", "rules-2026");
        for (MagicIncident.Type type : MagicIncident.Type.values()) {
            env.setProperty("seasonplan.incident-rules.severity." + type.name(), "HIGH");
        }
        return env;
    }

    @Test void resolvesConfiguredSeverityForEveryType() {
        MockEnvironment env = configured();
        env.setProperty("seasonplan.incident-rules.severity.CLIMATE", "CRITICAL");
        ConfiguredIncidentRuleProvider provider = new ConfiguredIncidentRuleProvider(env);
        assertThat(provider.severityFor(MagicIncident.Type.CLIMATE)).isEqualTo(MagicIncident.Severity.CRITICAL);
        assertThat(provider.severityFor(MagicIncident.Type.POLLEN)).isEqualTo(MagicIncident.Severity.HIGH);
    }

    @Test void missingVersionOrAnyMappingBlocksOperation() {
        MockEnvironment missingVersion = configured();
        missingVersion.setProperty("seasonplan.incident-rules.version", " ");
        assertThatThrownBy(() -> new ConfiguredIncidentRuleProvider(missingVersion).severityFor(MagicIncident.Type.RITUAL))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("version");
        MockEnvironment missingMapping = configured();
        missingMapping.setProperty("seasonplan.incident-rules.severity.SECURITY", "");
        assertThatThrownBy(() -> new ConfiguredIncidentRuleProvider(missingMapping).severityFor(MagicIncident.Type.RITUAL))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("SECURITY");
    }

    @Test void lifecycleConfigurationProvidesTheConcreteRuleProvider() {
        assertThat(new LifecycleConfiguration().incidentRuleProvider(configured()))
                .isInstanceOf(ConfiguredIncidentRuleProvider.class);
    }

    @Test void invalidSeverityIsNotSubstituted() {
        MockEnvironment env = configured();
        env.setProperty("seasonplan.incident-rules.severity.CLIMATE", "UNKNOWN");
        assertThatThrownBy(() -> new ConfiguredIncidentRuleProvider(env).severityFor(MagicIncident.Type.CLIMATE))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("CLIMATE");
    }
}
