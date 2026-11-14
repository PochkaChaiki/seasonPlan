package com.seasonplan.lifecycle.persistence;

import com.seasonplan.lifecycle.application.port.NotificationRecipientResolver;
import jakarta.mail.internet.AddressException;
import jakarta.mail.internet.InternetAddress;

import java.util.Map;

/** Temporary deployment-configured identity-to-email mapping, replaceable by an identity directory. */
public class ConfiguredNotificationRecipientResolver implements NotificationRecipientResolver {
    private final Map<String, String> recipients;
    public ConfiguredNotificationRecipientResolver(Map<String, String> recipients) { this.recipients = Map.copyOf(recipients); }

    @Override
    public String emailAddress(String recipientId) {
        String value = recipients.get(recipientId);
        try {
            if (value == null || value.isBlank() || value.contains("\r") || value.contains("\n")) throw new AddressException();
            InternetAddress address = new InternetAddress(value, true);
            address.validate();
            if (address.getPersonal() != null || !value.equals(address.getAddress())) throw new AddressException();
            return value;
        } catch (AddressException error) {
            throw new IllegalStateException("notification email address is not configured or invalid");
        }
    }
}
