package com.seasonplan.lifecycle.adapter.fake;

import com.seasonplan.lifecycle.domain.*;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.*;

import static org.assertj.core.api.Assertions.*;

class FakePollenTreeClientTest {
    private final FakePollenTreeClient tree = new FakePollenTreeClient(new BigDecimal("2500"));
    private final UUID plan = UUID.randomUUID();

    private PollenReservation request(UUID planId, int amount) {
        return tree.requestReservation(planId, UUID.randomUUID(), amount, "guardian");
    }

    @Test void capacityIsSharedAcrossPlansAndDeniedConfirmationIsAtomic() {
        PollenReservation first = request(plan, 2000);
        PollenReservation second = request(UUID.randomUUID(), 2000);
        String before = tree.capacity().version();
        tree.confirmReservation(first.id(), 2000, "ok", "guardian");
        assertThat(tree.capacity().availableGrams()).isEqualByComparingTo("500");
        assertThat(tree.capacity().version()).isNotEqualTo(before);
        String version = tree.capacity().version();
        assertThatThrownBy(() -> tree.confirmReservation(second.id(), 501, "too much", "guardian")).isInstanceOf(DomainValidationException.class);
        assertThat(tree.findById(second.id()).orElseThrow().status()).isEqualTo(PollenReservationStatus.PENDING);
        assertThat(tree.capacity().version()).isEqualTo(version);
        assertThatThrownBy(() -> request(UUID.randomUUID(), 501)).isInstanceOf(DomainValidationException.class);
        tree.confirmReservation(second.id(), 500, "ok", "guardian");
        assertThat(tree.capacity().availableGrams()).isEqualByComparingTo("0");
    }

