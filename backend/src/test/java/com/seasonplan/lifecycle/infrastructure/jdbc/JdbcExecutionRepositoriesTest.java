package com.seasonplan.lifecycle.infrastructure.jdbc;

import com.seasonplan.lifecycle.domain.ClimateMetric;
import com.seasonplan.lifecycle.domain.MetricDefinition;
import com.seasonplan.lifecycle.repository.ClimateMetricRepository;
import com.seasonplan.lifecycle.repository.CorrectionActionRepository;
import com.seasonplan.lifecycle.repository.MagicIncidentRepository;
import com.seasonplan.lifecycle.repository.RitualSessionRepository;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.nio.charset.StandardCharsets;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class JdbcExecutionRepositoriesTest {
    @Test void concreteAdaptersImplementAllFourContracts() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        assertThat(new JdbcClimateMetricRepository(jdbc)).isInstanceOf(ClimateMetricRepository.class);
        assertThat(new JdbcCorrectionActionRepository(jdbc)).isInstanceOf(CorrectionActionRepository.class);
        assertThat(new JdbcMagicIncidentRepository(jdbc)).isInstanceOf(MagicIncidentRepository.class);
        assertThat(new JdbcRitualSessionRepository(jdbc)).isInstanceOf(RitualSessionRepository.class);
    }

    @Test void legacyMetricRowsBlockGlobalImportLookup() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        when(jdbc.queryForObject(anyString(), eq(Integer.class))).thenReturn(1);
        assertThatThrownBy(() -> new JdbcClimateMetricRepository(jdbc).byImportKey("source", "event"))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("operator remediation");
    }

    @Test void legacyRitualRowsBlockAffectedPlanReads() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        when(jdbc.queryForObject(anyString(), eq(Integer.class), any(UUID.class))).thenReturn(1);
        assertThatThrownBy(() -> new JdbcRitualSessionRepository(jdbc).forPlan(UUID.randomUUID()))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("operator remediation");
    }

    @Test void metricReplayUsesNumericAndMicrosecondSemanticEquality() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        UUID plan = UUID.randomUUID(), id = UUID.randomUUID();
        Instant original = Instant.parse("2026-01-02T03:04:05.123456789Z");
        ClimateMetric stored = new ClimateMetric(id, plan, "source", "event", MetricDefinition.TEMPERATURE,
                new BigDecimal("20.0"), new BigDecimal("19.00"), new BigDecimal("2.0"), null,
                Instant.parse("2026-01-02T03:04:05.123456Z"));
        ClimateMetric replay = new ClimateMetric(id, plan, "source", "event", MetricDefinition.TEMPERATURE,
                new BigDecimal("20.00"), new BigDecimal("19"), new BigDecimal("2"), null, original);
        when(jdbc.queryForObject(anyString(), eq(Integer.class))).thenReturn(0);
        when(jdbc.update(anyString(), any(Object[].class))).thenReturn(0);
        when(jdbc.query(anyString(), org.mockito.ArgumentMatchers.<org.springframework.jdbc.core.RowMapper<ClimateMetric>>any(), any(Object[].class))).thenReturn(List.of(stored));
        when(jdbc.queryForObject(anyString(), eq(Integer.class), any(Object[].class))).thenReturn(0);
        new JdbcClimateMetricRepository(jdbc).save(replay);
        ClimateMetric conflicting = new ClimateMetric(id, plan, "source", "event", MetricDefinition.TEMPERATURE,
                new BigDecimal("21"), replay.target(), replay.tolerance(), null, original);
        assertThatThrownBy(() -> new JdbcClimateMetricRepository(jdbc).save(conflicting))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("conflicting metric");
    }

    @Test void migrationIsForwardOnlyAndContainsCriticalDatabaseGuards() throws Exception {
        String sql;
        try (var input = getClass().getResourceAsStream("/db/changelog/changes/012-execution-repositories.sql")) {
            assertThat(input).isNotNull();
            sql = new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
        assertThat(sql).contains("climate_metrics_import_key_unique", "correction_actions_dedupe_unique",
                "correction_actions_incident_plan_fk", "ritual_sessions_active_unique", "ritual_sessions_success_unique",
                "ritual_sessions_attempt_unique", "ritual_participants_immutable", "ritual_events_immutable")
                .doesNotContainIgnoringCase("truncate ", "delete from ");
    }
}
