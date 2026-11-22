package com.seasonplan.reporting.application;

import com.seasonplan.monitoring.domain.ClimateMetric;
import com.seasonplan.pollen.api.PollenOperations;
import com.seasonplan.reporting.application.port.CatalogVersionProvider;
import com.seasonplan.reporting.application.port.RequiredTaskResults;
import com.seasonplan.reporting.application.port.SeasonReportExporter;
import com.seasonplan.reporting.domain.SeasonReport;
import com.seasonplan.reporting.domain.ReportPolicy;
import com.seasonplan.response.domain.CorrectionAction;
import com.seasonplan.response.domain.MagicIncident;
import com.seasonplan.ritual.domain.RitualSession;
import com.seasonplan.season.api.CommandTransaction;
import com.seasonplan.season.api.SeasonLifecycle;
import com.seasonplan.season.api.SeasonFacts;
import com.seasonplan.season.domain.SeasonPlanStatus;

import com.seasonplan.audit.api.AuditAction;
import com.seasonplan.audit.api.AuditTrail;
import com.seasonplan.identity.api.LifecycleAccessPolicy;
import com.seasonplan.identity.api.LifecyclePermission;

import com.seasonplan.season.api.SeasonQueries;
import com.seasonplan.identity.api.ActorRole;
import com.seasonplan.shared.application.NotFoundException;
import com.seasonplan.ritual.api.RitualQueries;
import com.seasonplan.monitoring.api.MetricQueries;
import com.seasonplan.response.api.ResponseQueries;
import com.seasonplan.reporting.application.port.SeasonReportRepository;

import java.time.Clock;
import java.time.ZoneOffset;
import java.util.*;

/** Report snapshots, approval with atomic plan completion, approved-snapshot export and archival. */
public class SeasonReportService {
    public enum Format { HTML, PDF }
    public record Export(String filename, String contentType, byte[] content) {
        public Export { content = content.clone(); }
        @Override public byte[] content() { return content.clone(); }
    }
    private final SeasonQueries seasons;
    private final SeasonLifecycle lifecycle;
    private final RequiredTaskResults tasks;
    private final RitualQueries rituals;
    private final MetricQueries metrics;
    private final ResponseQueries response;
    private final PollenOperations pollen;
    private final SeasonReportRepository reports;
    private final CatalogVersionProvider catalog;
    private final SeasonReportExporter exporter;
    private final CommandTransaction transactions;
    private final AuditTrail audit;
    private final Clock clock;

    public SeasonReportService(SeasonQueries seasons, SeasonLifecycle lifecycle, RequiredTaskResults tasks, RitualQueries rituals,
                             MetricQueries metrics, ResponseQueries response,
                             PollenOperations pollen, SeasonReportRepository reports, CatalogVersionProvider catalog,
                             SeasonReportExporter exporter, CommandTransaction transactions, AuditTrail audit, Clock clock) {
        this.seasons = seasons; this.lifecycle = lifecycle; this.tasks = tasks; this.rituals = rituals; this.metrics = metrics; this.response = response; this.pollen = pollen; this.reports = reports; this.catalog = catalog;
        this.exporter = exporter; this.transactions = transactions; this.audit = audit; this.clock = clock;
    }
    public SeasonReport generate(UUID planId, String title, ActorRole role, String actorId) {
        LifecycleAccessPolicy.require(role, LifecyclePermission.GENERATE_REPORT);
        return transactions.execute(planId, () -> {
            SeasonFacts plan = seasons.get(planId);
            ReportPolicy.requireGenerationState(plan.status());
            LifecycleAccessPolicy.requireReportOwner(role, actorId, plan.createdBy());
            SeasonReport.Snapshot snapshot = snapshot(plan);
            SeasonReport report = new SeasonReport(UUID.randomUUID(), planId, reports.nextVersion(planId), title, snapshot,
                    snapshot.blockers(), SeasonReport.Status.DRAFT, actorId, clock.instant(), null, null);
            reports.saveDraft(report);
            audit.append(planId, AuditAction.SEASON_REPORT_GENERATED, actorId, report.id().toString());
            return report;
        });
    }
    public SeasonReport approveAndComplete(UUID planId, UUID reportId, ActorRole role, String actorId) {
        LifecycleAccessPolicy.require(role, LifecyclePermission.APPROVE_REPORT);
        return transactions.execute(planId, () -> {
            SeasonFacts plan = seasons.get(planId);
            ReportPolicy.requireApprovalState(plan.status());
            SeasonReport draft = reports.latestDraft(planId).orElseThrow(() -> new NotFoundException("draft report not found"));
            ReportPolicy.requireLatestDraft(reportId, draft);
            SeasonReport.Snapshot current = snapshot(plan);
            ReportPolicy.requireApprovableSnapshot(current, draft);
            SeasonReport approved = draft.approve(actorId, clock.instant());
            boolean completing = ReportPolicy.requiresCompletion(plan.status());
            reports.appendApproved(approved);
            audit.append(planId, AuditAction.SEASON_REPORT_APPROVED, actorId, approved.id().toString());
            if (completing) {
                lifecycle.complete(planId, evidence(approved), actorId);
            }
            return approved;
        });
    }
    public SeasonFacts archive(UUID planId, ActorRole role, String actorId) {
        LifecycleAccessPolicy.require(role, LifecyclePermission.ARCHIVE_PLAN);
        return transactions.execute(planId, () -> {
            SeasonFacts plan = seasons.get(planId);
            ReportPolicy.requireArchiveState(plan.status());
            ReportPolicy.requireNoCriticalForArchive(response.incidentsForPlan(planId));
            SeasonReport approved = approved(planId);
            ReportPolicy.requireLatestApprovedRevision(approved, reports.latestDraft(planId));
            lifecycle.archive(planId, evidence(approved), actorId);
            return seasons.get(planId);
        });
    }
    public Optional<SeasonReport> view(UUID planId) {
        seasons.get(planId);
        Optional<SeasonReport> approved = reports.latestApproved(planId);
        Optional<SeasonReport> draft = reports.latestDraft(planId);
        return ReportPolicy.selectCurrent(draft, approved);
    }
    public Export export(UUID planId, Format format) {
        seasons.get(planId);
        SeasonReport report = approved(planId);
        String extension = format.name().toLowerCase(Locale.ROOT);
        return new Export("season-" + planId + "-v" + report.version() + "." + extension,
                format == Format.PDF ? "application/pdf" : "text/html; charset=UTF-8", exporter.render(report, extension));
    }
    public List<SeasonReport> historicalComparison(UUID planId) {
        SeasonFacts plan = seasons.get(planId);
        int year = clock.instant().atZone(ZoneOffset.UTC).getYear();
        String version = catalog.forPlan(planId);
        return ReportPolicy.historical(reports.approvedForSeasonType(plan.seasonType()), version, year);
    }
    private SeasonLifecycle.ReportEvidence evidence(SeasonReport report) {
        return new SeasonLifecycle.ReportEvidence(report.id(), report.seasonPlanId(),
                report.status() == SeasonReport.Status.APPROVED, report.snapshot().incidents().stream().anyMatch(MagicIncident::critical));
    }
    private SeasonReport approved(UUID planId) {
        return ReportPolicy.requireApproved(reports.latestApproved(planId));
    }
    private SeasonReport.Snapshot snapshot(SeasonFacts plan) {
        UUID id = plan.id();
        return ReportPolicy.snapshot(plan, catalog.forPlan(id), tasks.requiredResults(id), metrics.forPlan(id),
                response.incidentsForPlan(id), response.correctionsForPlan(id), rituals.forPlan(id), pollen.usage(id).orElse(null));
    }
}
