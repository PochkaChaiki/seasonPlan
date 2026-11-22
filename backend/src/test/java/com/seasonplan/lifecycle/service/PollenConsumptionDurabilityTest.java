package com.seasonplan.lifecycle.service;

import com.seasonplan.audit.api.AuditTrail;
import com.seasonplan.identity.api.ActorRole;
import com.seasonplan.notification.api.NotificationOutbox;
import com.seasonplan.pollen.adapter.out.fake.FakePollenTreeClient;
import com.seasonplan.pollen.application.port.PollenTreeClient;
import com.seasonplan.season.application.SeasonQueryService;
import com.seasonplan.season.domain.SeasonPlanStatus;
import java.math.BigDecimal;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Counts boundary mutations, not the educational fake's internal deduplicated spend. */
class PollenConsumptionDurabilityTest {
    @Test void acceptedConsumptionWithLostResponseMustNotDispatchAgainOnRetry() {
        var f = new ExecutionTestFixture(SeasonPlanStatus.ACTIVE);
        var provider = confirmedProvider(f);
        var client = mock(PollenTreeClient.class);
        when(client.lookupConsumption(any(), any())).thenReturn(new PollenTreeClient.EventLookup(PollenTreeClient.EventLookupStatus.ABSENT, null));
        var first = new AtomicBoolean(true);
        UUID event = UUID.randomUUID();
        BigDecimal amount = new BigDecimal("80.000");
        when(client.summary(f.plan.id())).thenAnswer(call -> provider.summary(f.plan.id()));
        when(client.recordConsumption(f.plan.id(), event, amount)).thenAnswer(call -> {
            var receipt = provider.recordConsumption(f.plan.id(), event, amount);
            if (first.getAndSet(false)) throw new IllegalStateException("response lost after acceptance");
            return receipt;
        });
        var service = PollenTestServices.create(new SeasonQueryService(f.plans), client, () -> f.outbox,
                f.transactions, f.audit, f.clock);

        assertThatThrownBy(() -> service.recordConsumption(f.plan.id(), event, amount,
                ActorRole.POLLEN_GUARDIAN, "guardian")).isInstanceOf(RuntimeException.class);
        // A pending outcome is allowed. Another provider mutation is not.
        try { service.recordConsumption(f.plan.id(), event, amount, ActorRole.POLLEN_GUARDIAN, "guardian"); }
        catch (RuntimeException pending) { /* Recovery need not settle an unsupported exact-event lookup. */ }

        assertThat(provider.summary(f.plan.id()).orElseThrow().actualGrams()).isEqualByComparingTo(amount);
        verify(client, times(1)).recordConsumption(f.plan.id(), event, amount);
    }

    @Test void knownConsumptionReceiptMustSurviveAuditFailureWithoutAnotherDispatch() {
        var f = new ExecutionTestFixture(SeasonPlanStatus.ACTIVE);
        var provider = confirmedProvider(f);
        var client = mock(PollenTreeClient.class);
        when(client.lookupConsumption(any(), any())).thenReturn(new PollenTreeClient.EventLookup(PollenTreeClient.EventLookupStatus.ABSENT, null));
        UUID event = UUID.randomUUID();
        BigDecimal amount = new BigDecimal("80.000");
        when(client.recordConsumption(f.plan.id(), event, amount))
                .thenAnswer(call -> provider.recordConsumption(f.plan.id(), event, amount));
        when(client.summary(f.plan.id())).thenAnswer(call -> provider.summary(f.plan.id()));
        var audit = mock(AuditTrail.class);
        var first = new AtomicBoolean(true);
        when(audit.appendOnce(any(), any(), any(), anyString(), anyString())).thenAnswer(call -> {
            if (first.getAndSet(false)) throw new IllegalStateException("audit unavailable");
            return f.audit.appendOnce(call.getArgument(0), call.getArgument(1), call.getArgument(2),
                    call.getArgument(3), call.getArgument(4));
        });
        var service = PollenTestServices.create(new SeasonQueryService(f.plans), client, () -> f.outbox,
                f.transactions, audit, f.clock);

        assertThatThrownBy(() -> service.recordConsumption(f.plan.id(), event, amount,
                ActorRole.POLLEN_GUARDIAN, "guardian")).isInstanceOf(IllegalStateException.class);
        var result = service.recordConsumption(f.plan.id(), event, amount, ActorRole.POLLEN_GUARDIAN, "guardian");

        assertThat(result.actualGrams()).isEqualByComparingTo(amount);
        assertThat(f.audit.entriesFor(f.plan.id())).hasSize(1);
        verify(client, times(1)).recordConsumption(f.plan.id(), event, amount);
    }

    @Test void knownConsumptionReceiptMustSurviveOutboxFailureWithoutAnotherDispatch() {
        var f = new ExecutionTestFixture(SeasonPlanStatus.ACTIVE);
        var provider = confirmedProvider(f);
        var client = mock(PollenTreeClient.class);
        when(client.lookupConsumption(any(), any())).thenReturn(new PollenTreeClient.EventLookup(PollenTreeClient.EventLookupStatus.ABSENT, null));
        UUID event = UUID.randomUUID();
        BigDecimal amount = new BigDecimal("80.000");
        when(client.recordConsumption(f.plan.id(), event, amount))
                .thenAnswer(call -> provider.recordConsumption(f.plan.id(), event, amount));
        when(client.summary(f.plan.id())).thenAnswer(call -> provider.summary(f.plan.id()));
        var first = new AtomicBoolean(true);
        NotificationOutbox outbox = notification -> {
            if (first.getAndSet(false)) throw new IllegalStateException("outbox unavailable");
            f.outbox.enqueue(notification);
        };
        var service = PollenTestServices.create(new SeasonQueryService(f.plans), client, () -> outbox,
                f.transactions, f.audit, f.clock);

        assertThatThrownBy(() -> service.recordConsumption(f.plan.id(), event, amount,
                ActorRole.POLLEN_GUARDIAN, "guardian")).isInstanceOf(IllegalStateException.class);
        var result = service.recordConsumption(f.plan.id(), event, amount, ActorRole.POLLEN_GUARDIAN, "guardian");

        assertThat(result.actualGrams()).isEqualByComparingTo(amount);
        assertThat(f.events).hasSize(1);
        verify(client, times(1)).recordConsumption(f.plan.id(), event, amount);
    }

    private FakePollenTreeClient confirmedProvider(ExecutionTestFixture f) {
        var provider = new FakePollenTreeClient(new BigDecimal("1000"));
        var reservation = provider.requestReservation(f.plan.id(), UUID.randomUUID(), 100, "guardian");
        provider.confirmReservation(reservation.id(), 100, "ok", "guardian");
        return provider;
    }
}
