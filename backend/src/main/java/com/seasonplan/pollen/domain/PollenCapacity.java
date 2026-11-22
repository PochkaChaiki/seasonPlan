package com.seasonplan.pollen.domain;

import java.math.BigDecimal;

/** Read-only external capacity value, independent of the integration client contract. */
public record PollenCapacity(BigDecimal availableGrams, String version) {}
