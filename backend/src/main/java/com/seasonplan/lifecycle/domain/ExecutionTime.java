package com.seasonplan.lifecycle.domain;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

/** PostgreSQL execution records use microsecond precision, including replay comparisons. */
public final class ExecutionTime {
    private ExecutionTime() { }
    public static Instant canonical(Instant instant) {
        return instant == null ? null : instant.truncatedTo(ChronoUnit.MICROS);
    }
}
