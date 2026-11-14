package com.seasonplan.lifecycle.persistence;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.seasonplan.lifecycle.application.port.NotificationDeliveryGateway;
import com.seasonplan.lifecycle.application.port.NotificationOutbox;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/** Polls committed events in independent transactions; a channel failure cannot undo the command. */
public class JdbcNotificationDeliveryWorker {
    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;
    private final NotificationDeliveryGateway gateway;
    private final TransactionTemplate transaction;
    private final Clock clock;

    public JdbcNotificationDeliveryWorker(JdbcTemplate jdbc, ObjectMapper mapper, NotificationDeliveryGateway gateway,
                                          PlatformTransactionManager manager, Clock clock) {
        this.jdbc = jdbc;
        this.mapper = mapper;
        this.gateway = gateway;
        this.transaction = new TransactionTemplate(manager);
        this.clock = clock;
    }

    @Scheduled(fixedDelayString = "${notifications.poll-delay-ms:30000}", initialDelayString = "${notifications.initial-delay-ms:60000}")
    public void deliverPending() {
        for (int i = 0; i < 100; i++) {
            Boolean processed = transaction.execute(status -> deliverOne());
            if (!Boolean.TRUE.equals(processed)) break;
        }
    }

    private boolean deliverOne() {
        Instant now = clock.instant();
        jdbc.update("update notification_outbox_deliveries set status = 'EXPIRED' where status = 'PENDING' and expires_at <= ?", Timestamp.from(now));
        List<Delivery> rows = jdbc.query("""
                select d.event_id, d.recipient, d.channel, d.attempts, d.expires_at,
                       o.season_plan_id, o.event_type, o.recipients, o.channels, o.message, o.occurred_at
                  from notification_outbox_deliveries d join notification_outbox o on o.event_id = d.event_id
                 where d.status = 'PENDING' and d.next_attempt_at <= ? and d.expires_at > ?
                 order by d.next_attempt_at, d.event_id, d.recipient, d.channel
                 for update of d skip locked limit 1
                """, (rs, row) -> new Delivery(rs.getString("event_id"), rs.getString("recipient"),
                NotificationOutbox.Channel.valueOf(rs.getString("channel")), rs.getInt("attempts"),
                rs.getTimestamp("expires_at").toInstant(), rs.getObject("season_plan_id", UUID.class),
                rs.getString("event_type"), rs.getString("recipients"), rs.getString("channels"),
                rs.getString("message"), rs.getTimestamp("occurred_at").toInstant()), Timestamp.from(now), Timestamp.from(now));
        if (rows.isEmpty()) return false;
        Delivery delivery = rows.get(0);
        try {
            gateway.deliver(event(delivery), delivery.recipient(), delivery.channel());
            jdbc.update("update notification_outbox_deliveries set status = 'DELIVERED', attempts = attempts + 1, delivered_at = ? where event_id = ? and recipient = ? and channel = ?",
                    Timestamp.from(now), delivery.eventId(), delivery.recipient(), delivery.channel().name());
        } catch (RuntimeException failure) {
            long delaySeconds = 1L << Math.min(delivery.attempts(), 16);
            Instant next = now.plusSeconds(delaySeconds);
            if (next.isAfter(delivery.expiresAt())) next = delivery.expiresAt();
            jdbc.update("update notification_outbox_deliveries set attempts = attempts + 1, next_attempt_at = ? where event_id = ? and recipient = ? and channel = ?",
                    Timestamp.from(next), delivery.eventId(), delivery.recipient(), delivery.channel().name());
        }
        return true;
    }

    private NotificationOutbox.Event event(Delivery delivery) {
        try {
            Set<String> recipients = mapper.readValue(delivery.recipients(), new TypeReference<>() {});
            Set<NotificationOutbox.Channel> channels = mapper.readValue(delivery.channels(), new TypeReference<>() {});
            return new NotificationOutbox.Event(delivery.eventId(), delivery.planId(), delivery.type(), recipients,
                    channels, delivery.message(), delivery.occurredAt());
        } catch (com.fasterxml.jackson.core.JsonProcessingException error) {
            throw new IllegalStateException("invalid outbox payload", error);
        }
    }

    private record Delivery(String eventId, String recipient, NotificationOutbox.Channel channel, int attempts,
                            Instant expiresAt, UUID planId, String type, String recipients, String channels,
                            String message, Instant occurredAt) {}
}
