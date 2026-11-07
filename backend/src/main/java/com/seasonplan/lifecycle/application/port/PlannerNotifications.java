package com.seasonplan.lifecycle.application.port;

/** Existing UC-2 notification contract, isolated from its temporary in-memory adapter. */
public interface PlannerNotifications {
    void notifyPlannerApprovalResult(String plannerId, String decision, String comment);
}
