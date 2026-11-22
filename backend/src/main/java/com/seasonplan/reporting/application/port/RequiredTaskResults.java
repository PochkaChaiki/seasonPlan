package com.seasonplan.reporting.application.port;

import com.seasonplan.reporting.domain.SeasonReport;
import java.util.List;
import java.util.UUID;

/** Must include every required task, including required catalog categories missing from the plan. */
public interface RequiredTaskResults {
    List<SeasonReport.TaskResult> requiredResults(UUID planId);
}
