package com.seasonplan.lifecycle.application.port;

import com.seasonplan.lifecycle.domain.SeasonReport;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public final class CompletionPorts {
    private CompletionPorts() { }
    public interface Reports {
        Optional<SeasonReport> latestDraft(UUID planId);
        Optional<SeasonReport> latestApproved(UUID planId);
        int nextVersion(UUID planId);
        /** Replaces only the current DRAFT; never changes any approved snapshot. */
        void saveDraft(SeasonReport report);
        /** Append-only durable storage, atomically participating in CommandTransaction. */
        void appendApproved(SeasonReport report);
        List<SeasonReport> approvedForSeasonType(String seasonType);
    }
    /** Must include every required task, including required catalog categories missing from the plan. */
    public interface Tasks { List<SeasonReport.TaskResult> requiredResults(UUID planId); }
    public interface CatalogVersion { String forPlan(UUID planId); }
    public interface Exporter {
        /** Render only the supplied approved immutable snapshot; no live-data queries. */
        byte[] render(SeasonReport approved, String format);
    }
}
