package com.seasonplan.lifecycle.application.port;

import com.seasonplan.lifecycle.domain.CurrentActor;

/** Inbound port: application code never depends on a security-provider type. */
public interface CurrentActorProvider {
    CurrentActor currentActor();
}
