package com.seasonplan.lifecycle.service;

import com.seasonplan.audit.api.AuditAction;
import com.seasonplan.identity.api.ActorRole;
import com.seasonplan.pollen.adapter.out.fake.*;
import com.seasonplan.pollen.adapter.out.persistence.SpringRemoteCommandBoundary;
import com.seasonplan.pollen.api.*;
import com.seasonplan.pollen.application.PollenService;
import com.seasonplan.pollen.application.port.*;
import com.seasonplan.pollen.domain.*;
import com.seasonplan.season.application.SeasonQueryService;
import com.seasonplan.season.domain.SeasonPlanStatus;
import com.seasonplan.shared.domain.DomainValidationException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class PollenConsumptionSafetyTest {
    final ExecutionTestFixture f = new ExecutionTestFixture(SeasonPlanStatus.ACTIVE);
    final InMemoryPollenIntentRepository store = new InMemoryPollenIntentRepository();
    final FakePollenTreeClient provider = spy(new FakePollenTreeClient(new BigDecimal("5000")));
    PollenService service() { return new PollenService(new SeasonQueryService(f.plans), provider, () -> f.outbox,
            f.transactions, f.audit, f.clock, store, new SpringRemoteCommandBoundary()); }
    void confirmed(UUID plan) {
        var r = provider.requestReservation(plan, UUID.randomUUID(), 100, "guardian");
        provider.confirmReservation(r.id(), 100, "ok", "guardian");
    }
    @Test void publicOwnerContractExposesConsumptionRecovery() {
        assertThat(Arrays.stream(PollenOperations.class.getMethods()).map(java.lang.reflect.Method::getName))
                .contains("reconcileConsumption");
    }
    @Test void providerEventIdentityRemainsScopedToPlan() {
        var other = new ExecutionTestFixture(SeasonPlanStatus.ACTIVE);
        f.plans.save(other.plan); confirmed(f.plan.id()); confirmed(other.plan.id());
        var key = UUID.randomUUID();
        service().recordConsumption(f.plan.id(), key, BigDecimal.ONE, ActorRole.POLLEN_GUARDIAN, "guardian");
        assertThat(service().recordConsumption(other.plan.id(), key, BigDecimal.ONE, ActorRole.POLLEN_GUARDIAN, "guardian").actualGrams())
                .isEqualByComparingTo("1");
    }
    @Test void changedLegacyAmountDoesNotPoisonTheOriginalKeyOrRewriteAudit() {
        confirmed(f.plan.id()); var key = UUID.randomUUID();
        provider.recordConsumption(f.plan.id(), key, new BigDecimal("80"));
        var auditId = UUID.nameUUIDFromBytes(("pollen-consumption:" + f.plan.id() + ":" + key).getBytes(StandardCharsets.UTF_8));
        var original = f.audit.appendOnce(auditId, f.plan.id(), AuditAction.POLLEN_CONSUMPTION_RECORDED, "guardian", key.toString());
        clearInvocations(provider);
        assertThatThrownBy(() -> service().recordConsumption(f.plan.id(), key, new BigDecimal("79"), ActorRole.POLLEN_GUARDIAN, "guardian"))
                .isInstanceOf(DomainValidationException.class);
        assertThat(store.unresolvedConsumption(f.plan.id())).isEmpty();
        assertThat(service().recordConsumption(f.plan.id(), key, new BigDecimal("80"), ActorRole.POLLEN_GUARDIAN, "guardian").actualGrams())
                .isEqualByComparingTo("80");
        assertThat(f.audit.entriesFor(f.plan.id())).containsExactly(original);
        verify(provider, never()).recordConsumption(any(), any(), any());
    }
    @Test void provenDenialIsTerminalNotAnUnknownSpend() {
        confirmed(f.plan.id()); var key = UUID.randomUUID();
        doThrow(new PollenProviderRejectedException(Map.of("grams", List.of("denied"))))
                .when(provider).recordConsumption(f.plan.id(), key, new BigDecimal("1.000"));
        for (int i = 0; i < 2; i++) assertThatThrownBy(() -> service().recordConsumption(f.plan.id(), key, BigDecimal.ONE,
                ActorRole.POLLEN_GUARDIAN, "guardian")).isInstanceOf(PollenProviderRejectedException.class);
        assertThat(store.unresolvedConsumption(f.plan.id())).isEmpty();
        verify(provider, times(1)).recordConsumption(any(), any(), any());
    }
    @Test void unsettledReservationCannotBecomeSettledReportUsage() {
        confirmed(f.plan.id());
        store.insert(new PollenIntent(UUID.randomUUID(), new PollenIntent.Payload(PollenIntent.Kind.REQUEST, f.plan.id(),
                f.plan.materialRevision(), UUID.randomUUID(), UUID.randomUUID(), null, 100, null, "guardian"),
                PollenIntent.State.UNKNOWN, null, null, f.now));
        clearInvocations(provider);
        assertThatThrownBy(() -> service().usage(f.plan.id())).isInstanceOf(PollenCommandPendingException.class);
        verify(provider, never()).summary(any());
    }
    @Test void missingOutboxFailsBeforeAnyProviderAccess() {
        var missing = new PollenService(new SeasonQueryService(f.plans), provider, () -> null, f.transactions,
                f.audit, f.clock, store, new SpringRemoteCommandBoundary());
        assertThatThrownBy(() -> missing.recordConsumption(f.plan.id(), UUID.randomUUID(), BigDecimal.ONE,
                ActorRole.POLLEN_GUARDIAN, "guardian")).isInstanceOf(RuntimeException.class);
        verifyNoInteractions(provider);
    }
    @Test void missingConfirmedAgreementCannotDispatch() {
        var key = UUID.randomUUID();
        assertThatThrownBy(() -> service().recordConsumption(f.plan.id(), key, BigDecimal.ONE,
                ActorRole.POLLEN_GUARDIAN, "guardian")).isInstanceOf(DomainValidationException.class);
        verify(provider, never()).recordConsumption(any(), any(), any());
    }
    @Test void ambientEntryIsRejectedBeforeAnyProviderReadOrMutation() {
        org.springframework.transaction.support.TransactionSynchronizationManager.setActualTransactionActive(true);
        try {
            assertThatThrownBy(() -> service().recordConsumption(f.plan.id(), UUID.randomUUID(), BigDecimal.ONE,
                    ActorRole.POLLEN_GUARDIAN, "guardian")).isInstanceOf(IllegalStateException.class);
            verifyNoInteractions(provider);
        } finally { org.springframework.transaction.support.TransactionSynchronizationManager.setActualTransactionActive(false); }
    }
}
