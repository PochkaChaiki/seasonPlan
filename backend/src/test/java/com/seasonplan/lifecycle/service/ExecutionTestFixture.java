package com.seasonplan.lifecycle.service;

import com.seasonplan.monitoring.application.MonitoringService;
import com.seasonplan.monitoring.application.port.MetricRuleProvider;
import com.seasonplan.monitoring.domain.ClimateMetric;
import com.seasonplan.pollen.application.PollenService;
import com.seasonplan.reference.api.MetricDefinition;
import com.seasonplan.reporting.application.port.RequiredTaskResults;
import com.seasonplan.reporting.application.SeasonReportService;
import com.seasonplan.reporting.domain.SeasonReport;
import com.seasonplan.response.application.IncidentResponseService;
import com.seasonplan.response.domain.CorrectionAction;
import com.seasonplan.response.domain.MagicIncident;
import com.seasonplan.ritual.api.RitualReadinessChecker;
import com.seasonplan.ritual.application.RitualService;
import com.seasonplan.ritual.domain.RitualSession;
import com.seasonplan.season.api.CommandTransaction;
import com.seasonplan.season.domain.SeasonPlan;
import com.seasonplan.season.domain.SeasonPlanStatus;

import com.seasonplan.audit.application.AuditLogService;
import com.seasonplan.notification.api.NotificationOutbox;
import com.seasonplan.pollen.application.port.PollenTreeClient;
import com.seasonplan.audit.adapter.out.persistence.InMemoryAuditLogRepository;
import com.seasonplan.season.adapter.out.fake.InMemorySeasonPlanRepository;
import com.seasonplan.ritual.application.port.RitualSessionRepository;
import com.seasonplan.monitoring.application.port.ClimateMetricRepository;
import com.seasonplan.response.application.port.MagicIncidentRepository;
import com.seasonplan.response.application.port.CorrectionActionRepository;
import com.seasonplan.reporting.application.port.SeasonReportRepository;

import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import java.util.function.Supplier;

