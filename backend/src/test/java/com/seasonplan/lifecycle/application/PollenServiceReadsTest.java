package com.seasonplan.lifecycle.application;

import com.seasonplan.lifecycle.application.pollen.PollenService;
import com.seasonplan.lifecycle.application.port.*;
import com.seasonplan.lifecycle.service.AuditLogService;
import com.seasonplan.lifecycle.application.shared.PollenTreeUnavailableException;
import com.seasonplan.lifecycle.domain.*;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Clock;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class PollenServiceReadsTest {
    private final UUID planId = UUID.randomUUID();
    private final PollenTreeClient client = mock(PollenTreeClient.class);
    private final SeasonPlanRepository plans = mock(SeasonPlanRepository.class);
    private final AuditLogRepository audits = mock(AuditLogRepository.class);
    private final CommandTransaction transactions = mock(CommandTransaction.class);
    private final PollenService pollen = new PollenService(plans, client,
            () -> { throw new AssertionError("reads must not resolve the consumption outbox"); },
            transactions, new AuditLogService(audits, Clock.systemUTC()), Clock.systemUTC());

    @Test void latestReservationIsAlwaysReadThroughTheFacadeWithoutCachingOrLocalSideEffects() {
        var reservation = new PollenReservation(UUID.randomUUID(), planId, 100);
        when(client.findLatestBySeasonPlanId(planId)).thenReturn(Optional.of(reservation), Optional.empty());
        assertThat(pollen.findLatestBySeasonPlanId(planId)).containsSame(reservation);
        assertThat(pollen.findLatestBySeasonPlanId(planId)).isEmpty();
        verify(client, times(2)).findLatestBySeasonPlanId(planId);
        verifyNoInteractions(plans, audits, transactions);
    }

    @Test void usageReturnsTheDomainSnapshotIncludingSourceVersionAndPreservesAbsence() {
        var summary = new PollenTreeClient.Summary(UUID.randomUUID(), new BigDecimal("100"), new BigDecimal("80"), "external-v7");
        when(client.summary(planId)).thenReturn(Optional.of(summary), Optional.empty());
        assertThat(pollen.usage(planId)).contains(new PollenUsage(summary.reservationId(), summary.confirmedGrams(), summary.actualGrams(), "external-v7"));
        assertThat(pollen.usage(planId)).isEmpty();
        verify(client, times(2)).summary(planId);
        verifyNoInteractions(plans, audits, transactions);
    }

    @Test void externalReadFailuresPropagateRatherThanBecomingMissingData() {
        var failure = new PollenTreeUnavailableException("offline");
        when(client.findLatestBySeasonPlanId(planId)).thenThrow(failure);
        when(client.summary(planId)).thenThrow(failure);
        when(client.capacity()).thenThrow(failure);
        assertThatThrownBy(() -> pollen.findLatestBySeasonPlanId(planId)).isSameAs(failure);
        assertThatThrownBy(() -> pollen.usage(planId)).isSameAs(failure);
        assertThatThrownBy(pollen::capacity).isSameAs(failure);
        verifyNoInteractions(plans, audits, transactions);
    }
}
