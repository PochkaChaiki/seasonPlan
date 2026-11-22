package com.seasonplan.lifecycle.service;

import com.seasonplan.pollen.application.PollenService;
import com.seasonplan.pollen.application.port.PollenTreeClient;
import com.seasonplan.pollen.adapter.out.fake.InMemoryPollenIntentRepository;
import com.seasonplan.pollen.adapter.out.persistence.SpringRemoteCommandBoundary;
import com.seasonplan.season.api.*;
import com.seasonplan.audit.api.AuditTrail;
import com.seasonplan.notification.api.NotificationOutbox;
import java.time.Clock;
import java.util.function.Supplier;

/** Explicit deterministic unit-test store; the production bean requires the real JDBC journal. */
final class PollenTestServices {
    private PollenTestServices() { }
    static PollenService create(SeasonQueries seasons, PollenTreeClient client, Supplier<NotificationOutbox> outbox,
                                CommandTransaction transactions, AuditTrail audit, Clock clock) {
        return new PollenService(seasons, client, outbox, transactions, audit, clock,
                new InMemoryPollenIntentRepository(), new SpringRemoteCommandBoundary());
    }
}
