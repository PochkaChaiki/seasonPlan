package com.seasonplan.lifecycle.service;

import com.seasonplan.pollen.application.PollenService;
import com.seasonplan.pollen.application.port.PollenTreeClient;
import com.seasonplan.pollen.api.PollenTreeUnavailableException;
import com.seasonplan.pollen.domain.PollenReservation;
import com.seasonplan.audit.api.*;
import com.seasonplan.identity.api.ActorRole;
import com.seasonplan.season.domain.SeasonPlanStatus;
import com.seasonplan.season.application.SeasonQueryService;
import org.junit.jupiter.api.Test;
import java.util.UUID;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class PollenDurableCommandTest {
    @Test void recoveryContractExposesOperationIdCommandWithoutMutableOwnerResult() throws Exception {
        var command = com.seasonplan.pollen.api.PollenOperations.class.getMethod("reconcileOperation", UUID.class, ActorRole.class, String.class);
        assertThat(command.getReturnType()).isEqualTo(com.seasonplan.pollen.api.ReservationFacts.class);
    }
    @Test void provenFakeCapacityDenialIsTerminalNotUnknown() {
        var f = new ExecutionTestFixture(SeasonPlanStatus.DRAFT);
        var store = new com.seasonplan.pollen.adapter.out.fake.InMemoryPollenIntentRepository();
        var service = new PollenService(new SeasonQueryService(f.plans), new com.seasonplan.pollen.adapter.out.fake.FakePollenTreeClient(java.math.BigDecimal.ONE), () -> f.outbox, f.transactions, f.audit, f.clock, store, () -> {});
        assertThatThrownBy(() -> service.requestReservation(f.plan.id(), UUID.randomUUID(), 100, ActorRole.POLLEN_GUARDIAN, "guardian"))
                .isInstanceOf(com.seasonplan.pollen.application.port.PollenProviderRejectedException.class);
        assertThat(store.hasUnresolved(f.plan.id())).isFalse();
    }
    @Test void uncertainRequestReplayMustNotIssueAnotherMutation() {
        var f = new ExecutionTestFixture(SeasonPlanStatus.DRAFT);
        var client = mock(PollenTreeClient.class);
        var service = PollenTestServices.create(new SeasonQueryService(f.plans), client, () -> f.outbox, f.transactions, f.audit, f.clock);
        UUID key = UUID.randomUUID();
        when(client.requestReservation(eq(f.plan.id()), any(UUID.class), eq(100), eq("guardian")))
                .thenThrow(new PollenTreeUnavailableException("response lost"));
        for (int i = 0; i < 2; i++)
            assertThatThrownBy(() -> service.requestReservation(f.plan.id(), key, 100, ActorRole.POLLEN_GUARDIAN, "guardian"))
                    .isInstanceOf(PollenTreeUnavailableException.class);
        verify(client, times(1)).requestReservation(eq(f.plan.id()), any(UUID.class), eq(100), eq("guardian"));
    }
    @Test void acceptedDecisionSurvivesAuditFailureAndRetryCompletesLocally() {
        var f = new ExecutionTestFixture(SeasonPlanStatus.DRAFT);
        var client = mock(PollenTreeClient.class);
        var audit = mock(AuditTrail.class);
        var service = PollenTestServices.create(new SeasonQueryService(f.plans), client, () -> f.outbox, f.transactions, audit, f.clock);
        UUID id = UUID.randomUUID();
        var pending = new PollenReservation(id, f.plan.id(), 100);
        var confirmed = new PollenReservation(id, f.plan.id(), 100); confirmed.confirm(100, "ok", "guardian");
        when(client.requestReservation(eq(f.plan.id()), any(UUID.class), eq(100), eq("guardian"))).thenReturn(pending);
        when(client.confirmReservation(id, 100, "ok", "guardian")).thenReturn(confirmed);
        service.requestReservation(f.plan.id(), UUID.randomUUID(), 100, ActorRole.POLLEN_GUARDIAN, "guardian");
        var failure = new IllegalStateException("audit unavailable");
        when(audit.appendOnce(any(UUID.class), eq(f.plan.id()), eq(AuditAction.POLLEN_RESERVATION_CONFIRMED), eq("guardian"), anyString()))
                .thenThrow(failure).thenReturn(null);
        assertThatThrownBy(() -> service.confirmReservation(id, 100, "ok", ActorRole.POLLEN_GUARDIAN, "guardian")).isSameAs(failure);
        assertThat(service.confirmReservation(id, 100, "ok", ActorRole.POLLEN_GUARDIAN, "guardian").status())
                .isEqualTo(com.seasonplan.pollen.domain.PollenReservationStatus.CONFIRMED);
        verify(client, times(1)).confirmReservation(id, 100, "ok", "guardian");
    }
}
