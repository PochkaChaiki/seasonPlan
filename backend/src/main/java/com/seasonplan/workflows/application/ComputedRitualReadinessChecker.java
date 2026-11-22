package com.seasonplan.workflows.application;

import com.seasonplan.pollen.api.PollenOperations;
import com.seasonplan.response.api.ResponseQueries;
import com.seasonplan.response.domain.MagicIncident;
import com.seasonplan.ritual.api.RitualReadinessChecker;
import com.seasonplan.ritual.api.ReadinessEvaluation;
import com.seasonplan.season.api.SeasonReadiness;
import com.seasonplan.season.api.SeasonQueries;
import com.seasonplan.shared.application.LegacyExecutionMetadataException;
import com.seasonplan.work.api.WorkQueries;
import java.util.*;

/** Collect current facts, then ask the ritual domain for a readiness decision. */
public final class ComputedRitualReadinessChecker implements RitualReadinessChecker, SeasonReadiness {
    private final SeasonQueries seasons;
    private final WorkQueries work;
    private final PollenOperations pollen;
    private final ResponseQueries response;
    private final ReadinessEvaluation evaluation;

    public ComputedRitualReadinessChecker(SeasonQueries seasons, WorkQueries work, PollenOperations pollen, ResponseQueries response, ReadinessEvaluation evaluation) {
        this.seasons = seasons; this.work = work; this.pollen = pollen; this.response = response; this.evaluation = evaluation;
    }
    @Override public List<String> blockers(UUID id) { return inspect(id).blockers(); }
    public Readiness inspect(UUID id) {
        var plan = seasons.get(id);
        evaluation.requireCatalog(plan);
        var latest = pollen.findLatestBySeasonPlanId(id);
        var authoritative = evaluation.requiresResourceAuthority(id, latest)
                ? pollen.findAuthoritativeConfirmedBySeasonPlanId(id) : Optional.<com.seasonplan.pollen.api.ReservationFacts>empty();
        var facts = work.tasksForPlan(id).stream().map(task -> new ReadinessEvaluation.TaskFacts(task, work.assignmentsForTask(task.id()))).toList();
        List<MagicIncident> currentIncidents;
        try { currentIncidents = response.incidentsForPlan(id); }
        catch (RuntimeException ex) {
            for (Throwable cause = ex; cause != null; cause = cause.getCause()) {
                if (cause instanceof LegacyExecutionMetadataException) evaluation.legacyRecordsUnavailable();
            }
            throw ex;
        }
        return evaluation.evaluate(plan, latest, authoritative, facts, currentIncidents);
    }
}
