package com.seasonplan.lifecycle.service;

import com.seasonplan.monitoring.application.port.MetricRuleProvider;
import com.seasonplan.ritual.api.RitualReadinessChecker;

import com.seasonplan.ritual.domain.RitualSession;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ExecutionContractTest {
    @Test
    void readinessCopiesAllCollectionsAndRequiresEvents() {
        var participant = new RitualSession.Participant("fairy", "LEAD", Instant.EPOCH);
        var blockers = new ArrayList<>(List.of("blocker"));
        var participants = new ArrayList<>(List.of(participant));
        var events = new HashSet<>(Set.of("complete"));
        var readiness = new RitualReadinessChecker.Readiness(blockers, participants, events);
        blockers.clear(); participants.clear(); events.clear();
        assertThat(readiness.blockers()).containsExactly("blocker");
        assertThat(readiness.participants()).containsExactly(participant);
        assertThat(readiness.requiredEvents()).containsExactly("complete");
        assertThatThrownBy(() -> readiness.blockers().clear()).isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> readiness.participants().clear()).isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> readiness.requiredEvents().clear()).isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> new RitualReadinessChecker.Readiness(List.of(), List.of(), Set.of()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void metricRuleRequiresVersionOwnerPositiveResponseTimeAndTolerance() {
        var target = BigDecimal.ONE;
        var duration = Duration.ofMinutes(1);
        for (String value : new String[]{null, "", " "}) {
            assertThatThrownBy(() -> new MetricRuleProvider.MetricRule(target, target, value, "owner", duration)).isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> new MetricRuleProvider.MetricRule(target, target, "v1", value, duration)).isInstanceOf(IllegalArgumentException.class);
        }
        for (Duration value : new Duration[]{null, Duration.ZERO, Duration.ofSeconds(-1)}) {
            assertThatThrownBy(() -> new MetricRuleProvider.MetricRule(target, target, "v1", "owner", value)).isInstanceOf(IllegalArgumentException.class);
        }
        for (BigDecimal value : new BigDecimal[]{null, BigDecimal.ZERO, BigDecimal.ONE.negate()}) {
            assertThatThrownBy(() -> new MetricRuleProvider.MetricRule(target, value, "v1", "owner", duration)).isInstanceOf(IllegalArgumentException.class);
        }
        assertThat(new MetricRuleProvider.MetricRule(target, target, "v1", "owner", duration).target()).isEqualTo(target);
    }
}
