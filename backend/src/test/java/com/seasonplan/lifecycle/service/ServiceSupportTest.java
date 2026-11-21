package com.seasonplan.lifecycle.service;

import com.seasonplan.security.identity.ActorRole;

import com.seasonplan.lifecycle.domain.*;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class ServiceSupportTest {
    @Test void copyPreservesPinnedExecutionRules() {
        var env = new org.springframework.mock.env.MockEnvironment().withProperty("seasonplan.execution-rules.version", "test")
                .withProperty("seasonplan.execution-rules.required-events", "launch");
        env.setProperty("seasonplan.incident-rules.version", "test-v1");
        for (var type : MagicIncident.Type.values()) env.setProperty("seasonplan.incident-rules.severity." + type.name(), "HIGH");
        for (MetricDefinition metric : MetricDefinition.values()) {
            String key = "seasonplan.execution-rules.metrics." + metric.key() + ".";
            env.setProperty(key + "tolerance", "2"); env.setProperty(key + "owner", "owner"); env.setProperty(key + "response-seconds", "60");
        }
        var plan = SeasonPlan.createDraft(UUID.randomUUID(), "SPRING", List.of(), List.of(), Map.of(), Map.of(), List.of(), "actor", Instant.EPOCH);
        plan.pinExecutionRules(new com.seasonplan.lifecycle.config.ConfiguredExecutionRules(env).snapshot());
        assertThat(ServiceSupport.copy(plan).executionRules()).isEqualTo(plan.executionRules());
    }

    @Test void copyPreservesApprovalConditions() {
        var condition = new ApprovalCondition("weather", "Weather", "Evidence", ActorRole.TALENT_COORDINATOR);
        var plan = SeasonPlan.rehydrate(UUID.randomUUID(), "SPRING", List.of("north"), List.of(), Map.of(), Map.of(),
                SeasonPlanStatus.APPROVED, "conditional", List.of(), "planner", "chief", Instant.EPOCH, Instant.EPOCH, List.of(condition));
        assertThat(ServiceSupport.copy(plan).approvalConditions()).containsExactly(condition);
    }
}
