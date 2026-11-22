package com.seasonplan.domain;

import com.seasonplan.season.api.ResourceEvidenceSource.Reservation;
import com.seasonplan.season.api.ResourceEvidenceSource.Status;
import com.seasonplan.season.domain.SeasonPlanPolicy;
import com.seasonplan.shared.domain.DomainValidationException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import java.util.Optional;
import java.util.UUID;
import static org.assertj.core.api.Assertions.*;

class ResourceEvidencePolicyTest {
    private final UUID plan = UUID.randomUUID();
    @ParameterizedTest @EnumSource(Status.class)
    void matchingAuthoritativeConfirmationRetainsExistingReplacementSemantics(Status latestStatus) {
        var latest = new Reservation(UUID.randomUUID(), plan, latestStatus, "denied", 1L, false);
        var confirmed = new Reservation(UUID.randomUUID(), plan, Status.CONFIRMED, "accepted", 1L, false);
        assertThat(SeasonPlanPolicy.requireReservation(plan, Optional.of(latest))).isSameAs(latest);
        assertThat(SeasonPlanPolicy.reviewResources(plan, 1, latest, Optional.of(confirmed)).returnForRevision()).isFalse();
    }
    @ParameterizedTest @EnumSource(Status.class)
    void absentConfirmationOnlyReturnsACurrentRejectedPlanForRevision(Status status) {
        var latest = new Reservation(UUID.randomUUID(), plan, status, "capacity unavailable", 1L, false);
        if (status == Status.REJECTED) {
            var decision = SeasonPlanPolicy.reviewResources(plan, 1, latest, Optional.empty());
            assertThat(decision.returnForRevision()).isTrue();
            assertThat(decision.comment()).isEqualTo("Pollen reservation rejected: capacity unavailable");
        } else assertThatThrownBy(() -> SeasonPlanPolicy.reviewResources(plan, 1, latest, Optional.empty())).isInstanceOf(DomainValidationException.class);
    }
    @Test void missingForeignUnconfirmedUnboundAndUnknownEvidenceCannotAuthorizeApproval() {
        var latest = new Reservation(UUID.randomUUID(), plan, Status.PENDING, null, 1L, false);
        assertThatThrownBy(() -> SeasonPlanPolicy.requireReservation(plan, Optional.empty())).isInstanceOf(DomainValidationException.class);
        assertThatThrownBy(() -> SeasonPlanPolicy.requireReservation(plan, Optional.of(new Reservation(UUID.randomUUID(), UUID.randomUUID(), Status.CONFIRMED, null, 1L, false))))
                .isInstanceOf(DomainValidationException.class);
        for (Reservation invalid : new Reservation[]{new Reservation(UUID.randomUUID(), UUID.randomUUID(), Status.CONFIRMED, null, 1L, false),
                new Reservation(UUID.randomUUID(), plan, Status.PENDING, null, 1L, false), new Reservation(UUID.randomUUID(), plan, null, null, 1L, false),
                new Reservation(UUID.randomUUID(), plan, Status.CONFIRMED, null, null, false), new Reservation(UUID.randomUUID(), plan, Status.CONFIRMED, null, 0L, false),
                new Reservation(UUID.randomUUID(), plan, Status.CONFIRMED, null, 1L, true)}) {
            assertThatThrownBy(() -> SeasonPlanPolicy.reviewResources(plan, 1, latest, Optional.of(invalid))).isInstanceOf(DomainValidationException.class);
        }
        var unresolved = new Reservation(latest.id(), plan, Status.PENDING, null, 1L, true);
        assertThatThrownBy(() -> SeasonPlanPolicy.reviewResources(plan, 1, unresolved,
                Optional.of(new Reservation(UUID.randomUUID(), plan, Status.CONFIRMED, null, 1L, false)))).isInstanceOf(DomainValidationException.class);
    }
    @Test void rejectedEvidenceWithoutReasonCannotReturnAPlanForRevision() {
        assertThatThrownBy(() -> SeasonPlanPolicy.reviewResources(plan, 1, new Reservation(UUID.randomUUID(), plan, Status.REJECTED, " ", 1L, false), Optional.empty()))
                .isInstanceOf(DomainValidationException.class);
    }
    @Test void aNewExplicitBindingAtLegacyBaselineZeroIsValidButUnknownLegacyFundingIsNot() {
        var current = new Reservation(UUID.randomUUID(), plan, Status.CONFIRMED, "ok", 0L, false);
        assertThat(SeasonPlanPolicy.reviewResources(plan, 0, current, Optional.of(current)).returnForRevision()).isFalse();
        var unknown = new Reservation(current.id(), plan, Status.CONFIRMED, "ok", null, false);
        assertThatThrownBy(() -> SeasonPlanPolicy.reviewResources(plan, 0, unknown, Optional.of(unknown))).isInstanceOf(DomainValidationException.class);
    }
}
