package com.seasonplan.identity.api;

import com.seasonplan.identity.domain.LifecycleAuthorizationPolicy;

/** Public identity decision contract. Callers supply scalar ownership facts, never owner aggregates. */
public final class LifecycleAccessPolicy {
    private LifecycleAccessPolicy() { }
    public static void require(ActorRole role, LifecyclePermission permission) {
        LifecycleAuthorizationPolicy.require(role, permission);
    }
    public static ActorRole roleFor(CurrentActor actor, LifecyclePermission permission) {
        return LifecycleAuthorizationPolicy.roleFor(actor, permission);
    }
    public static void requireDraftOwner(ActorRole role, String actorId, String creatorId) {
        LifecycleAuthorizationPolicy.requireDraftOwner(role, actorId, creatorId);
    }
    public static void requireReviewHistory(CurrentActor actor, String creatorId) {
        LifecycleAuthorizationPolicy.requireReviewHistory(actor, creatorId);
    }
    public static void requireReportOwner(ActorRole role, String actorId, String creatorId) {
        LifecycleAuthorizationPolicy.requireReportOwner(role, actorId, creatorId);
    }
    public static void requireCorrectionOwner(ActorRole role, String actorId, String ownerId) {
        LifecycleAuthorizationPolicy.requireCorrectionOwner(role, actorId, ownerId);
    }
}
