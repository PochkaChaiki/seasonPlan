package com.seasonplan.lifecycle.service;

import com.seasonplan.monitoring.domain.ClimateMetric;
import com.seasonplan.monitoring.domain.MetricReactionPolicy;
import com.seasonplan.response.api.MetricReactions;
import com.seasonplan.response.application.MetricReactionService;
import com.seasonplan.season.application.SeasonLifecycleService;
import com.seasonplan.season.domain.SeasonPlanStatus;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.time.Duration;
import java.util.*;
import static org.assertj.core.api.Assertions.*;

class MetricReactionServiceTest {
    @Test void validatedCriticalSpecificationIsImmutableAndOwnerReplayHasNoDuplicateEffects() {
        var f = new ExecutionTestFixture(SeasonPlanStatus.ACTIVE);
        var metric = new ClimateMetric(UUID.randomUUID(), f.plan.id(), "sensor", "critical", ClimateMetric.definitionFor("temperature", "CELSIUS"),
                new BigDecimal("24"), new BigDecimal("20"), new BigDecimal("2"), null, f.now);
        var spec = MetricReactionPolicy.evaluate(metric, "v1", "owner", Duration.ofMinutes(5), f.now);
        assertThat(spec.incident().type()).isEqualTo(MetricReactions.IncidentType.CLIMATE);
        assertThat(spec.incident().severity()).isEqualTo(MetricReactions.Severity.CRITICAL);
        assertThat(spec.correction().key()).isEqualTo(f.plan.id() + ":" + metric.id() + ":v1");
        assertThat(spec.correction().deadline()).isEqualTo(f.now.plusSeconds(300));
        assertThatThrownBy(() -> spec.criticalAlert().recipients().clear()).isInstanceOf(UnsupportedOperationException.class);
        var owner = new MetricReactionService(f.incidentPort, f.correctionPort,
                new SeasonLifecycleService(f.plans, f.transactions, f.audit, f.clock), f.transactions, f.outbox, f.audit, f.clock);
        var first = owner.react(f.plan.id(), metric.id(), spec, "balancer");
        var replay = owner.react(f.plan.id(), metric.id(), spec, "balancer");
        assertThat(replay).isEqualTo(first);
        assertThat(f.incidents).hasSize(1);
        assertThat(f.actions).hasSize(1);
        assertThat(f.events).hasSize(1);
        assertThat(f.events.get(0).deduplicationKey()).isEqualTo("critical-metric:" + metric.id());
        assertThat(f.incidents.get(first.incidentId()).openedBy()).isEqualTo("balancer");
        assertThat(f.actions.get(first.correctionId()).incidentId()).isEqualTo(first.incidentId());
        assertThat(f.audit.entriesFor(f.plan.id())).hasSize(3);
    }
    @Test void normalAndWarningSpecificationsRetainCorrectionOnlyBehavior() {
        var f = new ExecutionTestFixture(SeasonPlanStatus.ACTIVE);
        for (String value : List.of("20", "22")) {
            var metric = new ClimateMetric(UUID.randomUUID(), f.plan.id(), "sensor", value,
                    ClimateMetric.definitionFor("temperature", "CELSIUS"), new BigDecimal(value),
                    new BigDecimal("20"), new BigDecimal("2"), null, f.now);
            var spec = MetricReactionPolicy.evaluate(metric, "v1", "owner", Duration.ofMinutes(5), f.now);
            var result = f.metricReactions.react(f.plan.id(), metric.id(), spec, "balancer");
            assertThat(result.incidentId()).isNull();
            if (value.equals("20")) assertThat(result.correctionId()).isNull();
            else {
                assertThat(f.actions.get(result.correctionId()).owner()).isEqualTo("owner");
                assertThat(f.actions.get(result.correctionId()).deadline()).isEqualTo(f.now.plusSeconds(300));
                assertThat(f.events.get(0).deduplicationKey()).isEqualTo("correction:" + result.correctionId());
                assertThat(f.events.get(0).type()).isEqualTo("CORRECTION_ASSIGNED");
                assertThat(f.events.get(0).recipients()).containsExactly("owner");
            }
        }
        assertThat(f.incidents).isEmpty();
        assertThat(f.actions).hasSize(1);
        assertThat(f.events).hasSize(1);
        assertThat(f.audit.entriesFor(f.plan.id())).hasSize(1);
        assertThat(f.plans.findById(f.plan.id()).orElseThrow().status()).isEqualTo(SeasonPlanStatus.ACTIVE);
    }
}
