package com.seasonplan.lifecycle.service;

import com.seasonplan.reference.adapter.out.configuration.ConfiguredExecutionRules;
import com.seasonplan.reference.domain.ExecutionRuleSnapshot;
import com.seasonplan.reference.api.MetricDefinition;
import org.springframework.mock.env.MockEnvironment;

public final class TestExecutionRules {
    private TestExecutionRules() { }
    public static ExecutionRuleSnapshot snapshot() {
        var env = new MockEnvironment().withProperty("seasonplan.execution-rules.version", "test-v1")
                .withProperty("seasonplan.execution-rules.required-events", "LAUNCH_VALIDATED");
        env.setProperty("seasonplan.incident-rules.version", "test-v1");
        for (var type : com.seasonplan.response.domain.MagicIncident.Type.values()) env.setProperty("seasonplan.incident-rules.severity." + type.name(), "HIGH");
        for (MetricDefinition metric : MetricDefinition.values()) {
            String prefix = "seasonplan.execution-rules.metrics." + metric.key() + ".";
            env.setProperty(prefix + "tolerance", "2"); env.setProperty(prefix + "owner", "test-owner"); env.setProperty(prefix + "response-seconds", "3600");
        }
        return new ConfiguredExecutionRules(env).snapshot();
    }
}
