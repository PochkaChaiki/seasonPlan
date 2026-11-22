package com.seasonplan.lifecycle.service;

import com.seasonplan.pollen.api.*;
import com.seasonplan.pollen.domain.PollenReservationStatus;
import com.seasonplan.season.api.ResourceEvidenceSource;
import com.seasonplan.workflows.application.PollenResourceEvidenceSource;
import org.junit.jupiter.api.Test;
import java.util.Optional;
import java.util.UUID;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class ResourceEvidenceSourceTest {
    @Test void factCollectionRetainsSeparateLatestAndAuthorityQueries() {
        UUID plan = UUID.randomUUID(), reservation = UUID.randomUUID();
        var pollen = mock(PollenOperations.class);
        when(pollen.findLatestBySeasonPlanId(plan)).thenReturn(Optional.of(new ReservationFacts(reservation, plan, 100, null, PollenReservationStatus.REJECTED, "denied", "guardian", 7L, true)));
        when(pollen.findAuthoritativeConfirmedBySeasonPlanId(plan)).thenReturn(Optional.of(new ReservationFacts(UUID.randomUUID(), plan, 80, 80, PollenReservationStatus.CONFIRMED, "accepted", "guardian", 7L, true)));
        var source = new PollenResourceEvidenceSource(pollen);
        assertThat(source.latestFor(plan)).contains(new ResourceEvidenceSource.Reservation(reservation, plan, ResourceEvidenceSource.Status.REJECTED, "denied", 7L, true));
        verify(pollen).findLatestBySeasonPlanId(plan);
        verifyNoMoreInteractions(pollen);
        assertThat(source.confirmedFor(plan).orElseThrow().status()).isEqualTo(ResourceEvidenceSource.Status.CONFIRMED);
        verify(pollen).findAuthoritativeConfirmedBySeasonPlanId(plan);
        verifyNoMoreInteractions(pollen);
    }
    @Test void unavailableAuthorityIsNotReplacedWithCachedOrInventedEvidence() {
        UUID plan = UUID.randomUUID();
        var pollen = mock(PollenOperations.class);
        var failure = new PollenTreeUnavailableException("unavailable");
        when(pollen.findAuthoritativeConfirmedBySeasonPlanId(plan)).thenThrow(failure);
        assertThatThrownBy(() -> new PollenResourceEvidenceSource(pollen).confirmedFor(plan)).isSameAs(failure);
        verify(pollen).findAuthoritativeConfirmedBySeasonPlanId(plan);
        verifyNoMoreInteractions(pollen);
    }
}
