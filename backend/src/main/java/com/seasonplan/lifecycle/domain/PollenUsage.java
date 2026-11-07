package com.seasonplan.lifecycle.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;
import java.util.UUID;

/** An immutable authoritative Pollen Tree snapshot, never a local reservation update. */
public record PollenUsage(UUID reservationId, BigDecimal confirmedGrams, BigDecimal actualGrams, String sourceVersion) {
    public PollenUsage {
        Objects.requireNonNull(reservationId); Objects.requireNonNull(confirmedGrams); Objects.requireNonNull(actualGrams);
        if (confirmedGrams.signum() <= 0 || actualGrams.signum() < 0 || sourceVersion == null || sourceVersion.isBlank()) {
            throw new IllegalArgumentException("valid authoritative pollen totals are required");
        }
        confirmedGrams = confirmedGrams.setScale(3, RoundingMode.HALF_UP);
        actualGrams = actualGrams.setScale(3, RoundingMode.HALF_UP);
        if (confirmedGrams.signum() <= 0) throw new IllegalArgumentException("confirmed grams round to zero");
    }
    public BigDecimal variance() { return actualGrams.subtract(confirmedGrams); }
    public BigDecimal consumptionRatio() { return actualGrams.divide(confirmedGrams, 4, RoundingMode.HALF_UP); }
}
