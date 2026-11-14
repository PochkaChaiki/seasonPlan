package com.seasonplan.lifecycle.persistence;

import com.seasonplan.lifecycle.application.port.NotificationOutbox;
import com.seasonplan.lifecycle.config.NotificationDeliveryConfiguration;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.Test;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;

import java.time.Instant;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class SmtpNotificationEmailGatewayTest {
    private final String userId = "11111111-1111-1111-1111-111111111111";
    private final NotificationOutbox.Event event = new NotificationOutbox.Event("event-1", UUID.randomUUID(), "SEASON_PLAN_APPROVED",
            Set.of(userId), Set.of(NotificationOutbox.Channel.EMAIL), "decision", Instant.EPOCH);

    @Test void sendsMappedRecipientWithStableEventId() throws Exception {
        JavaMailSender sender = mock(JavaMailSender.class);
        MimeMessage message = new MimeMessage(Session.getInstance(new Properties()));
        when(sender.createMimeMessage()).thenReturn(message);
        var resolver = new ConfiguredNotificationRecipientResolver(Map.of(userId, "planner@example.invalid"));
        var gateway = new SmtpNotificationEmailGateway(() -> sender, resolver, "sender@example.invalid");
        gateway.send(event, userId);
        verify(sender).send(message);
        assertThat(message.getAllRecipients()[0].toString()).isEqualTo("planner@example.invalid");
        assertThat(message.getHeader("X-SeasonPlan-Event-ID", null)).isEqualTo("event-1");
    }

    @Test void missingInvalidMappingMissingTransportAndSmtpErrorsFailClosed() {
        JavaMailSender sender = mock(JavaMailSender.class);
        when(sender.createMimeMessage()).thenReturn(new MimeMessage(Session.getInstance(new Properties())));
        for (Map<String, String> mapping : java.util.List.of(Map.<String, String>of(), Map.of(userId, "invalid"))) {
            var gateway = new SmtpNotificationEmailGateway(() -> sender, new ConfiguredNotificationRecipientResolver(mapping), "sender@example.invalid");
            assertThatThrownBy(() -> gateway.send(event, userId)).isInstanceOf(RuntimeException.class);
        }
        var resolver = new ConfiguredNotificationRecipientResolver(Map.of(userId, "planner@example.invalid"));
        assertThatThrownBy(() -> new SmtpNotificationEmailGateway(() -> null, resolver, "sender@example.invalid").send(event, userId)).isInstanceOf(RuntimeException.class);
        doThrow(new org.springframework.mail.MailSendException("offline")).when(sender).send(any(MimeMessage.class));
        assertThatThrownBy(() -> new SmtpNotificationEmailGateway(() -> sender, resolver, "sender@example.invalid").send(event, userId)).isInstanceOf(org.springframework.mail.MailSendException.class);
    }

    @Test void smtpRequiresTlsAndBoundedTimeouts() {
        var settings = new NotificationDeliveryConfiguration.SmtpSettings();
        settings.setHost("smtp.example.invalid"); settings.setPort(587); settings.setUsername("user"); settings.setPassword("secret");
        JavaMailSenderImpl sender = NotificationDeliveryConfiguration.createSmtpSender(settings);
        assertThat(sender.getJavaMailProperties()).containsEntry("mail.smtp.starttls.enable", "true")
                .containsEntry("mail.smtp.starttls.required", "true").containsEntry("mail.smtp.ssl.checkserveridentity", "true")
                .containsEntry("mail.smtp.ssl.protocols", "TLSv1.3 TLSv1.2")
                .containsEntry("mail.smtp.auth", "true").containsEntry("mail.smtp.connectiontimeout", "3000")
                .containsEntry("mail.smtp.timeout", "3000").containsEntry("mail.smtp.writetimeout", "3000");
    }
}
