package com.seasonplan.notification.application.port;

import com.seasonplan.notification.api.NotificationInboxItem;
import java.util.List;
import java.util.UUID;

/** Reads at most the 50 newest notifications for one recipient, with stable event-id tie breaking. */
public interface NotificationInboxRepository {
    List<NotificationInboxItem> findLatestByRecipient(UUID recipientId);
}
