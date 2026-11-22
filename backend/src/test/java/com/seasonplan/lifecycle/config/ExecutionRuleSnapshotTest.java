package com.seasonplan.lifecycle.config;

import com.seasonplan.reference.adapter.out.configuration.ConfiguredExecutionRules;
import com.seasonplan.reference.api.MetricDefinition;
import com.seasonplan.response.domain.MagicIncident;
import com.seasonplan.season.domain.SeasonPlan;

import com.seasonplan.season.adapter.out.fake.InMemorySeasonPlanRepository;
import com.seasonplan.monitoring.application.PinnedMetricRuleProvider;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;

class ExecutionRuleSnapshotTest {
    private MockEnvironment configuration() {
        var env = new MockEnvironment().withProperty("seasonplan.execution-rules.version", "test-v1")
                .withProperty("seasonplan.execution-rules.required-events", "LAUNCH_VALIDATED");
        env.setProperty("seasonplan.incident-rules.version", "test-v1");
        for (var type : MagicIncident.Type.values()) env.setProperty("seasonplan.incident-rules.severity." + type.name(), "HIGH");
        for (MetricDefinition metric : MetricDefinition.values()) {
            String prefix = "seasonplan.execution-rules.metrics." + metric.key() + ".";
            env.setProperty(prefix + "tolerance", "2"); env.setProperty(prefix + "owner", "owner"); env.setProperty(prefix + "response-seconds", "3600");
        }
        return env;
    }
    @Test void configurationMustBeCompleteAndSnapshotsRemainImmutable() {
        MockEnvironment env = configuration();
        var source = new com.seasonplan.bootstrap.DeploymentExecutionRules(new ConfiguredExecutionRules(env),
                new com.seasonplan.response.adapter.out.configuration.ConfiguredIncidentRuleProvider(env));
        var snapshot = source.snapshot();
        env.setProperty("seasonplan.execution-rules.metrics.temperature.tolerance", "5");
        assertThat(snapshot.metrics().get("temperature").tolerance()).isEqualByComparingTo("2");
        assertThat(snapshot.requiredEvents()).containsExactly("LAUNCH_VALIDATED");
        env.setProperty("seasonplan.execution-rules.required-events", " ");
        assertThatThrownBy(source::snapshot).isInstanceOf(IllegalStateException.class);
        env.setProperty("seasonplan.execution-rules.required-events", "LAUNCH_VALIDATED");
        env.setProperty("seasonplan.incident-rules.severity.SECURITY", "");
        assertThatThrownBy(source::snapshot).isInstanceOf(IllegalStateException.class);
    }
    @Test void metricProviderUsesPinnedSettingsAndCurrentPlanTarget() {
        var plans = new InMemorySeasonPlanRepository();
        var plan = SeasonPlan.createDraft(UUID.randomUUID(), "SPRING", List.of("north"), List.of(), Map.of("x", "y"), Map.of("temperature", 20.0), List.of(), "actor", Instant.EPOCH);
        plan.pinExecutionRules(new ConfiguredExecutionRules(configuration()).snapshot()); plans.save(plan);
        var provider = new PinnedMetricRuleProvider(new com.seasonplan.season.application.SeasonQueryService(plans));
        var rule = provider.forMetric(plan.id(), MetricDefinition.TEMPERATURE);
        assertThat(rule.target()).isEqualByComparingTo("20");
        assertThat(rule.tolerance()).isEqualByComparingTo("2");
        assertThat(rule.version()).isEqualTo("test-v1");
        var material = com.seasonplan.season.application.ServiceSupport.copy(plan);
        material.setTargetClimateMetrics(Map.of("temperature", 21.0));
        plan.updateDraftFieldsFrom(material, "editor", Instant.EPOCH.plusSeconds(1)); plans.save(plan);
        assertThat(provider.forMetric(plan.id(), MetricDefinition.TEMPERATURE).target()).isEqualByComparingTo("21");
        assertThat(provider.forMetric(plan.id(), MetricDefinition.TEMPERATURE).tolerance()).isEqualByComparingTo("2");
        assertThatThrownBy(() -> provider.forMetric(plan.id(), MetricDefinition.HUMIDITY)).isInstanceOf(IllegalStateException.class);
    }
}
