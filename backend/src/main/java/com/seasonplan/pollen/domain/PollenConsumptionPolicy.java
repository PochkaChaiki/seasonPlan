package com.seasonplan.pollen.domain;

import com.seasonplan.shared.domain.DomainValidationException;
import java.math.*;
import java.util.*;

/** Existing consumption and warning rules, independent of the resource provider. */
public final class PollenConsumptionPolicy {
    private PollenConsumptionPolicy() { }
    public static void requireConsumptionState(com.seasonplan.season.domain.SeasonPlanStatus status) {
        if (!eligible(status))
            throw new DomainValidationException(Map.of("status", List.of("operation is not allowed in " + status)));
    }
    public static boolean eligible(com.seasonplan.season.domain.SeasonPlanStatus status) {
        return status == com.seasonplan.season.domain.SeasonPlanStatus.ACTIVE;
    }
    public static void requireAgreement(UUID reservationId) {
        if (reservationId == null) throw new DomainValidationException(Map.of("pollenReservation", List.of("confirmed pollen reservation is required")));
    }
    public record Warning(String threshold, boolean escalated) { }
    public static void requirePositive(int amount, String field) {
        if (amount <= 0) throw new DomainValidationException(Map.of(field, List.of("amount must be positive")));
    }
    public static BigDecimal normalize(BigDecimal grams) {
        BigDecimal amount = Objects.requireNonNull(grams).setScale(3, RoundingMode.HALF_UP);
        if (amount.signum() <= 0) throw new IllegalArgumentException("consumption grams must be positive after rounding");
        return amount;
    }
    public static List<Warning> warnings(PollenUsage usage) {
        var result = new ArrayList<Warning>();
        if (usage.actualGrams().compareTo(usage.confirmedGrams().multiply(new BigDecimal("0.8"))) >= 0) result.add(new Warning("80", false));
        if (usage.actualGrams().compareTo(usage.confirmedGrams()) >= 0) result.add(new Warning("100", false));
        if (usage.actualGrams().compareTo(usage.confirmedGrams()) > 0) result.add(new Warning("OVER_100", true));
        return List.copyOf(result);
    }
    public static Set<String> recipients(Warning warning) {
        return warning.escalated() ? Set.of("POLLEN_GUARDIAN", "TALENT_COORDINATOR", "CHIEF_GUARDIAN") : Set.of("POLLEN_GUARDIAN", "TALENT_COORDINATOR");
    }
}
