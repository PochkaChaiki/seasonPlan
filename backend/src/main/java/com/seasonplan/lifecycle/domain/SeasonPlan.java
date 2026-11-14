package com.seasonplan.lifecycle.domain;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class SeasonPlan {
    private UUID id;
    private String seasonType;
    private List<String> targetTerritories = new ArrayList<>();
    private List<TimeWindow> timeWindows = new ArrayList<>();
    private Map<String, String> naturalPhenomenonParameters = new HashMap<>();
    private Map<String, Double> targetClimateMetrics = new HashMap<>();
    private SeasonPlanStatus status;
    private String approvalComment;
    private List<ApprovalCondition> approvalConditions = List.of();
    private List<ChecklistItem> readinessChecklist = new ArrayList<>();
    private String createdBy;
    private String updatedBy;
    private Instant createdAt;
    private Instant updatedAt;

    public static SeasonPlan createDraft(
            UUID id,
            String seasonType,
            List<String> targetTerritories,
            List<TimeWindow> timeWindows,
            Map<String, String> naturalPhenomenonParameters,
            Map<String, Double> targetClimateMetrics,
            List<ChecklistItem> readinessChecklist,
            String actorId,
            Instant now
    ) {
        SeasonPlan plan = new SeasonPlan();
        plan.id = id;
        plan.seasonType = seasonType;
        plan.setTargetTerritories(targetTerritories);
        plan.setTimeWindows(timeWindows);
        plan.setNaturalPhenomenonParameters(naturalPhenomenonParameters);
        plan.setTargetClimateMetrics(targetClimateMetrics);
        plan.status = SeasonPlanStatus.DRAFT;
        plan.setReadinessChecklist(readinessChecklist);
        plan.createdBy = actorId;
        plan.updatedBy = actorId;
        plan.createdAt = now;
        plan.updatedAt = now;
        return plan;
    }

    public static SeasonPlan rehydrate(
            UUID id,
            String seasonType,
            List<String> targetTerritories,
            List<TimeWindow> timeWindows,
            Map<String, String> naturalPhenomenonParameters,
            Map<String, Double> targetClimateMetrics,
            SeasonPlanStatus status,
            String approvalComment,
            List<ChecklistItem> readinessChecklist,
            String createdBy,
            String updatedBy,
            Instant createdAt,
            Instant updatedAt
    ) {
        SeasonPlan plan = new SeasonPlan();
        plan.id = id;
        plan.seasonType = seasonType;
        plan.setTargetTerritories(targetTerritories);
        plan.setTimeWindows(timeWindows);
        plan.setNaturalPhenomenonParameters(naturalPhenomenonParameters);
        plan.setTargetClimateMetrics(targetClimateMetrics);
        plan.status = status;
        plan.approvalComment = approvalComment;
        plan.setReadinessChecklist(readinessChecklist);
        plan.createdBy = createdBy;
        plan.updatedBy = updatedBy;
        plan.createdAt = createdAt;
        plan.updatedAt = updatedAt;
        return plan;
    }

    public static SeasonPlan rehydrate(
            UUID id, String seasonType, List<String> targetTerritories, List<TimeWindow> timeWindows,
            Map<String, String> naturalPhenomenonParameters, Map<String, Double> targetClimateMetrics,
            SeasonPlanStatus status, String approvalComment, List<ChecklistItem> readinessChecklist,
            String createdBy, String updatedBy, Instant createdAt, Instant updatedAt,
            List<ApprovalCondition> approvalConditions
    ) {
        SeasonPlan plan = rehydrate(id, seasonType, targetTerritories, timeWindows, naturalPhenomenonParameters,
                targetClimateMetrics, status, approvalComment, readinessChecklist, createdBy, updatedBy, createdAt, updatedAt);
        plan.approvalConditions = List.copyOf(approvalConditions);
        return plan;
    }

    public List<ApprovalCondition> approvalConditions() {
        return approvalConditions;
    }

    public UUID id() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public String seasonType() {
        return seasonType;
    }

    public void setSeasonType(String seasonType) {
        this.seasonType = seasonType;
    }

    public List<String> targetTerritories() {
        return Collections.unmodifiableList(targetTerritories);
    }

    public void setTargetTerritories(List<String> targetTerritories) {
        this.targetTerritories = targetTerritories == null ? new ArrayList<>() : new ArrayList<>(targetTerritories);
    }

    public List<TimeWindow> timeWindows() {
        return Collections.unmodifiableList(timeWindows);
    }

    public void setTimeWindows(List<TimeWindow> timeWindows) {
        this.timeWindows = timeWindows == null ? new ArrayList<>() : new ArrayList<>(timeWindows);
    }

    public Map<String, String> naturalPhenomenonParameters() {
        return Collections.unmodifiableMap(naturalPhenomenonParameters);
    }

    public void setNaturalPhenomenonParameters(Map<String, String> naturalPhenomenonParameters) {
        this.naturalPhenomenonParameters = naturalPhenomenonParameters == null ? new HashMap<>() : new HashMap<>(naturalPhenomenonParameters);
    }

    public Map<String, Double> targetClimateMetrics() {
        return Collections.unmodifiableMap(targetClimateMetrics);
    }

    public void setTargetClimateMetrics(Map<String, Double> targetClimateMetrics) {
        this.targetClimateMetrics = targetClimateMetrics == null ? new HashMap<>() : new HashMap<>(targetClimateMetrics);
    }

    public SeasonPlanStatus status() {
        return status;
    }

    public String approvalComment() {
        return approvalComment;
    }

    public void setApprovalComment(String approvalComment) {
        this.approvalComment = approvalComment;
    }

    public List<ChecklistItem> readinessChecklist() {
        return Collections.unmodifiableList(readinessChecklist);
    }

    public void setReadinessChecklist(List<ChecklistItem> readinessChecklist) {
        this.readinessChecklist = readinessChecklist == null ? new ArrayList<>() : new ArrayList<>(readinessChecklist);
    }

    public String createdBy() {
        return createdBy;
    }

    public void setCreatedBy(String createdBy) {
        this.createdBy = createdBy;
    }

    public String updatedBy() {
        return updatedBy;
    }

    public void setUpdatedBy(String updatedBy) {
        this.updatedBy = updatedBy;
    }

    public Instant createdAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant updatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }

    public void updateDraftFieldsFrom(SeasonPlan draft, String actorId, Instant now) {
        requireStatus(SeasonPlanStatus.DRAFT, "only DRAFT plans can be updated");
        this.seasonType = draft.seasonType;
        setTargetTerritories(draft.targetTerritories);
        setTimeWindows(draft.timeWindows);
        setNaturalPhenomenonParameters(draft.naturalPhenomenonParameters);
        setTargetClimateMetrics(draft.targetClimateMetrics);
        this.updatedBy = actorId;
        this.updatedAt = now;
    }

    public void submit(String actorId, Instant now) {
        requireStatus(SeasonPlanStatus.DRAFT, "only DRAFT plans can be submitted");
        this.status = SeasonPlanStatus.SUBMITTED;
        touch(actorId, now);
    }

    public void verify(String actorId, Instant now) {
        requireStatus(SeasonPlanStatus.SUBMITTED, "only SUBMITTED plans can be verified");
        this.status = SeasonPlanStatus.VERIFIED;
        touch(actorId, now);
    }

    public void approve(String comment, String actorId, Instant now) {
        approve(comment, List.of(), actorId, now);
    }

    public void approve(String comment, List<ApprovalCondition> conditions, String actorId, Instant now) {
        requireStatus(SeasonPlanStatus.VERIFIED, "only VERIFIED plans can be approved");
        List<ApprovalCondition> selected = conditions == null ? List.of() : conditions;
        if (selected.stream().anyMatch(java.util.Objects::isNull)
                || selected.stream().map(ApprovalCondition::key).distinct().count() != selected.size()) {
            throw new DomainValidationException(Map.of("conditions", List.of("condition keys must be unique and conditions must not be null")));
        }
        this.approvalConditions = List.copyOf(selected);
        this.status = SeasonPlanStatus.APPROVED;
        this.approvalComment = comment;
        touch(actorId, now);
    }

    public void returnForRevision(String comment, String actorId, Instant now) {
        requireStatus(SeasonPlanStatus.SUBMITTED, SeasonPlanStatus.VERIFIED, "only SUBMITTED or VERIFIED plans can be returned");
        if (comment == null || comment.isBlank()) {
            throw new DomainValidationException(new LinkedHashMap<>() {{
                put("comment", List.of("comment is required when returning a plan"));
            }});
        }
        this.status = SeasonPlanStatus.DRAFT;
        this.approvalComment = comment;
        touch(actorId, now);
    }

    public void markRitualReady(String actorId, Instant now) {
        requireStatus(SeasonPlanStatus.APPROVED, "only APPROVED plans can be marked RITUAL_READY");
        this.status = SeasonPlanStatus.RITUAL_READY;
        touch(actorId, now);
    }

    public void completeReadinessChecklistItem(String key, String actorId, Instant now) {
        readinessChecklist.stream()
                .filter(item -> item.key().equals(key))
                .findFirst()
                .orElseGet(() -> {
                    ChecklistItem item = new ChecklistItem(key, false);
                    readinessChecklist.add(item);
                    return item;
                })
                .markComplete();
        touch(actorId, now);
    }

    public void activate(RitualSession session, String actorId, Instant now) {
        requireStatus(SeasonPlanStatus.RITUAL_READY, "only RITUAL_READY plans can be activated");
        if (!id.equals(session.seasonPlanId()) || session.result() != RitualSession.Result.SUCCESS) {
            throw new IllegalArgumentException("activation requires a successful ritual for this plan");
        }
        this.status = SeasonPlanStatus.ACTIVE;
        touch(actorId, now);
    }

    public void complete(SeasonReport report, String actorId, Instant now) {
        requireStatus(SeasonPlanStatus.ACTIVE, "only ACTIVE plans can be completed");
        requireApprovedReport(report);
        this.status = SeasonPlanStatus.COMPLETED;
        touch(actorId, now);
    }

    public void archive(SeasonReport report, String actorId, Instant now) {
        requireStatus(SeasonPlanStatus.COMPLETED, "only COMPLETED plans can be archived");
        requireApprovedReport(report);
        this.status = SeasonPlanStatus.ARCHIVED;
        touch(actorId, now);
    }

    private void requireApprovedReport(SeasonReport report) {
        if (!id.equals(report.seasonPlanId()) || report.status() != SeasonReport.Status.APPROVED
                || report.snapshot().incidents().stream().anyMatch(MagicIncident::critical)) {
            throw new IllegalArgumentException("a complete approved report for this plan is required");
        }
    }

    private void touch(String actorId, Instant now) {
        this.updatedBy = actorId;
        this.updatedAt = now;
    }

    private void requireStatus(SeasonPlanStatus requiredStatus, String message) {
        if (status != requiredStatus) {
            throw new DomainValidationException(new LinkedHashMap<>() {{
                put("status", List.of(message));
            }});
        }
    }

    private void requireStatus(SeasonPlanStatus firstAllowed, SeasonPlanStatus secondAllowed, String message) {
        if (status != firstAllowed && status != secondAllowed) {
            throw new DomainValidationException(new LinkedHashMap<>() {{
                put("status", List.of(message));
            }});
        }
    }
}
