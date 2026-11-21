package com.seasonplan.lifecycle.service;

import com.seasonplan.lifecycle.domain.*;
import org.junit.jupiter.api.Test;
import java.time.Instant;
import java.util.UUID;
import static org.assertj.core.api.Assertions.*;

class ReadinessMetadataTest {
    @Test void completedTaskCannotBeEditedAndKeepsExplicitCategory() {
        MagicTask task = new MagicTask("work", "description", "weather", null, MagicTaskStatus.COMPLETED, "WEATHER");
        assertThat(task.category()).isEqualTo("WEATHER");
        assertThatThrownBy(() -> task.updateFrom(new MagicTask("changed", "description", "weather", null, MagicTaskStatus.READY, "WEATHER")))
                .isInstanceOf(IllegalStateException.class);
    }
    @Test void confirmedAssignmentRetainsRealTimestampAtPostgresPrecision() {
        var assignment = new FairyAssignment(UUID.randomUUID(), UUID.randomUUID(), "fairy", "RITUAL_LEAD", "weather", AssignmentStatus.CONFIRMED, Instant.parse("2026-01-01T00:00:00.123456789Z"));
        assertThat(assignment.confirmedAt()).isEqualTo(Instant.parse("2026-01-01T00:00:00.123456Z"));
    }
}
