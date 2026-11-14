package com.seasonplan.lifecycle.persistence;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.seasonplan.lifecycle.application.port.NotificationOutbox;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;

/** Transactional event and per-recipient/channel delivery storage. */
@Repository
public class JdbcNotificationOutbox implements NotificationOutbox {
    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;

    public JdbcNotificationOutbox(JdbcTemplate jdbc, ObjectMapper mapper) {
        this.jdbc = jdbc;
        this.mapper = mapper;
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void enqueue(Event event) {
        int inserted = jdbc.update("""
                insert into notification_outbox
                    (event_id, season_plan_id, event_type, recipients, channels, message, occurred_at)
                values (?, ?, ?, cast(? as jsonb), cast(? as jsonb), ?, ?)
                on conflict (event_id) do nothing
                """, event.deduplicationKey(), event.seasonPlanId(), event.type(),
                JdbcJson.write(mapper, event.recipients()), JdbcJson.write(mapper, event.channels()),
                event.message(), Timestamp.from(event.occurredAt()));
        if (inserted == 0) return;
        for (String recipient : event.recipients()) {
            for (Channel channel : event.channels()) {
                jdbc.update("""
                        insert into notification_outbox_deliveries (event_id, recipient, channel, next_attempt_at, expires_at)
                        values (?, ?, ?, ?, ?) on conflict do nothing
                        """, event.deduplicationKey(), recipient, channel.name(), Timestamp.from(event.occurredAt()),
                        Timestamp.from(event.occurredAt().plusSeconds(24 * 60 * 60)));
            }
        }
    }
}
