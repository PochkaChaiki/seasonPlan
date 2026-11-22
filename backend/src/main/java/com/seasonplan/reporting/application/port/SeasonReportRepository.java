package com.seasonplan.reporting.application.port;

import com.seasonplan.reporting.domain.SeasonReport;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SeasonReportRepository {
    Optional<SeasonReport> latestDraft(UUID planId);
    Optional<SeasonReport> latestApproved(UUID planId);
    int nextVersion(UUID planId);
    /** Replaces only the current DRAFT; never changes any approved snapshot. */
    void saveDraft(SeasonReport report);
    /** Append-only durable storage, atomically participating in CommandTransaction. */
    void appendApproved(SeasonReport report);
    List<SeasonReport> approvedForSeasonType(String seasonType);
}
