package com.seasonplan.pollen.domain;

import com.seasonplan.shared.domain.DomainValidationException;
import java.math.BigDecimal;
import java.util.*;

/** Pure limited-resource check used by the educational provider; not an application stock ledger. */
public final class PollenCapacityPolicy {
    private PollenCapacityPolicy() { }
    public static BigDecimal available(BigDecimal total, Collection<PollenReservation> reservations) {
        BigDecimal reserved = reservations.stream().filter(value -> value.status() == PollenReservationStatus.CONFIRMED)
                .map(value -> BigDecimal.valueOf(value.confirmedAmount())).reduce(BigDecimal.ZERO, BigDecimal::add);
        return total.subtract(reserved);
    }
    public static void requireAvailable(int amount, String field, BigDecimal available) {
        PollenConsumptionPolicy.requirePositive(amount, field);
        if (BigDecimal.valueOf(amount).compareTo(available) > 0)
            throw new DomainValidationException(Map.of(field, List.of("insufficient Pollen Tree capacity")));
    }
}
