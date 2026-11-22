package com.seasonplan.identity.api;



/** Inbound port: application code never depends on a security-provider type. */
public interface CurrentActorProvider {
    CurrentActor currentActor();
}
