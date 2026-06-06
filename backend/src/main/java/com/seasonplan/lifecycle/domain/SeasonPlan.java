package com.seasonplan.lifecycle.domain;

import com.fasterxml.jackson.annotation.JsonAutoDetect;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY)
public class SeasonPlan {
    private UUID id;
    private String seasonType;
    private List<String> targetTerritories = new ArrayList<>();
    private List<TimeWindow> timeWindows = new ArrayList<>();
    private Map<String, String> naturalPhenomenonParameters = new HashMap<>();
    private Map<String, Double> targetClimateMetrics = new HashMap<>();
    private SeasonPlanStatus status;
    private String approvalComment;
    private List<ChecklistItem> readinessChecklist = new ArrayList<>();
    private String createdBy;
    private String updatedBy;
    private Instant createdAt;
    private Instant updatedAt;

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
        return targetTerritories;
    }

    public void setTargetTerritories(List<String> targetTerritories) {
        this.targetTerritories = targetTerritories == null ? new ArrayList<>() : new ArrayList<>(targetTerritories);
    }

    public List<TimeWindow> timeWindows() {
        return timeWindows;
    }

    public void setTimeWindows(List<TimeWindow> timeWindows) {
        this.timeWindows = timeWindows == null ? new ArrayList<>() : new ArrayList<>(timeWindows);
    }

    public Map<String, String> naturalPhenomenonParameters() {
        return naturalPhenomenonParameters;
    }

    public void setNaturalPhenomenonParameters(Map<String, String> naturalPhenomenonParameters) {
        this.naturalPhenomenonParameters = naturalPhenomenonParameters == null ? new HashMap<>() : new HashMap<>(naturalPhenomenonParameters);
    }

    public Map<String, Double> targetClimateMetrics() {
        return targetClimateMetrics;
    }

    public void setTargetClimateMetrics(Map<String, Double> targetClimateMetrics) {
        this.targetClimateMetrics = targetClimateMetrics == null ? new HashMap<>() : new HashMap<>(targetClimateMetrics);
    }

    public SeasonPlanStatus status() {
        return status;
    }

    public void setStatus(SeasonPlanStatus status) {
        this.status = status;
    }

    public String approvalComment() {
        return approvalComment;
    }

    public void setApprovalComment(String approvalComment) {
        this.approvalComment = approvalComment;
    }

    public List<ChecklistItem> readinessChecklist() {
        return readinessChecklist;
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
        this.seasonType = draft.seasonType;
        setTargetTerritories(draft.targetTerritories);
        setTimeWindows(draft.timeWindows);
        setNaturalPhenomenonParameters(draft.naturalPhenomenonParameters);
        setTargetClimateMetrics(draft.targetClimateMetrics);
        this.updatedBy = actorId;
        this.updatedAt = now;
    }
}
