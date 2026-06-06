package com.seasonplan.lifecycle.domain;

import java.time.LocalDateTime;

public record TimeWindow(LocalDateTime start, LocalDateTime end) {
    public boolean overlaps(TimeWindow other) {
        return start != null && end != null && other.start != null && other.end != null
                && start.isBefore(other.end) && other.start.isBefore(end);
    }
}
