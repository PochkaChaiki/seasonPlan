package com.seasonplan.domain;

import com.seasonplan.pollen.domain.*;
import com.seasonplan.shared.domain.DomainValidationException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import java.util.UUID;
import static org.assertj.core.api.Assertions.*;

class PollenReservationInvariantTest {
    @ParameterizedTest @ValueSource(ints = {0, -1})
    void nonpositiveConfirmationCannotChangePendingState(int amount) {
        var reservation = pending();
        assertThatThrownBy(() -> reservation.confirm(amount, "ok", "guardian")).isInstanceOf(DomainValidationException.class);
        assertThat(reservation.status()).isEqualTo(PollenReservationStatus.PENDING);
    }
    @Test void rejectionRequiresAReasonAndDecisionMaker() {
        var reservation = pending();
        assertThatThrownBy(() -> reservation.reject(" ", "guardian")).isInstanceOf(DomainValidationException.class);
        assertThatThrownBy(() -> reservation.reject("not enough", " ")).isInstanceOf(DomainValidationException.class);
        assertThat(reservation.status()).isEqualTo(PollenReservationStatus.PENDING);
    }
    @Test void exactDecisionReplayIsSafeButTerminalDecisionCannotChange() {
        var reservation = pending(); reservation.confirm(100, "ok", "guardian");
        assertThatCode(() -> reservation.confirm(100, "ok", "guardian")).doesNotThrowAnyException();
        assertThatThrownBy(() -> reservation.confirm(101, "ok", "guardian")).isInstanceOf(DomainValidationException.class);
        assertThatThrownBy(() -> reservation.confirm(100, "ok", "someone-else")).isInstanceOf(DomainValidationException.class);
        assertThatThrownBy(() -> reservation.reject("changed", "guardian")).isInstanceOf(DomainValidationException.class);
        assertThat(reservation.confirmedAmount()).isEqualTo(100);
        assertThat(reservation.status()).isEqualTo(PollenReservationStatus.CONFIRMED);
    }
    @Test void rejectedDecisionCannotBecomeAConfirmation() {
        var reservation = pending(); reservation.reject("not available", "guardian");
        assertThatCode(() -> reservation.reject("not available", "guardian")).doesNotThrowAnyException();
        assertThatThrownBy(() -> reservation.reject("new reason", "guardian")).isInstanceOf(DomainValidationException.class);
        assertThatThrownBy(() -> reservation.confirm(100, "ok", "guardian")).isInstanceOf(DomainValidationException.class);
        assertThat(reservation.status()).isEqualTo(PollenReservationStatus.REJECTED);
    }
    private static PollenReservation pending() { return new PollenReservation(UUID.randomUUID(), UUID.randomUUID(), 100); }
}
