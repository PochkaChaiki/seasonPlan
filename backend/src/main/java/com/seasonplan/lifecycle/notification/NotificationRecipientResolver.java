package com.seasonplan.lifecycle.notification;

/** Resolves an application recipient id to a verified delivery address or throws. */
public interface NotificationRecipientResolver {
    String emailAddress(String recipientId);
}
