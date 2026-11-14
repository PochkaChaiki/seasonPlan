package com.seasonplan.lifecycle.domain;

import java.time.Instant;
import java.util.UUID;

/** Immutable in-app notification shown to its recipient. */
public record NotificationInboxItem(String eventId, UUID seasonPlanId, String eventType, String message, Instant occurredAt) {}
