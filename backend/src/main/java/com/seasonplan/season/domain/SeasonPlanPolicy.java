package com.seasonplan.season.domain;

import com.seasonplan.reference.api.MetricDefinition;
import com.seasonplan.reference.domain.ExecutionRuleSnapshot;
import com.seasonplan.shared.domain.*;
import com.seasonplan.season.api.ResourceEvidenceSource.Reservation;
import com.seasonplan.season.api.ResourceEvidenceSource.Status;
import java.util.*;

/** Planning and review decisions. All facts are supplied by the use case; this policy performs no I/O. */
public final class SeasonPlanPolicy {
    private SeasonPlanPolicy() { }
    public static List<ChecklistItem> initialReadinessChecklist() { return List.of(new ChecklistItem("calendar-reviewed", false), new ChecklistItem("roles-reviewed", false)); }
    public static Set<String> reviewRecipients(SeasonPlan plan) { return Set.of(plan.createdBy()); }
    public static void requireVerifiable(SeasonPlan plan) { requireReviewStatus(plan, SeasonPlanStatus.SUBMITTED); }
    public static void requireApprovable(SeasonPlan plan) { requireReviewStatus(plan, SeasonPlanStatus.VERIFIED); }

    public static LinkedHashMap<String, List<String>> validate(SeasonPlan plan, List<String> calendarErrors) {
        var errors = new LinkedHashMap<String, List<String>>();
        if (plan.seasonType() == null || plan.seasonType().isBlank()) ValidationSupport.add(errors, "seasonType", "season type is required");
        if (plan.timeWindows().isEmpty()) ValidationSupport.add(errors, "timeWindows", "at least one time window is required");
        if (plan.targetTerritories().isEmpty()) ValidationSupport.add(errors, "targetTerritories", "at least one target territory is required");
        if (plan.naturalPhenomenonParameters().isEmpty()) ValidationSupport.add(errors, "naturalPhenomenonParameters", "natural phenomenon parameters are required");
        if (plan.targetClimateMetrics().isEmpty()) ValidationSupport.add(errors, "targetClimateMetrics", "target climate metrics are required");
        for (MetricDefinition metric : MetricDefinition.values()) {
            if (!plan.targetClimateMetrics().containsKey(metric.key())) ValidationSupport.add(errors, "targetClimateMetrics", "target is required for " + metric.key());
        }
        for (var entry : plan.targetClimateMetrics().entrySet()) {
            boolean known = Arrays.stream(MetricDefinition.values()).anyMatch(metric -> metric.key().equals(entry.getKey()));
            if (!known || entry.getValue() == null || !Double.isFinite(entry.getValue())) ValidationSupport.add(errors, "targetClimateMetrics", "metric targets must use catalog keys and finite values");
        }
        for (TimeWindow window : plan.timeWindows()) {
            if (window.start() == null || window.end() == null || !window.start().isBefore(window.end())) {
                ValidationSupport.add(errors, "timeWindows", "each time window must have start before end"); break;
            }
        }
        var sorted = plan.timeWindows().stream().filter(window -> window.start() != null && window.end() != null)
                .sorted(Comparator.comparing(TimeWindow::start)).toList();
        for (int index = 1; index < sorted.size(); index++) {
            if (sorted.get(index - 1).overlaps(sorted.get(index))) {
                ValidationSupport.add(errors, "timeWindows", "time windows must not overlap"); break;
            }
        }
        for (String error : calendarErrors) ValidationSupport.add(errors, "naturalCalendar", error);
        ValidationSupport.throwIfAny(errors);
        return errors;
    }

    public static void requireNoWindowConflict(SeasonPlan plan, List<SeasonPlan> otherPlans) {
        var applicable = EnumSet.of(SeasonPlanStatus.SUBMITTED, SeasonPlanStatus.VERIFIED, SeasonPlanStatus.APPROVED,
                SeasonPlanStatus.RITUAL_READY, SeasonPlanStatus.ACTIVE, SeasonPlanStatus.CORRECTION, SeasonPlanStatus.EMERGENCY);
        var errors = new LinkedHashMap<String, List<String>>();
        for (SeasonPlan other : otherPlans) {
            if (plan.id().equals(other.id()) || !applicable.contains(other.status())) continue;
            for (String territory : plan.targetTerritories()) {
                if (territory == null) continue;
                String normalized = territory.trim().toLowerCase(Locale.ROOT);
                boolean shared = other.targetTerritories().stream().filter(Objects::nonNull)
                        .anyMatch(value -> value.trim().toLowerCase(Locale.ROOT).equals(normalized));
                if (!shared) continue;
                for (TimeWindow window : plan.timeWindows()) {
                    for (TimeWindow otherWindow : other.timeWindows()) {
                        if (window.overlaps(otherWindow)) ValidationSupport.add(errors, "targetTerritories", "territory " + normalized
                                + " conflicts with plan " + other.id() + " window " + otherWindow);
                    }
                }
            }
        }
        ValidationSupport.throwIfAny(errors);
    }

    public static void requireCatalogSeason(SeasonPlan plan, ExecutionRuleSnapshot rules, boolean pinned) {
        if (rules == null) reject("executionRules", "Legacy plan has no pinned execution rules; operator remediation required");
        if (!rules.seasons().containsKey(plan.seasonType())) reject("seasonType", pinned ? "season type is not present in pinned execution catalog" : "season type is not present in the execution catalog");
    }

    private static void requireReviewStatus(SeasonPlan plan, SeasonPlanStatus required) {
        if (plan.status() != required) reject("status", "only " + required + " plans can be " + (required == SeasonPlanStatus.SUBMITTED ? "verified" : "approved"));
    }

    public static Reservation requireReservation(UUID planId, Optional<Reservation> latest) {
        if (latest.isEmpty() || !planId.equals(latest.get().seasonPlanId())) throw invalidPollen();
        return latest.orElseThrow();
    }

    public record ResourceDecision(boolean returnForRevision, String comment) { }

    public static ResourceDecision reviewResources(UUID planId, long materialRevision, Reservation latest, Optional<Reservation> confirmed) {
        if (latest.unresolvedOperation()) throw invalidPollen();
        if (confirmed.isEmpty()) {
            if (currentResource(latest, materialRevision) && latest.status() == Status.REJECTED && latest.comment() != null && !latest.comment().isBlank())
                return new ResourceDecision(true, "Pollen reservation rejected: " + latest.comment());
            throw invalidPollen();
        }
        if (confirmed.get().status() != Status.CONFIRMED || !planId.equals(confirmed.get().seasonPlanId()) || !currentResource(confirmed.get(), materialRevision)) throw invalidPollen();
        return new ResourceDecision(false, null);
    }

    private static boolean currentResource(Reservation value, long revision) {
        return !value.unresolvedOperation() && value.materialRevision() != null && value.materialRevision() == revision;
    }

    public static Map<String, List<String>> readinessErrors(List<String> blockers) {
        var errors = new LinkedHashMap<String, List<String>>();
        for (String blocker : blockers) ValidationSupport.add(errors, blocker, "readiness requirement is not satisfied");
        return errors;
    }

    public static void requireReady(Map<String, List<String>> errors) { ValidationSupport.throwIfAny(new LinkedHashMap<>(errors)); }
    private static DomainValidationException invalidPollen() { return new DomainValidationException(Map.of("pollenReservation", List.of("confirmed pollen reservation is required"))); }
    private static void reject(String field, String message) { throw new DomainValidationException(Map.of(field, List.of(message))); }
}
