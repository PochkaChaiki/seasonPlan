package com.seasonplan.lifecycle.adapter.fake;

import com.seasonplan.lifecycle.application.port.PlannerNotifications;

import java.util.ArrayList;
import java.util.List;

public class NotificationService implements PlannerNotifications {
    private final List<String> notifications = new ArrayList<>();

    public void notifyPlannerApprovalResult(String plannerId, String decision, String comment) {
        notifications.add(plannerId + ":" + decision + ":" + comment);
    }

    public List<String> notifications() {
        return List.copyOf(notifications);
    }
}