    @Test void concurrentConfirmationsCannotOverbookCapacity() throws Exception {
        PollenReservation one = request(plan, 2000);
        PollenReservation two = request(UUID.randomUUID(), 2000);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);
        try {
            List<Future<Boolean>> results = new ArrayList<>();
            for (PollenReservation reservation : List.of(one, two)) {
                results.add(executor.submit(() -> {
                    start.await();
                    try { tree.confirmReservation(reservation.id(), 2000, "ok", "guardian"); return true; }
                    catch (DomainValidationException denied) { return false; }
                }));
            }
            start.countDown();
            assertThat(List.of(results.get(0).get(5, TimeUnit.SECONDS), results.get(1).get(5, TimeUnit.SECONDS)))
                    .containsExactlyInAnyOrder(true, false);
            assertThat(tree.capacity().availableGrams()).isEqualByComparingTo("500");
        } finally { executor.shutdownNow(); }
    }

    @Test void requestCorrelationIsIdempotentRejectsChangedPayloadAndKeepsLatestOrder() {
        UUID requestId = UUID.randomUUID();
        PollenReservation first = tree.requestReservation(plan, requestId, 100, "guardian");
        PollenReservation newer = request(plan, 200);
        PollenReservation retry = tree.requestReservation(plan, requestId, 100, "guardian");
        assertThat(retry.id()).isEqualTo(first.id());
        assertThat(tree.findLatestBySeasonPlanId(plan).orElseThrow().id()).isEqualTo(newer.id());
        assertThatThrownBy(() -> tree.requestReservation(plan, requestId, 101, "guardian")).isInstanceOf(DomainValidationException.class);
        assertThatThrownBy(() -> tree.requestReservation(UUID.randomUUID(), requestId, 100, "guardian")).isInstanceOf(DomainValidationException.class);
        assertThatThrownBy(() -> tree.requestReservation(plan, requestId, 100, "another")).isInstanceOf(DomainValidationException.class);
        FakePollenTreeClient sameInputs = new FakePollenTreeClient(new BigDecimal("2500"));
        assertThat(sameInputs.requestReservation(plan, requestId, 100, "guardian").id()).isEqualTo(first.id());
    }

    @Test void newerConfirmedReservationRemainsAuthoritativeWhenOlderDraftConfirmsLater() {
        var older = request(plan, 100);
        var newer = request(plan, 100);
        tree.confirmReservation(newer.id(), 100, "ok", "guardian");
        tree.confirmReservation(older.id(), 100, "ok", "guardian");
        assertThat(tree.findAuthoritativeConfirmedBySeasonPlanId(plan).orElseThrow().id()).isEqualTo(newer.id());
    }

    @Test void snapshotsAreDetachedAndTerminalReservationsCannotChange() {
        assertThat(tree.findById(UUID.randomUUID())).isEmpty();
        assertThat(tree.findLatestBySeasonPlanId(plan)).isEmpty();
        PollenReservation first = request(plan, 100);
        first.confirm(9999, "forged", "attacker");
        PollenReservation read = tree.findById(first.id()).orElseThrow();
        assertThat(read.status()).isEqualTo(PollenReservationStatus.PENDING);
        read.reject("forged", "attacker");
        PollenReservation confirmed = tree.confirmReservation(first.id(), 100, "ok", "guardian");
        confirmed.reject("forged", "attacker");
        assertThat(tree.findById(first.id()).orElseThrow().status()).isEqualTo(PollenReservationStatus.CONFIRMED);
        assertThatThrownBy(() -> tree.rejectReservation(first.id(), "reason", "guardian")).isInstanceOf(DomainValidationException.class);
        assertThatThrownBy(() -> tree.confirmReservation(first.id(), 200, "reason", "guardian")).isInstanceOf(DomainValidationException.class);
        assertThat(tree.capacity().availableGrams()).isEqualByComparingTo("2400");
    }

    @Test void rejectedReasonIsRequiredAndInvalidAmountsNeverReserveCapacity() {
        assertThatThrownBy(() -> request(plan, 0)).isInstanceOf(DomainValidationException.class);
        PollenReservation first = request(plan, 100);
        assertThatThrownBy(() -> tree.rejectReservation(first.id(), " ", "guardian")).isInstanceOf(DomainValidationException.class);
        assertThatThrownBy(() -> tree.confirmReservation(first.id(), 0, "ok", "guardian")).isInstanceOf(DomainValidationException.class);
        assertThat(tree.findById(first.id()).orElseThrow().status()).isEqualTo(PollenReservationStatus.PENDING);
        PollenReservation rejected = tree.rejectReservation(first.id(), "not needed", "guardian");
        assertThat(rejected.comment()).isEqualTo("not needed");
        assertThat(rejected.confirmedBy()).isEqualTo("guardian");
        assertThat(rejected.status()).isEqualTo(PollenReservationStatus.REJECTED);
        assertThatThrownBy(() -> tree.confirmReservation(first.id(), 100, "ok", "guardian")).isInstanceOf(DomainValidationException.class);
        assertThat(tree.capacity().availableGrams()).isEqualByComparingTo("2500");
    }

    @Test void consumptionRetryReturnsOriginalSnapshotAndUsesPlanScopedEventKeys() {
        PollenReservation first = request(plan, 100);
        assertThat(tree.summary(plan)).isEmpty();
        tree.confirmReservation(first.id(), 100, "ok", "guardian");
        assertThat(tree.summary(plan).orElseThrow().actualGrams()).isEqualByComparingTo("0");
        UUID event = UUID.randomUUID();
        var original = tree.recordConsumption(plan, event, new BigDecimal("80"));
        tree.recordConsumption(plan, UUID.randomUUID(), new BigDecimal("30"));
        assertThat(tree.recordConsumption(plan, event, new BigDecimal("80.000"))).isEqualTo(original);
        assertThat(tree.summary(plan).orElseThrow().actualGrams()).isEqualByComparingTo("110");
        assertThatThrownBy(() -> tree.recordConsumption(plan, event, new BigDecimal("81"))).isInstanceOf(DomainValidationException.class);
        UUID secondPlan = UUID.randomUUID();
        var second = request(secondPlan, 100);
        tree.confirmReservation(second.id(), 100, "ok", "guardian");
        assertThat(tree.recordConsumption(secondPlan, event, BigDecimal.ONE).actualGrams()).isEqualByComparingTo("1");
        assertThat(tree.recordConsumption(plan, event, new BigDecimal("80"))).isEqualTo(original);
        assertThat(tree.capacity().availableGrams()).isEqualByComparingTo("2300");
    }

    @Test void invalidConsumptionOrMissingConfirmationNeverCreatesUsage() {
        assertThatThrownBy(() -> tree.recordConsumption(plan, UUID.randomUUID(), BigDecimal.ONE)).isInstanceOf(DomainValidationException.class);
        var reservation = request(plan, 100);
        tree.confirmReservation(reservation.id(), 100, "ok", "guardian");
        assertThatThrownBy(() -> tree.recordConsumption(plan, UUID.randomUUID(), new BigDecimal("0.0001"))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> tree.recordConsumption(plan, UUID.randomUUID(), BigDecimal.ONE.negate())).isInstanceOf(IllegalArgumentException.class);
        assertThat(tree.summary(plan).orElseThrow().actualGrams()).isEqualByComparingTo("0");
    }
}
