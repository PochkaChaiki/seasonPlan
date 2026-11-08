package com.seasonplan.lifecycle.application;

import com.seasonplan.lifecycle.adapter.fake.FakePollenTreeClient;
import com.seasonplan.lifecycle.application.pollen.PollenService;
import com.seasonplan.lifecycle.application.port.NotificationOutbox;
import com.seasonplan.lifecycle.domain.*;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.util.UUID;
import static org.assertj.core.api.Assertions.*;

class PollenConsumptionUseCaseTest {
    @Test void consumptionUsesTheSameExternalClientAndRetriesDoNotDuplicateSpendWarningsOrAudit() {
        var f = new UseCaseFixture(SeasonPlanStatus.ACTIVE);
        var client = confirmedClient(f);
        var service = new PollenService(f.plans, client, () -> f.outbox, f.transactions, f.audit, f.clock);
        UUID event = UUID.randomUUID();
        service.recordConsumption(f.plan.id(), event, new BigDecimal("80"), ActorRole.POLLEN_GUARDIAN, "guardian");
        service.recordConsumption(f.plan.id(), event, new BigDecimal("80"), ActorRole.POLLEN_GUARDIAN, "guardian");
        assertThat(client.summary(f.plan.id()).orElseThrow().actualGrams()).isEqualByComparingTo("80.000");
        assertThat(f.events).hasSize(1);
        assertThat(f.audit.entriesFor(f.plan.id())).hasSize(1);
        service.recordConsumption(f.plan.id(), UUID.randomUUID(), new BigDecimal("20.001"), ActorRole.POLLEN_GUARDIAN, "guardian");
        assertThat(f.events.stream().filter(e -> e.type().equals("POLLEN_OVER_100")).findFirst().orElseThrow().recipients())
                .contains("CHIEF_GUARDIAN");
    }

    @Test void retryAfterOutboxFailureReusesExternalConsumption() {
        var f = new UseCaseFixture(SeasonPlanStatus.ACTIVE);
        var client = confirmedClient(f);
        boolean[] fail = {true};
        NotificationOutbox outbox = event -> {
            if (fail[0]) { fail[0] = false; throw new IllegalStateException("outbox unavailable"); }
            f.outbox.enqueue(event);
        };
        var service = new PollenService(f.plans, client, () -> outbox, f.transactions, f.audit, f.clock);
        UUID event = UUID.randomUUID();
        assertThatThrownBy(() -> service.recordConsumption(f.plan.id(), event, new BigDecimal("80"), ActorRole.POLLEN_GUARDIAN, "guardian"))
                .isInstanceOf(IllegalStateException.class);
        service.recordConsumption(f.plan.id(), event, new BigDecimal("80"), ActorRole.POLLEN_GUARDIAN, "guardian");
        assertThat(client.summary(f.plan.id()).orElseThrow().actualGrams()).isEqualByComparingTo("80");
        assertThat(f.events).hasSize(1);
    }

    @Test void nonActiveSeasonCannotConsumePollen() {
        var f = new UseCaseFixture(SeasonPlanStatus.SUBMITTED);
        var client = confirmedClient(f);
        var service = new PollenService(f.plans, client, () -> f.outbox, f.transactions, f.audit, f.clock);
        assertThatThrownBy(() -> service.recordConsumption(f.plan.id(), UUID.randomUUID(), BigDecimal.ONE,
                ActorRole.POLLEN_GUARDIAN, "guardian")).isInstanceOf(DomainValidationException.class);
        assertThat(client.summary(f.plan.id()).orElseThrow().actualGrams()).isEqualByComparingTo("0");
    }

    private FakePollenTreeClient confirmedClient(UseCaseFixture f) {
        var client = new FakePollenTreeClient(new BigDecimal("1000"));
        var reservation = client.requestReservation(f.plan.id(), UUID.randomUUID(), 100, "guardian");
        client.confirmReservation(reservation.id(), 100, "ok", "guardian");
        return client;
    }
}
