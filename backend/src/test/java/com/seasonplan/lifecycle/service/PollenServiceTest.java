package com.seasonplan.lifecycle.service;

import com.seasonplan.pollen.application.PollenService;
import com.seasonplan.pollen.domain.PollenReservationStatus;
import com.seasonplan.season.domain.SeasonPlanStatus;
import com.seasonplan.shared.domain.DomainValidationException;

import com.seasonplan.audit.api.AuditLogEntry;
import com.seasonplan.audit.api.AuditAction;
import com.seasonplan.audit.application.AuditLogService;
import com.seasonplan.identity.api.AccessDeniedForRoleException;
import com.seasonplan.identity.api.ActorRole;

import com.seasonplan.pollen.adapter.out.fake.FakePollenTreeClient;
import com.seasonplan.audit.adapter.out.persistence.InMemoryAuditLogRepository;
import com.seasonplan.pollen.application.port.PollenTreeClient;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class PollenServiceTest {
    @Test void oneServiceCoordinatesReservationsConsumptionAndWarningsWithoutLocalReservationStorage() {
        var f = new ExecutionTestFixture(SeasonPlanStatus.ACTIVE);
        var client = new FakePollenTreeClient(new BigDecimal("3000"));
        var service = PollenTestServices.create(new com.seasonplan.season.application.SeasonQueryService(f.plans), client, () -> f.outbox, f.transactions, f.audit, f.clock);
        UUID requestId = UUID.randomUUID();
        var reservation = service.requestReservation(f.plan.id(), requestId, 1500, ActorRole.POLLEN_GUARDIAN, "guardian");
        assertThat(service.capacity().availableGrams()).isEqualByComparingTo("3000");
        service.confirmReservation(reservation.id(), 1500, "ok", ActorRole.POLLEN_GUARDIAN, "guardian");
        assertThat(service.get(reservation.id()).status()).isEqualTo(PollenReservationStatus.CONFIRMED);
        assertThat(service.requestReservation(f.plan.id(), requestId, 1500, ActorRole.POLLEN_GUARDIAN, "guardian").id())
                .isEqualTo(reservation.id());
        assertThat(service.capacity().availableGrams()).isEqualByComparingTo("1500");

        UUID eventId = UUID.randomUUID();
        var usage = service.recordConsumption(f.plan.id(), eventId, new BigDecimal("1200"), ActorRole.POLLEN_GUARDIAN, "guardian");
        assertThat(service.recordConsumption(f.plan.id(), eventId, new BigDecimal("1200"), ActorRole.POLLEN_GUARDIAN, "guardian"))
                .isEqualTo(usage);
        assertThat(client.summary(f.plan.id()).orElseThrow().actualGrams()).isEqualByComparingTo("1200");
        assertThat(f.events).hasSize(1);
        assertThat(f.audit.entriesFor(f.plan.id())).extracting(AuditLogEntry::action)
                .containsExactly(AuditAction.POLLEN_RESERVATION_CONFIRMED, AuditAction.POLLEN_CONSUMPTION_RECORDED);
        assertThat(f.audit.entriesFor(f.plan.id()).get(0).comment()).contains(reservation.id().toString()).contains("ok");
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void remoteDecisionSurvivesAuditFailureAndIdenticalRetryRepairsOnlyOurAudit(boolean confirm) {
        var f = new ExecutionTestFixture(SeasonPlanStatus.SUBMITTED);
        var client = new FakePollenTreeClient(new BigDecimal("500"));
        var audits = spy(new InMemoryAuditLogRepository());
        var service = PollenTestServices.create(new com.seasonplan.season.application.SeasonQueryService(f.plans), client, () -> f.outbox, f.transactions, new AuditLogService(audits, f.clock), f.clock);
        var reservation = service.requestReservation(f.plan.id(), 100, ActorRole.POLLEN_GUARDIAN, "guardian");
        var failure = new IllegalStateException("audit unavailable");
        doThrow(failure).doCallRealMethod().when(audits).append(any(AuditLogEntry.class));

        assertThatThrownBy(() -> decide(service, reservation.id(), confirm)).isSameAs(failure);
        assertThat(client.findById(reservation.id()).orElseThrow().status())
                .isEqualTo(confirm ? PollenReservationStatus.CONFIRMED : PollenReservationStatus.REJECTED);
        decide(service, reservation.id(), confirm);
        decide(service, reservation.id(), confirm);
        assertThat(audits.entriesFor(f.plan.id())).hasSize(1);
        assertThat(client.capacity().availableGrams()).isEqualByComparingTo(confirm ? "400" : "500");
    }

    @Test void invalidAndUnauthorizedCommandsNeverReachTheExternalClient() {
        var f = new ExecutionTestFixture(SeasonPlanStatus.ACTIVE);
        var client = mock(PollenTreeClient.class);
        var service = PollenTestServices.create(new com.seasonplan.season.application.SeasonQueryService(f.plans), client, () -> f.outbox, f.transactions, f.audit, f.clock);
        assertThatThrownBy(() -> service.requestReservation(f.plan.id(), 0, ActorRole.POLLEN_GUARDIAN, "guardian"))
                .isInstanceOf(DomainValidationException.class);
        assertThatThrownBy(() -> service.confirmReservation(UUID.randomUUID(), 1, "ok", ActorRole.FAIRY_PLANNER, "planner"))
                .isInstanceOf(AccessDeniedForRoleException.class);
        assertThatThrownBy(() -> service.recordConsumption(f.plan.id(), UUID.randomUUID(), new BigDecimal("0.0001"),
                ActorRole.POLLEN_GUARDIAN, "guardian")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.recordConsumption(f.plan.id(), UUID.randomUUID(), BigDecimal.ONE,
                ActorRole.FAIRY_PLANNER, "planner"))
                .isInstanceOf(AccessDeniedForRoleException.class);
        verifyNoInteractions(client);
    }

    private void decide(PollenService service, UUID id, boolean confirm) {
        if (confirm) service.confirmReservation(id, 100, "decision", ActorRole.POLLEN_GUARDIAN, "guardian");
        else service.rejectReservation(id, "decision", ActorRole.POLLEN_GUARDIAN, "guardian");
    }
}
