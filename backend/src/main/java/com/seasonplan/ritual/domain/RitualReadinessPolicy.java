package com.seasonplan.ritual.domain;

import com.seasonplan.ritual.api.RitualReadinessChecker.Readiness;
import com.seasonplan.season.domain.ApprovalCondition;
import com.seasonplan.ritual.api.ReadinessEvaluation;
import com.seasonplan.season.api.SeasonFacts;
import com.seasonplan.work.domain.MagicTaskStatus;
import com.seasonplan.work.domain.AssignmentStatus;
import com.seasonplan.work.api.AssignmentFacts;
import com.seasonplan.pollen.domain.PollenReservationStatus;
import com.seasonplan.pollen.api.ReservationFacts;
import com.seasonplan.response.domain.MagicIncident;
import com.seasonplan.shared.domain.DomainValidationException;
import java.util.*;

/** Pure readiness decision over the current plan and supplied operational snapshots. */
public final class RitualReadinessPolicy implements ReadinessEvaluation {
    public RitualReadinessPolicy() { }
    @Override public void requireCatalog(SeasonFacts plan) {
        if (plan.executionRules() == null) reject("Legacy plan has no pinned execution rules; operator remediation required");
        if (!plan.executionRules().seasons().containsKey(plan.seasonType())) reject("Plan season type is absent from its pinned catalog; operator remediation required");
    }
    @Override public boolean requiresResourceAuthority(UUID id, Optional<ReservationFacts> latest) {
        return latest.filter(r -> !r.unresolvedOperation() && id.equals(r.seasonPlanId()) && (r.status() == PollenReservationStatus.CONFIRMED || r.status() == PollenReservationStatus.REJECTED)).isPresent();
    }
    @Override public Readiness evaluate(SeasonFacts plan, Optional<ReservationFacts> latest, Optional<ReservationFacts> authoritative,
                                     List<TaskFacts> tasks, List<MagicIncident> incidents) {
        requireCatalog(plan);
        var rules = plan.executionRules();
        var season = rules.seasons().get(plan.seasonType());
        var blockers = new ArrayList<String>();
        boolean confirmed = requiresResourceAuthority(plan.id(), latest) && authoritative
                .filter(r -> plan.id().equals(r.seasonPlanId()) && r.status() == PollenReservationStatus.CONFIRMED
                        && !r.unresolvedOperation() && r.materialRevision() != null && r.materialRevision() == plan.materialRevision())
                .filter(r -> latest.orElseThrow().status() == PollenReservationStatus.REJECTED || r.id().equals(latest.orElseThrow().id())).isPresent();
        if (!confirmed) blockers.add("pollen-confirmed");
        for (String category : new TreeSet<>(season.categories())) {
            if (tasks.stream().noneMatch(fact -> category.equals(fact.task().category()) && fact.task().status() != MagicTaskStatus.CANCELLED)) blockers.add("category:" + category);
        }
        var confirmedRoles = new HashSet<String>();
        var participants = new HashSet<RitualSession.Participant>();
        var knownCategories = new HashSet<String>();
        rules.seasons().values().forEach(requirements -> knownCategories.addAll(requirements.categories()));
        for (TaskFacts fact : tasks) {
            var task = fact.task();
            if (task.status() == MagicTaskStatus.CANCELLED) continue;
            if (task.category() == null || !knownCategories.contains(task.category())) { blockers.add("task-category:" + task.id()); continue; }
            if (!season.categories().contains(task.category())) continue;
            if (task.status() != MagicTaskStatus.COMPLETED) blockers.add("task:" + task.id());
            boolean compatible = false;
            for (AssignmentFacts assignment : fact.assignments()) {
                if (assignment.status() == AssignmentStatus.CONFIRMED && assignment.confirmedAt() != null && Objects.equals(task.requiredTalent(), assignment.talent())) {
                    compatible = true; confirmedRoles.add(assignment.role());
                    participants.add(new RitualSession.Participant(assignment.fairyId(), assignment.role(), assignment.confirmedAt()));
                }
            }
            if (!compatible) blockers.add("assignment:" + task.id());
        }
        for (String role : new TreeSet<>(season.roles())) if (!confirmedRoles.contains(role)) blockers.add("role:" + role);
        if (plan.readinessChecklist().isEmpty() || plan.readinessChecklist().stream().anyMatch(item -> !item.complete())) blockers.add("readinessChecklist");
        for (ApprovalCondition condition : plan.approvalConditions()) blockers.add(condition.key());
        if (incidents.stream().anyMatch(MagicIncident::critical)) blockers.add("critical-incident");
        var sorted = participants.stream().sorted(Comparator.comparing(RitualSession.Participant::fairyId)
                .thenComparing(RitualSession.Participant::role).thenComparing(RitualSession.Participant::confirmedAt)).toList();
        return new Readiness(blockers, sorted, rules.requiredEvents());
    }
    @Override public void legacyRecordsUnavailable() { reject("Legacy execution records need operator remediation before readiness can be checked"); }
    private static void reject(String reason) { throw new DomainValidationException(Map.of("executionRules", List.of(reason))); }
}
