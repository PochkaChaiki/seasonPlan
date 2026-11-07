package com.seasonplan.lifecycle.application;

import com.seasonplan.lifecycle.application.execution.PollenConsumptionService;
import com.seasonplan.lifecycle.application.port.*;
import com.seasonplan.lifecycle.domain.*;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.util.*;
import static org.assertj.core.api.Assertions.*;

class PollenConsumptionUseCaseTest {
    @Test void consumptionUsesPollenTreeAndRetryDoesNotDoubleSpendOrDuplicateThresholdEvents() {
        UseCaseFixture f = new UseCaseFixture(SeasonPlanStatus.ACTIVE);
        Tree tree = new Tree();
        var service = new PollenConsumptionService(f.plans, tree, f.outbox, f.transactions, f.audit, f.clock);
        UUID event = UUID.randomUUID();
        service.record(f.plan.id(), event, new BigDecimal("80"), ActorRole.POLLEN_GUARDIAN, "guardian");
        service.record(f.plan.id(), event, new BigDecimal("80"), ActorRole.POLLEN_GUARDIAN, "guardian");
        assertThat(tree.actual).isEqualByComparingTo("80.000");
        assertThat(f.events).hasSize(1);
        service.record(f.plan.id(), UUID.randomUUID(), new BigDecimal("20.001"), ActorRole.POLLEN_GUARDIAN, "guardian");
        assertThat(f.events.stream().filter(e -> e.type().equals("POLLEN_OVER_100")).findFirst().orElseThrow().recipients())
                .contains("CHIEF_GUARDIAN");
    }

    @Test void retryAfterOutboxFailureReusesTheExternalConsumptionEvent() {
        UseCaseFixture f = new UseCaseFixture(SeasonPlanStatus.ACTIVE);
        Tree tree = new Tree();
        boolean[] fail = {true};
        NotificationOutbox outbox = event -> { if (fail[0]) { fail[0] = false; throw new IllegalStateException("outbox unavailable"); } f.outbox.enqueue(event); };
        var service = new PollenConsumptionService(f.plans, tree, outbox, f.transactions, f.audit, f.clock);
        UUID id = UUID.randomUUID();
        assertThatThrownBy(() -> service.record(f.plan.id(), id, new BigDecimal("80"), ActorRole.POLLEN_GUARDIAN, "guardian"))
                .isInstanceOf(IllegalStateException.class);
        service.record(f.plan.id(), id, new BigDecimal("80"), ActorRole.POLLEN_GUARDIAN, "guardian");
        assertThat(tree.actual).isEqualByComparingTo("80");
        assertThat(f.events).hasSize(1);
    }

    @Test void invalidAndUnauthorizedConsumptionNeverReachesIntegration() {
        UseCaseFixture f = new UseCaseFixture(SeasonPlanStatus.ACTIVE);
        Tree tree = new Tree();
        var service = new PollenConsumptionService(f.plans, tree, f.outbox, f.transactions, f.audit, f.clock);
        assertThatThrownBy(() -> service.record(f.plan.id(), UUID.randomUUID(), new BigDecimal("0.0001"), ActorRole.POLLEN_GUARDIAN, "guardian"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.record(f.plan.id(), UUID.randomUUID(), BigDecimal.ONE, ActorRole.FAIRY_PLANNER, "planner"))
                .isInstanceOf(com.seasonplan.lifecycle.application.shared.AccessDeniedForRoleException.class);
        assertThat(tree.results).isEmpty();
    }
    private static class Tree implements PollenTree {
        final UUID reservationId = UUID.randomUUID();
        BigDecimal actual = BigDecimal.ZERO;
        final Map<UUID, Summary> results = new HashMap<>();
        public Optional<Summary> summary(UUID planId) { return Optional.of(new Summary(reservationId, new BigDecimal("100"), actual, "v1")); }
        public Summary recordConsumption(UUID planId, UUID eventId, BigDecimal grams) {
            return results.computeIfAbsent(eventId, ignored -> {
                actual = actual.add(grams); return new Summary(reservationId, new BigDecimal("100"), actual, eventId.toString());
            });
        }
    }
}
