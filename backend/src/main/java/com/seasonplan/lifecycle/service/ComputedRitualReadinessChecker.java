package com.seasonplan.lifecycle.service;

import com.seasonplan.lifecycle.domain.*;
import com.seasonplan.lifecycle.repository.*;

import java.util.*;

/**
 * Computes readiness from current authoritative sources and the plan's pinned catalog.
 */
public final class ComputedRitualReadinessChecker implements RitualReadinessChecker {
    private final SeasonPlanRepository plans;
    private final MagicTaskRepository tasks;
    private final FairyAssignmentRepository assignments;
    private final PollenService pollen;
    private final MagicIncidentRepository incidents;

    public ComputedRitualReadinessChecker(SeasonPlanRepository plans, MagicTaskRepository tasks,
                                          FairyAssignmentRepository assignments, PollenService pollen, MagicIncidentRepository incidents) {
        this.plans = plans;
        this.tasks = tasks;
        this.assignments = assignments;
        this.pollen = pollen;
        this.incidents = incidents;
    }

    @Override
    public Readiness inspect(UUID planId) {
        SeasonPlan plan = ServiceSupport.plan(plans, planId);
        var rules = plan.executionRules();
        if (rules == null)
            ServiceSupport.reject("executionRules", "Legacy plan has no pinned execution rules; operator remediation required");
        var season = rules.seasons().get(plan.seasonType());
        if (season == null)
            ServiceSupport.reject("executionRules", "Plan season type is absent from its pinned catalog; operator remediation required");
        List<String> blockers = new ArrayList<>();
        var latest = pollen.findLatestBySeasonPlanId(planId);
        boolean eligible = latest.filter(r -> planId.equals(r.seasonPlanId()) && (r.status() == PollenReservationStatus.CONFIRMED || r.status() == PollenReservationStatus.REJECTED)).isPresent();
        boolean confirmed = eligible && pollen.findAuthoritativeConfirmedBySeasonPlanId(planId)
            .filter(r -> planId.equals(r.seasonPlanId()) && r.status() == PollenReservationStatus.CONFIRMED)
            .filter(r -> latest.orElseThrow().status() == PollenReservationStatus.REJECTED || r.id().equals(latest.orElseThrow().id())).isPresent();
        if (!confirmed) blockers.add("pollen-confirmed");
        var planTasks = tasks.findBySeasonPlanId(planId);
        for (String category : new TreeSet<>(season.categories())) {
            if (planTasks.stream().noneMatch(task -> category.equals(task.category()) && task.status() != MagicTaskStatus.CANCELLED))
                blockers.add("category:" + category);
        }
        Set<String> confirmedRoles = new HashSet<>();
        Set<RitualSession.Participant> participants = new HashSet<>();
        Set<String> knownCategories = new HashSet<>();
        rules.seasons().values().forEach(requirements -> knownCategories.addAll(requirements.categories()));
        for (MagicTask task : planTasks) {
            if (task.status() == MagicTaskStatus.CANCELLED) continue;
            if (task.category() == null || !knownCategories.contains(task.category())) {
                blockers.add("task-category:" + task.id());
                continue;
            }
            if (!season.categories().contains(task.category())) continue;
            if (task.status() != MagicTaskStatus.COMPLETED) blockers.add("task:" + task.id());
            boolean compatibleAssignment = false;
            for (FairyAssignment assignment : assignments.findByMagicTaskId(task.id())) {
                if (assignment.status() == AssignmentStatus.CONFIRMED && assignment.confirmedAt() != null
                    && Objects.equals(task.requiredTalent(), assignment.talent())) {
                    compatibleAssignment = true;
                    confirmedRoles.add(assignment.role());
                    participants.add(new RitualSession.Participant(assignment.fairyId(), assignment.role(), assignment.confirmedAt()));
                }
            }
            if (!compatibleAssignment) blockers.add("assignment:" + task.id());
        }
        for (String role : new TreeSet<>(season.roles()))
            if (!confirmedRoles.contains(role)) blockers.add("role:" + role);
        if (plan.readinessChecklist().isEmpty() || plan.readinessChecklist().stream().anyMatch(item -> !item.complete()))
            blockers.add("readinessChecklist");
        for (ApprovalCondition condition : plan.approvalConditions()) blockers.add(condition.key());
        try {
            if (incidents.forPlan(planId).stream().anyMatch(MagicIncident::critical)) blockers.add("critical-incident");
        } catch (RuntimeException ex) {
            for (Throwable cause = ex; cause != null; cause = cause.getCause()) {
                if (cause instanceof LegacyExecutionMetadataException)
                    ServiceSupport.reject("executionRules", "Legacy execution records need operator remediation before readiness can be checked");
            }
            throw ex;
        }
        List<RitualSession.Participant> sorted = participants.stream().sorted(Comparator.comparing(RitualSession.Participant::fairyId).thenComparing(RitualSession.Participant::role).thenComparing(RitualSession.Participant::confirmedAt)).toList();
        return new Readiness(blockers, sorted, rules.requiredEvents());
    }
}
