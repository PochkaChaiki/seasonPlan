package com.seasonplan.bootstrap;

import com.seasonplan.reference.api.MetricDefinition;
import com.seasonplan.reference.adapter.out.configuration.ConfiguredExecutionRules;
import com.seasonplan.response.domain.MagicIncident;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;
import static org.assertj.core.api.Assertions.*;

class DeploymentExecutionRulesTest {
    private MockEnvironment configured() {
        var env = new MockEnvironment().withProperty("seasonplan.execution-rules.version", "v1")
                .withProperty("seasonplan.execution-rules.required-events", "LAUNCH_VALIDATED")
                .withProperty("seasonplan.incident-rules.version", "v1");
        for (var type : MagicIncident.Type.values()) env.setProperty("seasonplan.incident-rules.severity." + type.name(), "HIGH");
        for (var metric : MetricDefinition.values()) {
            String prefix = "seasonplan.execution-rules.metrics." + metric.key() + ".";
            env.setProperty(prefix + "tolerance", "2"); env.setProperty(prefix + "owner", "owner"); env.setProperty(prefix + "response-seconds", "3600");
        }
        return env;
    }
    private DeploymentExecutionRules deployment(MockEnvironment env) {
        var bootstrap = new LifecycleConfiguration();
        return bootstrap.deploymentExecutionRules(bootstrap.executionRules(env), bootstrap.incidentRuleProvider(env));
    }
    @Test void bootstrapCompositionRequiresEveryIncidentMappingAndVersionBeforePinningRules() {
        assertThat(deployment(configured()).snapshot().version()).isEqualTo("v1");
        for (var type : MagicIncident.Type.values()) {
            for (String value : new String[]{"", "UNKNOWN"}) {
                var env = configured();
                env.setProperty("seasonplan.incident-rules.severity." + type.name(), value);
                assertThatThrownBy(() -> deployment(env).snapshot()).isInstanceOf(IllegalStateException.class);
            }
        }
        var env = configured();
        env.setProperty("seasonplan.incident-rules.version", " ");
        assertThatThrownBy(() -> deployment(env).snapshot()).isInstanceOf(IllegalStateException.class);
    }
    @Test void referenceCatalogDoesNotNeedResponseConfigurationButDeploymentStillDoes() {
        var env = configured();
        env.setProperty("seasonplan.incident-rules.version", "");
        assertThat(new ConfiguredExecutionRules(env).snapshot().version()).isEqualTo("v1");
        assertThatThrownBy(() -> deployment(env).snapshot()).isInstanceOf(IllegalStateException.class);
    }
    @Test void completeIncidentConfigurationDoesNotHideInvalidReferenceConfiguration() {
        for (String key : new String[]{"version", "required-events", "metrics.temperature.tolerance", "metrics.humidity.owner", "metrics.pollen_density.response-seconds"}) {
            var env = configured();
            env.setProperty("seasonplan.execution-rules." + key, "");
            assertThatThrownBy(() -> deployment(env).snapshot()).isInstanceOf(IllegalStateException.class);
        }
    }
}
