package com.seasonplan.domain;

import com.seasonplan.pollen.domain.*;
import com.seasonplan.pollen.domain.PollenIntent.*;
import org.junit.jupiter.api.Test;
import java.time.Instant;
import java.util.*;
import static org.assertj.core.api.Assertions.*;

class PollenIntentInvariantTest {
    private final UUID plan = UUID.randomUUID(), reservation = UUID.randomUUID(), id = UUID.randomUUID();
    private Payload payload(Kind kind) { return new Payload(kind, plan, 1, UUID.randomUUID(), kind == Kind.REQUEST ? UUID.randomUUID() : null, kind == Kind.REQUEST ? null : reservation, kind == Kind.REJECT ? null : 100, "reason", "original-actor"); }
    private ReservationReceipt receipt(UUID p, PollenReservationStatus status) { return new ReservationReceipt(reservation, p, 100, status == PollenReservationStatus.CONFIRMED ? 80 : null, status, status == PollenReservationStatus.PENDING ? null : "reason", status == PollenReservationStatus.PENDING ? null : "provider-actor"); }
    @Test void constructorsEnforceTheSameIdentityAndDecisionRulesAsReceived() {
        var payload = payload(Kind.CONFIRM);
        for (var invalid : List.of(receipt(UUID.randomUUID(), PollenReservationStatus.CONFIRMED), receipt(plan, PollenReservationStatus.REJECTED))) {
            assertThatThrownBy(() -> new PollenIntent(id, payload, State.RECEIVED, invalid, null, Instant.now())).isInstanceOf(RuntimeException.class);
        }
        assertThatThrownBy(() -> new PollenIntent(id, payload, State.DISPATCHED, receipt(plan, PollenReservationStatus.CONFIRMED), null, Instant.now())).isInstanceOf(RuntimeException.class);
        assertThatThrownBy(() -> new PollenIntent(id, payload, State.DENIED, null, null, Instant.now())).isInstanceOf(RuntimeException.class);
        assertThatThrownBy(() -> new PollenIntent(id, payload, State.UNKNOWN, null, "denied", Instant.now())).isInstanceOf(RuntimeException.class);
    }
    @Test void knownReceiptsCannotChangeAndObsoleteResultsNeverReturnCurrentFunding() {
        var initial = new PollenIntent(id, payload(Kind.CONFIRM), State.DISPATCHED, null, null, Instant.now());
        var evidence = receipt(plan, PollenReservationStatus.CONFIRMED);
        var received = initial.uncertain().received(evidence);
        assertThat(received.uncertain()).isSameAs(received);
        assertThat(received.received(evidence)).isSameAs(received);
        assertThat(received.applied(2, false).nextStep()).isEqualTo(Step.OBSOLETE);
        assertThat(received.applied(1, true).nextStep()).isEqualTo(Step.OBSOLETE);
        assertThat(received.applied(1, false).nextStep()).isEqualTo(Step.RETURN);
        assertThatThrownBy(() -> received.denied("capacity")).isInstanceOf(RuntimeException.class);
        assertThatThrownBy(() -> initial.applied(1, false)).isInstanceOf(RuntimeException.class);
    }
    @Test void replayInputIsImmutableButDoesNotRetrofitCapturedRevisionOrProviderKey() {
        var original = payload(Kind.REQUEST);
        var retry = new Payload(Kind.REQUEST, plan, 2, original.clientKey(), UUID.randomUUID(), null, 100, "reason", "original-actor");
        assertThatCode(() -> original.requireSameInput(retry)).doesNotThrowAnyException();
        for (var changed : List.of(
                new Payload(Kind.REQUEST, plan, 1, original.clientKey(), original.providerKey(), null, 101, "reason", "original-actor"),
                new Payload(Kind.REQUEST, plan, 1, original.clientKey(), original.providerKey(), null, 100, "reason", "different-actor"))) {
            assertThatThrownBy(() -> original.requireSameInput(changed)).isInstanceOf(com.seasonplan.shared.domain.DomainValidationException.class);
        }
        assertThat(original.materialRevision()).isEqualTo(1);
        assertThat(original.providerKey()).isNotEqualTo(retry.providerKey());
    }

    @Test void bindingMustUseItsOwnAcceptedRequestEvidence() {
        var request = new PollenIntent(id, payload(Kind.REQUEST), State.DISPATCHED, null, null, Instant.now());
        var binding = new ReservationBinding(reservation, plan, 1, id);
        assertThatThrownBy(() -> binding.requireRequestEvidence(request)).isInstanceOf(IllegalStateException.class);
        var received = request.received(receipt(plan, PollenReservationStatus.PENDING));
        assertThatCode(() -> binding.requireRequestEvidence(received)).doesNotThrowAnyException();
        assertThatThrownBy(() -> new ReservationBinding(UUID.randomUUID(), plan, 1, id).requireRequestEvidence(received)).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> new ReservationBinding(reservation, plan, 2, id).requireRequestEvidence(received)).isInstanceOf(IllegalStateException.class);
        var decision = new PollenIntent(id, payload(Kind.CONFIRM), State.RECEIVED, receipt(plan, PollenReservationStatus.CONFIRMED), null, Instant.now());
        assertThatThrownBy(() -> binding.requireRequestEvidence(decision)).isInstanceOf(IllegalStateException.class);
    }

}