/** Deterministic test-only port implementations, not application wiring or a persistence substitute. */
class ExecutionTestFixture {
    final Instant now = Instant.parse("2026-03-01T10:00:00Z");
    final Clock clock = Clock.fixed(now, ZoneOffset.UTC);
    final InMemorySeasonPlanRepository plans = new InMemorySeasonPlanRepository();
    final AuditLogService audit = new AuditLogService(new InMemoryAuditLogRepository(), clock);
    SeasonPlan plan;
    List<String> readinessBlockers = List.of();
    List<RitualSession.Participant> participants = List.of(new RitualSession.Participant("fairy", "RITUAL_LEAD", now));
    final Map<UUID, RitualSession> sessions = new LinkedHashMap<>();
    final Map<UUID, ClimateMetric> metrics = new LinkedHashMap<>();
    final Map<UUID, MagicIncident> incidents = new LinkedHashMap<>();
    final Map<UUID, CorrectionAction> actions = new LinkedHashMap<>();
    final List<NotificationOutbox.Event> events = new ArrayList<>();
    final CommandTransaction transactions = new CommandTransaction() {
        public <T> T execute(UUID id, Supplier<T> command) { return command.get(); }
        public <T> T create(Supplier<T> command) { return command.get(); }
    };
    final NotificationOutbox outbox = event -> {
        if (events.stream().noneMatch(old -> old.deduplicationKey().equals(event.deduplicationKey()))) events.add(event);
    };
    final RitualSessionRepository ritualPort = new RitualSessionRepository() {
        public Optional<RitualSession> find(UUID id) { return Optional.ofNullable(sessions.get(id)); }
        public List<RitualSession> forPlan(UUID id) { return sessions.values().stream().filter(s -> s.seasonPlanId().equals(id)).toList(); }
        public void save(RitualSession session) { sessions.put(session.id(), session); }
    };
    final ClimateMetricRepository metricPort = new ClimateMetricRepository() {
        public Optional<ClimateMetric> byImportKey(String source, String id) {
            return metrics.values().stream().filter(m -> m.source().equals(source) && m.sourceEventId().equals(id)).findFirst();
        }
        public List<ClimateMetric> forPlan(UUID id) { return metrics.values().stream().filter(m -> m.seasonPlanId().equals(id)).toList(); }
        public void save(ClimateMetric metric) { metrics.put(metric.id(), metric); }
    };
    final MagicIncidentRepository incidentPort = new MagicIncidentRepository() {
        public Optional<MagicIncident> find(UUID id) { return Optional.ofNullable(incidents.get(id)); }
        public List<MagicIncident> forPlan(UUID id) { return incidents.values().stream().filter(i -> i.seasonPlanId().equals(id)).toList(); }
        public void save(MagicIncident incident) { incidents.put(incident.id(), incident); }
    };
    final CorrectionActionRepository correctionPort = new CorrectionActionRepository() {
        public Optional<CorrectionAction> find(UUID id) { return Optional.ofNullable(actions.get(id)); }
        public Optional<CorrectionAction> byKey(String key) { return actions.values().stream().filter(a -> a.deduplicationKey().equals(key)).findFirst(); }
        public List<CorrectionAction> forPlan(UUID id) { return actions.values().stream().filter(a -> a.seasonPlanId().equals(id)).toList(); }
        public List<CorrectionAction> allOpen() { return actions.values().stream().filter(a -> !a.terminal()).toList(); }
        public void save(CorrectionAction action) { actions.put(action.id(), action); }
    };
    final List<SeasonReport.TaskResult> tasks = new ArrayList<>();
    PollenTreeClient.Summary pollenSummary = new PollenTreeClient.Summary(UUID.randomUUID(), new BigDecimal("100"), new BigDecimal("80"), "v1");
    SeasonReport draftReport;
    SeasonReport approvedReport;
    final List<SeasonReport> approvedReports = new ArrayList<>();
    SeasonReport exported;
    int reportVersion;
    final SeasonReportService completion;
    final PollenTreeClient pollenPort = org.mockito.Mockito.mock(PollenTreeClient.class);
    final SeasonReportRepository reportPort = new SeasonReportRepository() {
        public Optional<SeasonReport> latestDraft(UUID id) { return Optional.ofNullable(draftReport); }
        public Optional<SeasonReport> latestApproved(UUID id) { return Optional.ofNullable(approvedReport); }
        public int nextVersion(UUID id) { return ++reportVersion; }
        public void saveDraft(SeasonReport report) { draftReport = report; }
        public void appendApproved(SeasonReport report) {
            if (approvedReports.stream().anyMatch(old -> old.id().equals(report.id()))) throw new IllegalStateException("approved report cannot be overwritten");
            approvedReports.add(report); approvedReport = report;
        }
        public List<SeasonReport> approvedForSeasonType(String type) { return approvedReports.stream().filter(report -> report.snapshot().seasonType().equals(type)).toList(); }
    };
    final RequiredTaskResults taskPort = id -> List.copyOf(tasks);
    final RitualService rituals;
    final MonitoringService monitoring;
    final com.seasonplan.response.api.MetricReactions metricReactions;
    final IncidentResponseService response;

