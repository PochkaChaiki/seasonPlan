package com.seasonplan.season.api;

import java.util.List;
import java.util.UUID;

/** Readiness evidence required by the season owner, without coupling it to ritual implementation types. */
@FunctionalInterface
public interface SeasonReadiness {
    List<String> blockers(UUID planId);
}
