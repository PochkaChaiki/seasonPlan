package com.seasonplan.lifecycle.domain;

import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import static org.assertj.core.api.Assertions.*;

class ExecutionDomainTest {
    @Test void metricUsesPositiveToleranceCatalogUnitsAndFourDecimalRounding() {
        assertThat(ClimateMetric.score(new BigDecimal("23"), new BigDecimal("20"), new BigDecimal("2")))
                .isEqualByComparingTo("1.5000");
        assertThat(ClimateMetric.score(BigDecimal.ONE, BigDecimal.ZERO, new BigDecimal("3")))
                .isEqualByComparingTo("0.3333");
        assertThatThrownBy(() -> ClimateMetric.score(BigDecimal.ONE, BigDecimal.ZERO, BigDecimal.ZERO))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(MetricDefinition.forKey("humidity").unit()).isEqualTo("PERCENT");
    }

    @Test void actionTransitionsAndDeadlineBoundaryAreExplicit() {
        Instant now = Instant.parse("2026-01-01T00:00:00Z");
        CorrectionAction action = CorrectionAction.open(UUID.randomUUID(), UUID.randomUUID(), null, "manual-1", "Исправить", "owner", now);
        assertThat(action.overdue(now)).isFalse();
        assertThat(action.overdue(now.plusNanos(1))).isTrue();
        assertThatThrownBy(() -> action.transition(CorrectionAction.Status.DONE, null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> action.transition(CorrectionAction.Status.CANCELLED, " ")).isInstanceOf(IllegalArgumentException.class);
        CorrectionAction done = action.transition(CorrectionAction.Status.IN_PROGRESS, null).transition(CorrectionAction.Status.DONE, null);
        assertThat(done.overdue(now.plusSeconds(10))).isFalse();
        assertThatThrownBy(() -> done.transition(CorrectionAction.Status.OPEN, null)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test void ritualCannotFinishBeforeItsLastEvent() {
        Instant now = Instant.parse("2026-01-01T00:00:00Z");
        RitualSession session = RitualSession.start(UUID.randomUUID(), UUID.randomUUID(), 1, "coordinator", now,
                List.of(new RitualSession.Participant("fairy", "LEAD", now)))
                .addEvent(new RitualSession.Event(now.plusSeconds(1), "VALIDATED", "ok"));
        assertThatThrownBy(() -> session.succeed(now)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test void ritualCopiesSnapshotsAndRejectsEventsAfterCompletion() {
        Instant now = Instant.parse("2026-01-01T00:00:00Z");
        RitualSession session = RitualSession.start(UUID.randomUUID(), UUID.randomUUID(), 1, "coordinator", now,
                List.of(new RitualSession.Participant("fairy", "LEAD", now)));
        RitualSession failed = session.fail("FAILURE", "Ошибка", now);
        assertThatThrownBy(() -> failed.addEvent(new RitualSession.Event(now, "START", "payload")))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> session.participants().clear()).isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> session.fail(" ", "Ошибка", now)).isInstanceOf(IllegalArgumentException.class);
    }
}
