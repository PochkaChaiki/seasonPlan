package com.seasonplan.lifecycle.domain;

import java.util.Arrays;

public enum MetricDefinition {
    TEMPERATURE("temperature", "CELSIUS"), HUMIDITY("humidity", "PERCENT"), POLLEN_DENSITY("pollen_density", "G_PER_M3");
    private final String key;
    private final String unit;
    MetricDefinition(String key, String unit) { this.key = key; this.unit = unit; }
    public String key() { return key; }
    public String unit() { return unit; }
    public static MetricDefinition forKey(String key) {
        return Arrays.stream(values()).filter(value -> value.key.equals(key)).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("unknown metric: " + key));
    }
}
