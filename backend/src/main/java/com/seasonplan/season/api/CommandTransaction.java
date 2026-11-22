package com.seasonplan.season.api;

import java.util.UUID;
import java.util.function.Supplier;

/**
 * Executes one command atomically, including state, audit and outbox writes.
 * Implementations MUST serialize commands for the plan (including monitoring and response)
 * and roll back every participating repository on failure. No no-op production default is supplied.
 */
public interface CommandTransaction {
    <T> T execute(UUID seasonPlanId, Supplier<T> command);
    /** Atomically creates a new plan and its audit records before any plan row exists to lock. */
    <T> T create(Supplier<T> command);
}
