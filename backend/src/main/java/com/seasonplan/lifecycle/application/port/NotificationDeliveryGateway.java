package com.seasonplan.lifecycle.application.port;

/** A replaceable delivery boundary. Consumers must deduplicate by event id, recipient and channel. */
public interface NotificationDeliveryGateway {
    void deliver(NotificationOutbox.Event event, String recipient, NotificationOutbox.Channel channel);
}
