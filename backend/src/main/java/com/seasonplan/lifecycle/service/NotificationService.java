package com.seasonplan.lifecycle.service;

import java.util.ArrayList;
import java.util.List;

public class NotificationService {
    private final List<String> notifications = new ArrayList<>();

    public void notifyPlannerApprovalResult(String plannerId, String decision, String comment) {
        notifications.add(plannerId + ":" + decision + ":" + comment);
    }

    public List<String> notifications() {
        return List.copyOf(notifications);
    }
}
