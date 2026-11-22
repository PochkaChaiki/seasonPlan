package com.seasonplan.lifecycle.service;

import com.seasonplan.pollen.application.PollenService;
import com.seasonplan.pollen.domain.PollenCapacity;
import com.seasonplan.pollen.domain.PollenReservation;
import com.seasonplan.season.application.SeasonPlanService;
import com.seasonplan.season.domain.SeasonPlanStatus;
import com.seasonplan.shared.domain.DomainValidationException;
import com.seasonplan.workflows.application.ComputedRitualReadinessChecker;

import com.seasonplan.audit.api.AuditLogEntry;
import com.seasonplan.audit.api.AuditAction;
import com.seasonplan.identity.api.ActorRole;

import com.seasonplan.pollen.application.port.PollenTreeClient;
import com.seasonplan.shared.application.NotFoundException;
import com.seasonplan.pollen.api.PollenTreeUnavailableException;
import com.seasonplan.reference.adapter.out.fake.AllowingNaturalCalendarClient;
import com.seasonplan.work.adapter.out.fake.InMemoryFairyAssignmentRepository;
import com.seasonplan.work.adapter.out.fake.InMemoryMagicTaskRepository;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.*;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class PollenReservationBoundaryTest {
    private final ExecutionTestFixture fixture = new ExecutionTestFixture(SeasonPlanStatus.DRAFT);
    private final PollenTreeClient client = mock(PollenTreeClient.class);
    private final PollenService service = PollenTestServices.create(new com.seasonplan.season.application.SeasonQueryService(fixture.plans), client, () -> fixture.outbox,
            fixture.transactions, fixture.audit, fixture.clock);
    private final UUID planId = fixture.plan.id();
    private final SeasonPlanService approval = new SeasonPlanService(fixture.plans, new com.seasonplan.workflows.application.PollenResourceEvidenceSource(new com.seasonplan.pollen.application.PollenOperationsFacade(service)),
            fixture.audit,
            fixture.transactions, fixture.outbox, new com.seasonplan.reference.application.CalendarValidationService(new AllowingNaturalCalendarClient()), fixture.clock, TestExecutionRules::snapshot,
            new ComputedRitualReadinessChecker(new com.seasonplan.season.application.SeasonQueryService(fixture.plans), new com.seasonplan.work.application.WorkQueryService(new InMemoryMagicTaskRepository(), new InMemoryFairyAssignmentRepository()), new com.seasonplan.pollen.application.PollenOperationsFacade(service),
                    new com.seasonplan.response.application.ResponseQueryService(fixture.incidentPort, fixture.correctionPort), new com.seasonplan.ritual.domain.RitualReadinessPolicy()));

    PollenReservationBoundaryTest() {
        var material = com.seasonplan.season.application.ServiceSupport.copy(fixture.plan);
        material.setTimeWindows(SeasonPlanTestFixture.validPlanDraft("planner").timeWindows());
        fixture.plan.updateDraftFieldsFrom(material, "planner", fixture.now);
        fixture.plans.save(fixture.plan); fixture.setStatus(SeasonPlanStatus.SUBMITTED);
    }

    @Test void requestAndCapacityReturnExternalValuesWithoutAnyReservationPersistence() {
        UUID requestId = UUID.randomUUID();
        var external = new PollenReservation(UUID.randomUUID(), planId, 1234);
        when(client.requestReservation(eq(planId), any(UUID.class), eq(1500), eq("guardian"))).thenReturn(external);
        var capacity = new PollenCapacity(new BigDecimal("9000.500"), "external-v7");
        when(client.capacity()).thenReturn(capacity);
        assertThat(service.requestReservation(planId, requestId, 1500, ActorRole.POLLEN_GUARDIAN, "guardian")).usingRecursiveComparison().isEqualTo(external);
        assertThat(service.capacity()).isSameAs(capacity);
        assertThat(fixture.audit.entriesFor(planId)).isEmpty();
    }

    @Test void legacyRequestDelegatesWithGeneratedCorrelation() {
        var external = new PollenReservation(UUID.randomUUID(), planId, 50);
        when(client.requestReservation(eq(planId), any(UUID.class), eq(50), eq("guardian"))).thenReturn(external);
        assertThat(service.requestReservation(planId, 50, ActorRole.POLLEN_GUARDIAN, "guardian")).usingRecursiveComparison().isEqualTo(external);
    }

    @Test void confirmationAboveOldLimitUsesExternalResultAndAuditsOnlySuccess() {
        var external = new PollenReservation(UUID.randomUUID(), planId, 2000);
        trust(external);
        external.confirm(1800, "external comment", "external guardian");
        when(client.confirmReservation(external.id(), 2000, "requested comment", "guardian")).thenReturn(external);
        assertThat(service.confirmReservation(external.id(), 2000, "requested comment", ActorRole.POLLEN_GUARDIAN, "guardian"))
                .usingRecursiveComparison().isEqualTo(external);
        assertThat(fixture.audit.entriesFor(planId)).extracting(AuditLogEntry::action).containsExactly(AuditAction.POLLEN_RESERVATION_CONFIRMED);
    }

    @Test void rejectionAndLookupUseExternalState() {
        var external = new PollenReservation(UUID.randomUUID(), planId, 100);
        trust(external);
        external.reject("external rejection", "external guardian");
        when(client.rejectReservation(external.id(), "reason", "guardian")).thenReturn(external);
        assertThat(service.rejectReservation(external.id(), "reason", ActorRole.POLLEN_GUARDIAN, "guardian")).usingRecursiveComparison().isEqualTo(external);
        assertThat(fixture.audit.entriesFor(planId)).extracting(AuditLogEntry::action).containsExactly(AuditAction.POLLEN_RESERVATION_REJECTED);
        when(client.findById(external.id())).thenReturn(Optional.of(external));
        assertThat(service.get(external.id())).usingRecursiveComparison().isEqualTo(external);
        when(client.findById(external.id())).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.get(external.id())).isInstanceOf(NotFoundException.class);
    }

    @Test void remoteDenialOrOutageNeverCreatesSuccessAudit() {
        for (boolean denied : List.of(true, false)) {
            var store = new com.seasonplan.pollen.adapter.out.fake.InMemoryPollenIntentRepository();
            var isolated = new PollenService(new com.seasonplan.season.application.SeasonQueryService(fixture.plans), client, () -> fixture.outbox,
                    fixture.transactions, fixture.audit, fixture.clock, store, () -> {});
            RuntimeException failure = denied ? new com.seasonplan.pollen.application.port.PollenProviderRejectedException(Map.of("capacity", List.of("denied"))) : new PollenTreeUnavailableException("offline");
            when(client.requestReservation(eq(planId), any(UUID.class), eq(100), eq("guardian"))).thenThrow(failure);
            UUID key = UUID.randomUUID();
            for (int retry = 0; retry < 2; retry++) {
                assertThatThrownBy(() -> isolated.requestReservation(planId, key, 100, ActorRole.POLLEN_GUARDIAN, "guardian"))
                        .isInstanceOf(denied ? com.seasonplan.pollen.application.port.PollenProviderRejectedException.class : com.seasonplan.pollen.api.PollenCommandPendingException.class);
            }
            verify(client, times(1)).requestReservation(eq(planId), any(UUID.class), eq(100), eq("guardian"));
            assertThat(fixture.audit.entriesFor(planId)).isEmpty();
            reset(client);
        }
    }

    private void trust(PollenReservation reservation) {
        when(client.requestReservation(eq(planId), any(UUID.class), eq(reservation.requestedAmount()), eq("guardian"))).thenReturn(reservation);
        service.requestReservation(planId, UUID.randomUUID(), reservation.requestedAmount(), ActorRole.POLLEN_GUARDIAN, "guardian");
    }

    @Test void invalidAmountsAreRejectedBeforeIntegration() {
        assertThatThrownBy(() -> service.requestReservation(planId, 0, ActorRole.POLLEN_GUARDIAN, "guardian"))
                .isInstanceOf(DomainValidationException.class);
        assertThatThrownBy(() -> service.confirmReservation(UUID.randomUUID(), -1, "reason", ActorRole.POLLEN_GUARDIAN, "guardian"))
                .isInstanceOf(DomainValidationException.class);
        verifyNoInteractions(client);
    }

    @Test void legacyExternalConfirmationCannotAcquireCurrentFundingByLookupOrConfirmation() {
        fixture.setStatus(SeasonPlanStatus.VERIFIED);
        var legacy = new PollenReservation(UUID.randomUUID(), planId, 100);
        legacy.confirm(100, "old", "guardian");
        when(client.findById(legacy.id())).thenReturn(Optional.of(legacy));
        when(client.findLatestBySeasonPlanId(planId)).thenReturn(Optional.of(legacy));
        when(client.findAuthoritativeConfirmedBySeasonPlanId(planId)).thenReturn(Optional.of(legacy));
        service.get(legacy.id());
        assertThat(service.bindingRevision(legacy.id(), planId)).isNull();
        assertThatThrownBy(() -> service.confirmReservation(legacy.id(), 100, "old", ActorRole.POLLEN_GUARDIAN, "guardian"))
                .isInstanceOf(DomainValidationException.class);
        verify(client, never()).confirmReservation(any(), anyInt(), any(), any());
        assertThatThrownBy(() -> approval.approve(planId, "old funds", ActorRole.CHIEF_GUARDIAN, "chief"))
                .isInstanceOf(DomainValidationException.class);
        assertThat(approval.calculateChecklist(planId)).containsKey("pollen-confirmed");
    }

    @Test void approvalRejectsAuthorityForAnotherPlan() {
        fixture.setStatus(SeasonPlanStatus.VERIFIED);
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
        var wrong = new PollenReservation(UUID.randomUUID(), UUID.randomUUID(), 100);
        wrong.confirm(100, "ok", "guardian");
        when(client.findLatestBySeasonPlanId(planId)).thenReturn(Optional.of(wrong));
        assertThatThrownBy(() -> approval.approve(planId, "ok", ActorRole.CHIEF_GUARDIAN, "chief")).isInstanceOf(DomainValidationException.class);
        verify(client, never()).findAuthoritativeConfirmedBySeasonPlanId(planId);
        assertThat(fixture.plan.status()).isEqualTo(SeasonPlanStatus.VERIFIED);
    }

    @Test void approvalAndReadinessRequireLiveExternalConfirmation() {
        fixture.setStatus(SeasonPlanStatus.VERIFIED);
        var readiness = approval;
        var pending = new PollenReservation(UUID.randomUUID(), planId, 100);
        for (Optional<PollenReservation> state : List.of(Optional.<PollenReservation>empty(), Optional.of(pending))) {
            when(client.findLatestBySeasonPlanId(planId)).thenReturn(state);
            assertThatThrownBy(() -> approval.approve(planId, "approved", ActorRole.CHIEF_GUARDIAN, "chief")).isInstanceOf(DomainValidationException.class);
            assertThat(readiness.calculateChecklist(planId)).containsKey("pollen-confirmed");
            assertThat(fixture.plan.status()).isEqualTo(SeasonPlanStatus.VERIFIED);
        }
        when(client.findLatestBySeasonPlanId(planId)).thenThrow(new PollenTreeUnavailableException("offline"));
        assertThatThrownBy(() -> approval.approve(planId, "approved", ActorRole.CHIEF_GUARDIAN, "chief")).isInstanceOf(PollenTreeUnavailableException.class);
        assertThatThrownBy(() -> readiness.calculateChecklist(planId)).isInstanceOf(PollenTreeUnavailableException.class);
        var confirmed = new PollenReservation(UUID.randomUUID(), planId, 100);
        trust(confirmed);
        confirmed.confirm(100, "ok", "guardian");
        doReturn(Optional.of(confirmed)).when(client).findLatestBySeasonPlanId(planId);
        doReturn(Optional.of(confirmed)).when(client).findAuthoritativeConfirmedBySeasonPlanId(planId);
        assertThat(readiness.calculateChecklist(planId)).doesNotContainKey("pollen-confirmed");
        assertThat(approval.approve(planId, "approved", ActorRole.CHIEF_GUARDIAN, "chief").status()).isEqualTo(SeasonPlanStatus.APPROVED);
    }
}
