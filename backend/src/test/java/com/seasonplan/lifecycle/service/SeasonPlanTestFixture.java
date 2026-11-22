package com.seasonplan.lifecycle.service;

import com.seasonplan.pollen.application.PollenService;
import com.seasonplan.season.application.SeasonPlanService;
import com.seasonplan.work.application.MagicTaskService;
import com.seasonplan.workflows.application.ComputedRitualReadinessChecker;

import com.seasonplan.audit.application.AuditLogService;
import com.seasonplan.notification.api.NotificationOutbox;
import com.seasonplan.reference.adapter.out.fake.AllowingNaturalCalendarClient;
import com.seasonplan.pollen.adapter.out.fake.FakePollenTreeClient;
import com.seasonplan.audit.adapter.out.persistence.InMemoryAuditLogRepository;
import com.seasonplan.work.adapter.out.fake.InMemoryFairyAssignmentRepository;
import com.seasonplan.work.adapter.out.fake.InMemoryMagicTaskRepository;
import com.seasonplan.season.adapter.out.fake.InMemorySeasonPlanRepository;


import com.seasonplan.work.domain.MagicTask;
import com.seasonplan.work.domain.MagicTaskStatus;
import com.seasonplan.season.domain.SeasonPlan;
import com.seasonplan.shared.domain.TimeWindow;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;

public class SeasonPlanTestFixture {
    private final com.seasonplan.season.api.SeasonPlanning seasonPlanning;
    private final com.seasonplan.work.api.WorkPreparation workPreparation;
    private final com.seasonplan.pollen.api.PollenOperations pollenOperations;
    private final com.seasonplan.season.application.port.SeasonPlanRepository seasonRepository;
    private final SeasonPlanService seasonPlanService;
    private final PollenService pollenService;
    private final MagicTaskService magicTaskService;
    private final AuditLogService auditLogService;
    private final List<NotificationOutbox.Event> reviewEvents;

    private SeasonPlanTestFixture(SeasonPlanService seasonPlanService, PollenService pollenService,
                                  MagicTaskService magicTaskService, AuditLogService auditLogService,
                                  List<NotificationOutbox.Event> reviewEvents, com.seasonplan.season.api.SeasonQueries seasons, com.seasonplan.work.api.WorkQueries work, com.seasonplan.season.application.port.SeasonPlanRepository seasonRepository) {
        this.seasonRepository = seasonRepository;
        this.seasonPlanService = seasonPlanService;
        this.pollenService = pollenService;
        this.magicTaskService = magicTaskService;
        this.auditLogService = auditLogService;
        this.reviewEvents = reviewEvents;
        this.seasonPlanning = new com.seasonplan.season.application.SeasonPlanningFacade(seasonPlanService, seasons);
        this.workPreparation = new com.seasonplan.work.application.WorkPreparationFacade(magicTaskService, work);
        this.pollenOperations = new com.seasonplan.pollen.application.PollenOperationsFacade(pollenService);
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
        PollenService pollenService = PollenTestServices.create(new com.seasonplan.season.application.SeasonQueryService(plans), tree,
                () -> event -> pollenEvents.putIfAbsent(event.deduplicationKey(), event), new DirectTestTransaction(), auditLogService, clock);
        var resources = new com.seasonplan.pollen.application.PollenOperationsFacade(pollenService);
        var readiness = new ComputedRitualReadinessChecker(new com.seasonplan.season.application.SeasonQueryService(plans),
                new com.seasonplan.work.application.WorkQueryService(magicTasks, fairyAssignments), resources,
                org.mockito.Mockito.mock(com.seasonplan.response.api.ResponseQueries.class), new com.seasonplan.ritual.domain.RitualReadinessPolicy());
        SeasonPlanService seasonPlanService = new SeasonPlanService(plans, new com.seasonplan.workflows.application.PollenResourceEvidenceSource(resources),
                auditLogService, new DirectTestTransaction(), reviewEvents::add, new com.seasonplan.reference.application.CalendarValidationService(new AllowingNaturalCalendarClient()), clock, TestExecutionRules::snapshot, readiness);
        MagicTaskService magicTaskService = new MagicTaskService(magicTasks, fairyAssignments, new com.seasonplan.season.application.SeasonQueryService(plans), auditLogService, new DirectTestTransaction(), clock);
        return new SeasonPlanTestFixture(seasonPlanService, pollenService, magicTaskService, auditLogService, reviewEvents, new com.seasonplan.season.application.SeasonQueryService(plans), new com.seasonplan.work.application.WorkQueryService(magicTasks, fairyAssignments), plans);
    }

    public com.seasonplan.season.application.port.SeasonPlanRepository seasonRepository() { return seasonRepository; }
    public com.seasonplan.season.api.SeasonPlanning seasonPlanning() { return seasonPlanning; }
    public com.seasonplan.work.api.WorkPreparation workPreparation() { return workPreparation; }
    public com.seasonplan.pollen.api.PollenOperations pollenOperations() { return pollenOperations; }

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
