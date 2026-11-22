package com.seasonplan.notification.api;

/** Resolves an application recipient id to a verified delivery address or throws. */
public interface NotificationRecipientResolver {
    String emailAddress(String recipientId);
}
