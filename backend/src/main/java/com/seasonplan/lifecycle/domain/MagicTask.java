package com.seasonplan.lifecycle.domain;

import java.util.UUID;

public class MagicTask {
    private UUID id;
    private UUID seasonPlanId;
    private String title;
    private String description;
    private String requiredTalent;
    private TimeWindow timeWindow;
    private MagicTaskStatus status;

    public MagicTask() {
    }

    public MagicTask(String title, String description, String requiredTalent, TimeWindow timeWindow, MagicTaskStatus status) {
        this.title = title;
        this.description = description;
        this.requiredTalent = requiredTalent;
        this.timeWindow = timeWindow;
        this.status = status;
    }

    public UUID id() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public UUID seasonPlanId() {
        return seasonPlanId;
    }

    public void setSeasonPlanId(UUID seasonPlanId) {
        this.seasonPlanId = seasonPlanId;
    }

    public String title() {
        return title;
    }

    public String description() {
        return description;
    }

    public String requiredTalent() {
        return requiredTalent;
    }

    public TimeWindow timeWindow() {
        return timeWindow;
    }

    public MagicTaskStatus status() {
        return status;
    }

    public void updateFrom(MagicTask draft) {
        this.title = draft.title;
        this.description = draft.description;
        this.requiredTalent = draft.requiredTalent;
        this.timeWindow = draft.timeWindow;
        this.status = draft.status;
    }
}
