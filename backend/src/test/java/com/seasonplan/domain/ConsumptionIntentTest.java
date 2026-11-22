package com.seasonplan.domain;

import com.seasonplan.pollen.domain.ConsumptionIntent;
import com.seasonplan.pollen.application.port.PollenTreeClient;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class ConsumptionIntentTest {
    @Test void receiptSurvivesUncertaintyAndCannotBeReplaced() {
        var intent = intent();
        var receipt = new ConsumptionIntent.Receipt(intent.planId(), intent.clientKey(), intent.amount(),
                UUID.randomUUID(), new BigDecimal("100"), new BigDecimal("80"), "v1");
        var known = intent.received(receipt);
        assertThat(known.uncertain()).isEqualTo(known);
        assertThat(known.received(receipt)).isEqualTo(known);
        assertThatThrownBy(() -> known.received(new ConsumptionIntent.Receipt(intent.planId(), intent.clientKey(), intent.amount(),
                receipt.reservationId(), receipt.confirmedGrams(), new BigDecimal("90"), "v2"))).isInstanceOf(RuntimeException.class);
    }
    @Test void mismatchedAcceptedFactIsRetainedButQuarantined() {
        var intent = intent();
        var receipt = new ConsumptionIntent.Receipt(UUID.randomUUID(), intent.clientKey(), intent.amount(),
                UUID.randomUUID(), new BigDecimal("100"), new BigDecimal("80"), "v1");
        var known = intent.received(receipt);
        assertThat(known.receipt()).isEqualTo(receipt);
        assertThat(known.apply(1, true).state()).isEqualTo(ConsumptionIntent.State.QUARANTINED);
    }
    @Test void payloadReplayCannotChangeActorAmountOrPlanAndObsoleteEvidenceIsRetained() {
        var intent = intent();
        assertThatThrownBy(() -> intent.requireSame(intent.planId(), intent.amount(), "other")).isInstanceOf(RuntimeException.class);
        assertThatThrownBy(() -> intent.requireSame(UUID.randomUUID(), intent.amount(), "guardian")).isInstanceOf(RuntimeException.class);
        assertThatThrownBy(() -> intent.requireSame(intent.planId(), BigDecimal.ONE, "guardian")).isInstanceOf(RuntimeException.class);
        var known = intent.received(new ConsumptionIntent.Receipt(intent.planId(), intent.clientKey(), intent.amount(),
                UUID.randomUUID(), new BigDecimal("100"), new BigDecimal("80"), "v1"));
        assertThat(known.apply(2, true).state()).isEqualTo(ConsumptionIntent.State.QUARANTINED);
    }
    private ConsumptionIntent intent() {
        return new ConsumptionIntent(UUID.randomUUID(), UUID.randomUUID(), 1, UUID.randomUUID(), new BigDecimal("80.000"),
                "guardian", null, ConsumptionIntent.State.DISPATCHED, null, Instant.now());
    }
}
