package com.seasonplan.lifecycle.persistence;

import com.seasonplan.lifecycle.application.port.NotificationOutbox;
import com.seasonplan.lifecycle.application.port.NotificationRecipientResolver;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.InternetAddress;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;

import java.util.function.Supplier;

/** SMTP transport; downstream consumers must deduplicate the stable event-id header. */
public class SmtpNotificationEmailGateway {
    private final Supplier<JavaMailSender> sender;
    private final NotificationRecipientResolver recipients;
    private final String from;

    public SmtpNotificationEmailGateway(Supplier<JavaMailSender> sender, NotificationRecipientResolver recipients, String from) {
        this.sender = sender;
        this.recipients = recipients;
        this.from = from;
    }

    public void send(NotificationOutbox.Event event, String recipientId) {
        JavaMailSender transport = sender.get();
        if (transport == null) throw new IllegalStateException("SMTP delivery is not configured");
        String address = recipients.emailAddress(recipientId);
        try {
            if (from == null || from.isBlank() || from.contains("\r") || from.contains("\n")) throw new MessagingException();
            InternetAddress fromAddress = new InternetAddress(from, true);
            fromAddress.validate();
            if (fromAddress.getPersonal() != null || !from.equals(fromAddress.getAddress())) throw new MessagingException();
            var message = transport.createMimeMessage();
            var helper = new MimeMessageHelper(message, "UTF-8");
            helper.setFrom(fromAddress);
            helper.setTo(address);
            helper.setSubject("SeasonPlan: " + event.type());
            helper.setText(event.message(), false);
            message.setHeader("X-SeasonPlan-Event-ID", event.deduplicationKey());
            transport.send(message);
        } catch (MessagingException error) {
            throw new IllegalStateException("notification email configuration or message is invalid");
        }
    }
}
