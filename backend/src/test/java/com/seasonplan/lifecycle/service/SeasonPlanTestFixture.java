package com.seasonplan.lifecycle.service;

import com.seasonplan.lifecycle.adapter.fake.InMemoryAuditLogRepository;
import com.seasonplan.lifecycle.adapter.fake.InMemoryFairyAssignmentRepository;
import com.seasonplan.lifecycle.adapter.fake.InMemoryMagicTaskRepository;
import com.seasonplan.lifecycle.adapter.fake.InMemoryPollenReservationRepository;
import com.seasonplan.lifecycle.adapter.fake.InMemorySeasonPlanRepository;
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
    private final PollenReservationService pollenReservationService;
    private final MagicTaskService magicTaskService;
    private final FairyAssignmentService fairyAssignmentService;
    private final ReadinessChecklistService readinessChecklistService;
    private final AuditLogService auditLogService;
    private final NotificationService notificationService;

    private SeasonPlanTestFixture(
            SeasonPlanService seasonPlanService,
            PollenReservationService pollenReservationService,
            MagicTaskService magicTaskService,
            FairyAssignmentService fairyAssignmentService,
            ReadinessChecklistService readinessChecklistService,
            AuditLogService auditLogService,
            NotificationService notificationService
    ) {
        this.seasonPlanService = seasonPlanService;
        this.pollenReservationService = pollenReservationService;
        this.magicTaskService = magicTaskService;
        this.fairyAssignmentService = fairyAssignmentService;
        this.readinessChecklistService = readinessChecklistService;
        this.auditLogService = auditLogService;
        this.notificationService = notificationService;
    }

    public static SeasonPlanTestFixture create() {
        Clock clock = Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"), ZoneOffset.UTC);
        InMemorySeasonPlanRepository plans = new InMemorySeasonPlanRepository();
        InMemoryPollenReservationRepository pollenReservations = new InMemoryPollenReservationRepository();
        InMemoryMagicTaskRepository magicTasks = new InMemoryMagicTaskRepository();
        InMemoryFairyAssignmentRepository fairyAssignments = new InMemoryFairyAssignmentRepository();
        AuditLogService auditLogService = new AuditLogService(new InMemoryAuditLogRepository(), clock);
        NotificationService notificationService = new NotificationService();
        ReadinessChecklistService readinessChecklistService = new ReadinessChecklistService(plans, pollenReservations,
                magicTasks, fairyAssignments, clock);
        SeasonPlanService seasonPlanService = new SeasonPlanService(plans, pollenReservations, auditLogService,
                notificationService, new AllowingNaturalCalendarService(), readinessChecklistService, clock);
        PollenReservationService pollenReservationService = new PollenReservationService(pollenReservations, seasonPlanService, auditLogService);
        MagicTaskService magicTaskService = new MagicTaskService(magicTasks, seasonPlanService, auditLogService);
        FairyAssignmentService fairyAssignmentService = new FairyAssignmentService(fairyAssignments, magicTaskService, auditLogService);
        return new SeasonPlanTestFixture(seasonPlanService, pollenReservationService, magicTaskService,
                fairyAssignmentService, readinessChecklistService, auditLogService, notificationService);
    }

    public static SeasonPlan validPlanDraft(String createdBy) {
        SeasonPlan plan = new SeasonPlan();
        plan.setSeasonType("SPRING");
        plan.setTargetTerritories(List.of("north-grove"));
        plan.setTimeWindows(List.of(new TimeWindow(LocalDateTime.parse("2026-03-01T09:00:00"), LocalDateTime.parse("2026-03-01T12:00:00"))));
        plan.setNaturalPhenomenonParameters(Map.of("bloom", "early"));
        plan.setTargetClimateMetrics(Map.of("temperature", 17.5));
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

    public SeasonPlanService seasonPlanService() {
        return seasonPlanService;
    }

    public PollenReservationService pollenReservationService() {
        return pollenReservationService;
    }

    public MagicTaskService magicTaskService() {
        return magicTaskService;
    }

    public FairyAssignmentService fairyAssignmentService() {
        return fairyAssignmentService;
    }

    public ReadinessChecklistService readinessChecklistService() {
        return readinessChecklistService;
    }

    public AuditLogService auditLogService() {
        return auditLogService;
    }

    public NotificationService notificationService() {
        return notificationService;
    }
}