    ExecutionTestFixture(SeasonPlanStatus status) {
        org.mockito.Mockito.when(pollenPort.summary(org.mockito.ArgumentMatchers.any(UUID.class)))
                .thenAnswer(call -> Optional.ofNullable(pollenSummary));
        plan = SeasonPlan.createDraft(UUID.randomUUID(), "SPRING", List.of("north"), List.of(), Map.of("bloom", "early"),
                Map.of("temperature", 20.0, "humidity", 60.0, "pollen_density", 1.0), List.of(), "planner", now);
        setStatus(status);
        rituals = new RitualService(new com.seasonplan.season.application.SeasonQueryService(plans), new com.seasonplan.season.application.SeasonLifecycleService(plans, transactions, audit, clock), ritualPort, new com.seasonplan.response.application.ResponseQueryService(incidentPort, correctionPort),
                id -> new RitualReadinessChecker.Readiness(readinessBlockers,
                        participants, Set.of("LAUNCH_VALIDATED")), transactions, audit, clock);
        metricReactions = new com.seasonplan.response.application.MetricReactionService(incidentPort, correctionPort,
                new com.seasonplan.season.application.SeasonLifecycleService(plans, transactions, audit, clock), transactions, outbox, audit, clock);
        monitoring = new MonitoringService(new com.seasonplan.season.application.SeasonQueryService(plans), metricPort,
                (id, metric) -> new MetricRuleProvider.MetricRule(new BigDecimal("20"), new BigDecimal("2"), "v1", "balancer", Duration.ofHours(1)),
                metricReactions, transactions, audit, clock);
        response = new IncidentResponseService(new com.seasonplan.season.application.SeasonQueryService(plans), new com.seasonplan.season.application.SeasonLifecycleService(plans, transactions, audit, clock), incidentPort, correctionPort, type -> MagicIncident.Severity.CRITICAL,
                transactions, outbox, audit, clock);
        var pollen = PollenTestServices.create(new com.seasonplan.season.application.SeasonQueryService(plans), pollenPort, () -> outbox, transactions, audit, clock);
        completion = new SeasonReportService(new com.seasonplan.season.application.SeasonQueryService(plans), new com.seasonplan.season.application.SeasonLifecycleService(plans, transactions, audit, clock), taskPort, new com.seasonplan.ritual.application.RitualQueryService(ritualPort),
                new com.seasonplan.monitoring.application.MetricQueryService(metricPort),
                new com.seasonplan.response.application.ResponseQueryService(incidentPort, correctionPort), new com.seasonplan.pollen.application.PollenOperationsFacade(pollen),
                reportPort, id -> "v1", (report, format) -> { exported = report; return new byte[] {1, 2, 3}; }, transactions, audit, clock);
    }
    void successfulSources() {
        RitualSession ritual = RitualSession.start(UUID.randomUUID(), plan.id(), 1, "coordinator", now,
                List.of(new RitualSession.Participant("fairy", "LEAD", now))).succeed(now);
        sessions.clear(); sessions.put(ritual.id(), ritual);
        ClimateMetric metric = new ClimateMetric(UUID.randomUUID(), plan.id(), "sensor", "metric-1", MetricDefinition.TEMPERATURE,
                new BigDecimal("20"), new BigDecimal("20"), new BigDecimal("2"), null, now);
        metrics.clear(); metrics.put(metric.id(), metric);
        ClimateMetric humidity = new ClimateMetric(UUID.randomUUID(), plan.id(), "sensor", "metric-2", MetricDefinition.HUMIDITY,
                new BigDecimal("60"), new BigDecimal("60"), new BigDecimal("2"), null, now);
        ClimateMetric density = new ClimateMetric(UUID.randomUUID(), plan.id(), "sensor", "metric-3", MetricDefinition.POLLEN_DENSITY,
                new BigDecimal("1"), new BigDecimal("1"), new BigDecimal("2"), null, now);
        metrics.put(humidity.id(), humidity); metrics.put(density.id(), density);
        tasks.clear(); tasks.add(new SeasonReport.TaskResult(UUID.randomUUID(), "Work", true));
    }
    void setStatus(SeasonPlanStatus status) {
        plan = SeasonPlan.rehydrate(plan.id(), plan.seasonType(), plan.targetTerritories(), plan.timeWindows(), plan.naturalPhenomenonParameters(),
                plan.targetClimateMetrics(), status, null, plan.readinessChecklist(), plan.createdBy(), plan.updatedBy(), plan.createdAt(), plan.updatedAt(), plan.materialRevisionStamp());
        plan.pinExecutionRules(TestExecutionRules.snapshot());
        plans.save(plan);
    }
}
