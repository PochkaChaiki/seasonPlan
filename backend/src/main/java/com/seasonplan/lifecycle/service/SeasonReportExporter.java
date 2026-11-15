package com.seasonplan.lifecycle.service;

import com.seasonplan.lifecycle.domain.SeasonReport;

public interface SeasonReportExporter {
    /** Render only the supplied approved immutable snapshot; no live-data queries. */
    byte[] render(SeasonReport approved, String format);
}
