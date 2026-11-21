package com.seasonplan.lifecycle.service;

import com.seasonplan.lifecycle.infrastructure.fake.AllowingNaturalCalendarClient;
import com.seasonplan.lifecycle.repository.SeasonPlanRepository;
import com.seasonplan.lifecycle.domain.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class SeasonPlanReviewValidationTest {
    private final SeasonPlanRepository plans = mock(SeasonPlanRepository.class);
    private final SeasonPlanService planning = new SeasonPlanService(plans, null, null, null, null,
            new DirectTestTransaction(), null, new AllowingNaturalCalendarClient(), Clock.systemUTC(), TestExecutionRules::snapshot,
            id -> new RitualReadinessChecker.Readiness(java.util.List.of(), java.util.List.of(), java.util.Set.of("TEST")));
    private final LocalDateTime start = LocalDateTime.parse("2026-03-01T09:00:00");
    private final SeasonPlan plan = plan(UUID.randomUUID(), SeasonPlanStatus.SUBMITTED, "North", start, start.plusHours(3));

    @ParameterizedTest
    @EnumSource(value = SeasonPlanStatus.class, names = {"SUBMITTED", "VERIFIED", "APPROVED", "RITUAL_READY", "ACTIVE"})
    void applicableStatusesBlockOverlappingNormalizedTerritory(SeasonPlanStatus status) {
        when(plans.findAll()).thenReturn(List.of(plan, plan(UUID.randomUUID(), status, " north ", start, start.plusHours(1))));
        assertThatThrownBy(() -> planning.validateForReview(plan)).isInstanceOf(DomainValidationException.class);
    }

    @ParameterizedTest
    @EnumSource(value = SeasonPlanStatus.class, names = {"DRAFT", "COMPLETED", "ARCHIVED"})
    void historicalAndDraftPlansDoNotBlock(SeasonPlanStatus status) {
        when(plans.findAll()).thenReturn(List.of(plan, plan(UUID.randomUUID(), status, "north", start, start.plusHours(1))));
        assertThatCode(() -> planning.validateForReview(plan)).doesNotThrowAnyException();
    }

    @Test
    void selfDifferentTerritoryAndTouchingWindowsDoNotBlock() {
        when(plans.findAll()).thenReturn(List.of(plan,
                plan(UUID.randomUUID(), SeasonPlanStatus.ACTIVE, "south", start, start.plusHours(2)),
                plan(UUID.randomUUID(), SeasonPlanStatus.ACTIVE, "NORTH", start.plusHours(3), start.plusHours(4))));
        assertThatCode(() -> planning.validateForReview(plan)).doesNotThrowAnyException();
    }

    private SeasonPlan plan(UUID id, SeasonPlanStatus status, String territory, LocalDateTime from, LocalDateTime to) {
        return SeasonPlan.rehydrate(id, "SPRING", List.of(territory), List.of(new TimeWindow(from, to)),
                Map.of("bloom", "early"), Map.of("temperature", 20.0, "humidity", 60.0, "pollen_density", 1.0), status, null, List.of(),
                "planner", "planner", Instant.EPOCH, Instant.EPOCH);
    }
}
