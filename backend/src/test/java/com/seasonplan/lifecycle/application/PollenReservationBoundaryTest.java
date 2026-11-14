package com.seasonplan.lifecycle.application;

import com.seasonplan.lifecycle.adapter.fake.*;
import com.seasonplan.lifecycle.application.approval.ApprovalService;
import com.seasonplan.lifecycle.application.pollen.PollenService;
import com.seasonplan.lifecycle.application.port.*;
import com.seasonplan.lifecycle.application.preparation.ReadinessChecklistService;
import com.seasonplan.lifecycle.application.shared.*;
import com.seasonplan.lifecycle.domain.*;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.*;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class PollenReservationBoundaryTest {
    private final UseCaseFixture fixture = new UseCaseFixture(SeasonPlanStatus.SUBMITTED);
    private final PollenTreeClient client = mock(PollenTreeClient.class);
    private final PollenService service = new PollenService(fixture.plans, client, () -> fixture.outbox,
            fixture.transactions, fixture.audit, fixture.clock);
    private final UUID planId = fixture.plan.id();

    @Test void requestAndCapacityReturnExternalValuesWithoutAnyReservationPersistence() {
        UUID requestId = UUID.randomUUID();
        var external = new PollenReservation(UUID.randomUUID(), planId, 1234);
        when(client.requestReservation(planId, requestId, 1500, "guardian")).thenReturn(external);
        var capacity = new PollenCapacity(new BigDecimal("9000.500"), "external-v7");
        when(client.capacity()).thenReturn(capacity);
        assertThat(service.requestReservation(planId, requestId, 1500, ActorRole.POLLEN_GUARDIAN, "guardian")).isSameAs(external);
        assertThat(service.capacity()).isSameAs(capacity);
        assertThat(fixture.audit.entriesFor(planId)).isEmpty();
    }

    @Test void legacyRequestDelegatesWithGeneratedCorrelation() {
        var external = new PollenReservation(UUID.randomUUID(), planId, 50);
        when(client.requestReservation(eq(planId), any(UUID.class), eq(50), eq("guardian"))).thenReturn(external);
        assertThat(service.requestReservation(planId, 50, ActorRole.POLLEN_GUARDIAN, "guardian")).isSameAs(external);
    }

    @Test void confirmationAboveOldLimitUsesExternalResultAndAuditsOnlySuccess() {
        var external = new PollenReservation(UUID.randomUUID(), planId, 2000);
        external.confirm(1800, "external comment", "external guardian");
        when(client.confirmReservation(external.id(), 2000, "requested comment", "guardian")).thenReturn(external);
        assertThat(service.confirmReservation(external.id(), 2000, "requested comment", ActorRole.POLLEN_GUARDIAN, "guardian"))
                .isSameAs(external);
        assertThat(fixture.audit.entriesFor(planId)).extracting(AuditLogEntry::action).containsExactly(AuditAction.POLLEN_RESERVATION_CONFIRMED);
    }

    @Test void rejectionAndLookupUseExternalState() {
        var external = new PollenReservation(UUID.randomUUID(), planId, 100);
        external.reject("external rejection", "external guardian");
        when(client.rejectReservation(external.id(), "reason", "guardian")).thenReturn(external);
        assertThat(service.rejectReservation(external.id(), "reason", ActorRole.POLLEN_GUARDIAN, "guardian")).isSameAs(external);
        assertThat(fixture.audit.entriesFor(planId)).extracting(AuditLogEntry::action).containsExactly(AuditAction.POLLEN_RESERVATION_REJECTED);
        when(client.findById(external.id())).thenReturn(Optional.of(external));
        assertThat(service.get(external.id())).isSameAs(external);
        when(client.findById(external.id())).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.get(external.id())).isInstanceOf(NotFoundException.class);
    }

    @Test void remoteDenialOrOutageNeverCreatesSuccessAudit() {
        UUID id = UUID.randomUUID();
        UUID requestId = UUID.randomUUID();
        for (RuntimeException failure : List.of(new DomainValidationException(Map.of("capacity", List.of("denied"))),
                new PollenTreeUnavailableException("offline"))) {
            when(client.requestReservation(planId, requestId, 100, "guardian")).thenThrow(failure);
            when(client.confirmReservation(id, 100, "reason", "guardian")).thenThrow(failure);
            when(client.rejectReservation(id, "reason", "guardian")).thenThrow(failure);
            when(client.findById(id)).thenThrow(failure);
            assertThatThrownBy(() -> service.requestReservation(planId, requestId, 100, ActorRole.POLLEN_GUARDIAN, "guardian")).isSameAs(failure);
            assertThatThrownBy(() -> service.confirmReservation(id, 100, "reason", ActorRole.POLLEN_GUARDIAN, "guardian")).isSameAs(failure);
            assertThatThrownBy(() -> service.rejectReservation(id, "reason", ActorRole.POLLEN_GUARDIAN, "guardian")).isSameAs(failure);
            assertThatThrownBy(() -> service.get(id)).isSameAs(failure);
            assertThat(fixture.audit.entriesFor(planId)).isEmpty();
            reset(client);
        }
    }

    @Test void invalidAmountsAreRejectedBeforeIntegration() {
        assertThatThrownBy(() -> service.requestReservation(planId, 0, ActorRole.POLLEN_GUARDIAN, "guardian"))
                .isInstanceOf(DomainValidationException.class);
        assertThatThrownBy(() -> service.confirmReservation(UUID.randomUUID(), -1, "reason", ActorRole.POLLEN_GUARDIAN, "guardian"))
                .isInstanceOf(DomainValidationException.class);
        verifyNoInteractions(client);
    }

    @Test void approvalRejectsAuthorityForAnotherPlan() {
        fixture.setStatus(SeasonPlanStatus.VERIFIED);
        var approval = new ApprovalService(fixture.plans, service, fixture.audit, fixture.transactions, fixture.outbox, plan -> {}, fixture.clock);
        var latest = new PollenReservation(UUID.randomUUID(), planId, 100);
        latest.confirm(100, "ok", "guardian");
        var wrong = new PollenReservation(UUID.randomUUID(), UUID.randomUUID(), 100);
        wrong.confirm(100, "ok", "guardian");
        when(client.findLatestBySeasonPlanId(planId)).thenReturn(Optional.of(latest));
        when(client.findAuthoritativeConfirmedBySeasonPlanId(planId)).thenReturn(Optional.of(wrong));
        assertThatThrownBy(() -> approval.approve(planId, "ok", ActorRole.CHIEF_GUARDIAN, "chief")).isInstanceOf(DomainValidationException.class);
        assertThat(fixture.plan.status()).isEqualTo(SeasonPlanStatus.VERIFIED);
    }

    @Test void authorityOutageDoesNotFallBackToLatestOrChangePlan() {
        fixture.setStatus(SeasonPlanStatus.VERIFIED);
        var approval = new ApprovalService(fixture.plans, service, fixture.audit, fixture.transactions, fixture.outbox, plan -> {}, fixture.clock);
        var latest = new PollenReservation(UUID.randomUUID(), planId, 100);
        latest.confirm(100, "ok", "guardian");
        when(client.findLatestBySeasonPlanId(planId)).thenReturn(Optional.of(latest));
        when(client.findAuthoritativeConfirmedBySeasonPlanId(planId)).thenThrow(new PollenTreeUnavailableException("offline"));
        assertThatThrownBy(() -> approval.approve(planId, "ok", ActorRole.CHIEF_GUARDIAN, "chief")).isInstanceOf(PollenTreeUnavailableException.class);
        assertThat(fixture.plan.status()).isEqualTo(SeasonPlanStatus.VERIFIED);
        assertThat(fixture.events).isEmpty();
    }

    @Test void latestReservationForAnotherPlanIsRejected() {
        fixture.setStatus(SeasonPlanStatus.VERIFIED);
        var approval = new ApprovalService(fixture.plans, service, fixture.audit, fixture.transactions, fixture.outbox, plan -> {}, fixture.clock);
        var wrong = new PollenReservation(UUID.randomUUID(), UUID.randomUUID(), 100);
        wrong.confirm(100, "ok", "guardian");
        when(client.findLatestBySeasonPlanId(planId)).thenReturn(Optional.of(wrong));
        assertThatThrownBy(() -> approval.approve(planId, "ok", ActorRole.CHIEF_GUARDIAN, "chief")).isInstanceOf(DomainValidationException.class);
        verify(client, never()).findAuthoritativeConfirmedBySeasonPlanId(planId);
        assertThat(fixture.plan.status()).isEqualTo(SeasonPlanStatus.VERIFIED);
    }

    @Test void approvalAndReadinessRequireLiveExternalConfirmation() {
        var approval = new ApprovalService(fixture.plans, service, fixture.audit, fixture.transactions, fixture.outbox, plan -> {}, fixture.clock);
        fixture.setStatus(SeasonPlanStatus.VERIFIED);
        var readiness = new ReadinessChecklistService(fixture.plans, service, new InMemoryMagicTaskRepository(),
                new InMemoryFairyAssignmentRepository(), fixture.transactions, fixture.clock);
        var pending = new PollenReservation(UUID.randomUUID(), planId, 100);
        for (Optional<PollenReservation> state : List.of(Optional.<PollenReservation>empty(), Optional.of(pending))) {
            when(client.findLatestBySeasonPlanId(planId)).thenReturn(state);
            assertThatThrownBy(() -> approval.approve(planId, "approved", ActorRole.CHIEF_GUARDIAN, "chief")).isInstanceOf(DomainValidationException.class);
            assertThat(readiness.calculateChecklist(planId)).containsKey("pollenReservation");
            assertThat(fixture.plan.status()).isEqualTo(SeasonPlanStatus.VERIFIED);
        }
        when(client.findLatestBySeasonPlanId(planId)).thenThrow(new PollenTreeUnavailableException("offline"));
        assertThatThrownBy(() -> approval.approve(planId, "approved", ActorRole.CHIEF_GUARDIAN, "chief")).isInstanceOf(PollenTreeUnavailableException.class);
        assertThatThrownBy(() -> readiness.calculateChecklist(planId)).isInstanceOf(PollenTreeUnavailableException.class);
        var confirmed = new PollenReservation(UUID.randomUUID(), planId, 100);
        confirmed.confirm(100, "ok", "guardian");
        doReturn(Optional.of(confirmed)).when(client).findLatestBySeasonPlanId(planId);
        doReturn(Optional.of(confirmed)).when(client).findAuthoritativeConfirmedBySeasonPlanId(planId);
        assertThat(readiness.calculateChecklist(planId)).doesNotContainKey("pollenReservation");
        assertThat(approval.approve(planId, "approved", ActorRole.CHIEF_GUARDIAN, "chief").status()).isEqualTo(SeasonPlanStatus.APPROVED);
    }
}
