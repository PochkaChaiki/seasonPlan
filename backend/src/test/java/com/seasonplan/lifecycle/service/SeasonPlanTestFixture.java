package com.seasonplan.lifecycle.service;

import com.seasonplan.lifecycle.notification.NotificationOutbox;
import com.seasonplan.lifecycle.infrastructure.fake.AllowingNaturalCalendarClient;
import com.seasonplan.lifecycle.infrastructure.fake.FakePollenTreeClient;
import com.seasonplan.lifecycle.infrastructure.fake.InMemoryAuditLogRepository;
import com.seasonplan.lifecycle.infrastructure.fake.InMemoryFairyAssignmentRepository;
import com.seasonplan.lifecycle.infrastructure.fake.InMemoryMagicTaskRepository;
import com.seasonplan.lifecycle.infrastructure.fake.InMemorySeasonPlanRepository;


import com.seasonplan.lifecycle.infrastructure.fake.*;
import com.seasonplan.lifecycle.domain.MagicTask;
import com.seasonplan.lifecycle.domain.MagicTaskStatus;
import com.seasonplan.lifecycle.domain.SeasonPlan;
import com.seasonplan.lifecycle.domain.TimeWindow;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;

public class SeasonPlanTestFixture {
    private final SeasonPlanService seasonPlanService;
    private final PollenService pollenService;
    private final MagicTaskService magicTaskService;
    private final AuditLogService auditLogService;
    private final List<NotificationOutbox.Event> reviewEvents;

    private SeasonPlanTestFixture(SeasonPlanService seasonPlanService, PollenService pollenService,
                                  MagicTaskService magicTaskService, AuditLogService auditLogService,
                                  List<NotificationOutbox.Event> reviewEvents) {
        this.seasonPlanService = seasonPlanService;
        this.pollenService = pollenService;
        this.magicTaskService = magicTaskService;
        this.auditLogService = auditLogService;
        this.reviewEvents = reviewEvents;
    }

    public static SeasonPlanTestFixture create() {
        Clock clock = Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"), ZoneOffset.UTC);
        InMemorySeasonPlanRepository plans = new InMemorySeasonPlanRepository();
        FakePollenTreeClient tree = new FakePollenTreeClient(new java.math.BigDecimal("10000"));
        InMemoryMagicTaskRepository magicTasks = new InMemoryMagicTaskRepository();
        InMemoryFairyAssignmentRepository fairyAssignments = new InMemoryFairyAssignmentRepository();
        AuditLogService auditLogService = new AuditLogService(new InMemoryAuditLogRepository(), clock);
        List<NotificationOutbox.Event> reviewEvents = new java.util.ArrayList<>();
        Map<String, NotificationOutbox.Event> pollenEvents = new java.util.LinkedHashMap<>();
        PollenService pollenService = new PollenService(plans, tree,
                () -> event -> pollenEvents.putIfAbsent(event.deduplicationKey(), event), new DirectTestTransaction(), auditLogService, clock);
        SeasonPlanService seasonPlanService = new SeasonPlanService(plans, pollenService, magicTasks, fairyAssignments,
                auditLogService, new DirectTestTransaction(), reviewEvents::add, new AllowingNaturalCalendarClient(), clock, TestExecutionRules::snapshot,
                new ComputedRitualReadinessChecker(plans, magicTasks, fairyAssignments, pollenService, org.mockito.Mockito.mock(com.seasonplan.lifecycle.repository.MagicIncidentRepository.class)));
        MagicTaskService magicTaskService = new MagicTaskService(magicTasks, fairyAssignments, plans, auditLogService, new DirectTestTransaction(), clock);
        return new SeasonPlanTestFixture(seasonPlanService, pollenService, magicTaskService, auditLogService, reviewEvents);
    }

    public static SeasonPlan validPlanDraft(String createdBy) {
        SeasonPlan plan = new SeasonPlan();
        plan.setSeasonType("SPRING");
        plan.setTargetTerritories(List.of("north-grove"));
        plan.setTimeWindows(List.of(new TimeWindow(LocalDateTime.parse("2026-03-01T09:00:00"), LocalDateTime.parse("2026-03-01T12:00:00"))));
        plan.setNaturalPhenomenonParameters(Map.of("bloom", "early"));
        plan.setTargetClimateMetrics(Map.of("temperature", 17.5, "humidity", 60.0, "pollen_density", 1.0));
        plan.setCreatedBy(createdBy);
        return plan;
    }

    public static SeasonPlan invalidPlanDraft() {
        return new SeasonPlan();
    }

    public static MagicTask validTaskDraft() {
        return new MagicTask(
                "Prepare weather lattice",
                "Align the seasonal wind pattern",
                "SPRING_WEATHER",
                new TimeWindow(LocalDateTime.parse("2026-03-01T09:00:00"), LocalDateTime.parse("2026-03-01T10:00:00")),
                MagicTaskStatus.READY
        );
    }

    public SeasonPlanService seasonPlanService() { return seasonPlanService; }
    public PollenService pollenService() { return pollenService; }
    public MagicTaskService magicTaskService() { return magicTaskService; }
    public AuditLogService auditLogService() { return auditLogService; }
    public List<NotificationOutbox.Event> reviewEvents() { return List.copyOf(reviewEvents); }
}
