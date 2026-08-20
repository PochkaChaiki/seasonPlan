package com.seasonplan.lifecycle.service;

import com.seasonplan.lifecycle.domain.CurrentActor;

/** Inbound port: application code never depends on a security-provider type. */
public interface CurrentActorProvider {
    CurrentActor currentActor();
}
