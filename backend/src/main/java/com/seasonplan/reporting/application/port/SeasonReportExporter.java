package com.seasonplan.reporting.application.port;

import com.seasonplan.reporting.domain.SeasonReport;

public interface SeasonReportExporter {
    /** Render only the supplied approved immutable snapshot; no live-data queries. */
    byte[] render(SeasonReport approved, String format);
}
