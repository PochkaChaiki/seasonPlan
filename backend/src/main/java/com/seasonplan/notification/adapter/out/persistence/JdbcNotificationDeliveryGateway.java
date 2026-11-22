package com.seasonplan.notification.adapter.out.persistence;

import com.seasonplan.notification.adapter.out.mail.SmtpNotificationEmailGateway;

import com.seasonplan.notification.api.NotificationDeliveryGateway;
import com.seasonplan.notification.api.NotificationOutbox;
import org.springframework.jdbc.core.JdbcTemplate;

import java.sql.Timestamp;

/** Durable in-app sink and replaceable SMTP transport. */
public class JdbcNotificationDeliveryGateway implements NotificationDeliveryGateway {
    private final JdbcTemplate jdbc;
    private final SmtpNotificationEmailGateway email;

    public JdbcNotificationDeliveryGateway(JdbcTemplate jdbc, SmtpNotificationEmailGateway email) {
        this.jdbc = jdbc;
        this.email = email;
    }

    @Override
    public void deliver(NotificationOutbox.Event event, String recipient, NotificationOutbox.Channel channel) {
        if (channel == NotificationOutbox.Channel.EMAIL) {
            email.send(event, recipient);
            return;
        }
        jdbc.update("""
                insert into notification_inbox (event_id, recipient, season_plan_id, event_type, message, occurred_at)
                values (?, ?, ?, ?, ?, ?) on conflict (event_id, recipient) do nothing
                """, event.deduplicationKey(), recipient, event.seasonPlanId(), event.type(), event.message(),
                Timestamp.from(event.occurredAt()));
    }
}
