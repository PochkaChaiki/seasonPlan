package com.seasonplan.lifecycle.application.port;

import java.time.Instant;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/** A transactional append-only outbox. Repeated deduplication keys must not create another event. */
public interface NotificationOutbox {
    enum Channel { IN_APP, EMAIL }
    record Event(String deduplicationKey, UUID seasonPlanId, String type, Set<String> recipients,
                 Set<Channel> channels, String message, Instant occurredAt) {
        public Event {
            Objects.requireNonNull(deduplicationKey); Objects.requireNonNull(seasonPlanId); Objects.requireNonNull(type);
            Objects.requireNonNull(message); Objects.requireNonNull(occurredAt);
            recipients = Set.copyOf(recipients); channels = Set.copyOf(channels);
        }
    }
    void enqueue(Event event);
}
