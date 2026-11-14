package com.seasonplan.lifecycle.persistence;

import com.seasonplan.lifecycle.application.port.NotificationInboxRepository;
import com.seasonplan.lifecycle.domain.NotificationInboxItem;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public class JdbcNotificationInboxRepository implements NotificationInboxRepository {
    private final JdbcTemplate jdbc;

    public JdbcNotificationInboxRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    @Override
    public List<NotificationInboxItem> findLatestByRecipient(UUID recipientId) {
        return List.copyOf(jdbc.query("""
                select event_id, season_plan_id, event_type, message, occurred_at
                  from notification_inbox where recipient = ?
                 order by occurred_at desc, event_id desc limit 50
                """, (rs, row) -> new NotificationInboxItem(rs.getString("event_id"),
                rs.getObject("season_plan_id", UUID.class), rs.getString("event_type"),
                rs.getString("message"), rs.getTimestamp("occurred_at").toInstant()), recipientId.toString()));
    }
}
