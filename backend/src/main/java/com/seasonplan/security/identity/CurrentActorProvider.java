package com.seasonplan.security.identity;



/** Inbound port: application code never depends on a security-provider type. */
public interface CurrentActorProvider {
    CurrentActor currentActor();
}
