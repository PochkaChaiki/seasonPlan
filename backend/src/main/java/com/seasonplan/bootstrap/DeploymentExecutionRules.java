package com.seasonplan.bootstrap;

import com.seasonplan.reference.adapter.out.configuration.ConfiguredExecutionRules;
import com.seasonplan.reference.domain.ExecutionRuleSnapshot;
import com.seasonplan.response.adapter.out.configuration.ConfiguredIncidentRuleProvider;
import com.seasonplan.response.domain.MagicIncident;

/** Validates cross-capability deployment configuration before a new plan pins its execution rules. */
public final class DeploymentExecutionRules {
    private final ConfiguredExecutionRules executionRules;
    private final ConfiguredIncidentRuleProvider incidentRules;
    public DeploymentExecutionRules(ConfiguredExecutionRules executionRules, ConfiguredIncidentRuleProvider incidentRules) {
        this.executionRules = executionRules; this.incidentRules = incidentRules;
    }
    public ExecutionRuleSnapshot snapshot() {
        try {
            for (var type : MagicIncident.Type.values()) incidentRules.severityFor(type);
            return executionRules.snapshot();
        } catch (RuntimeException ex) {
            throw new IllegalStateException("Complete valid versioned seasonplan.execution-rules configuration is required before creating a plan", ex);
        }
    }
}
