package com.seasonplan.work.api;

import com.seasonplan.shared.domain.TimeWindow;
import com.seasonplan.work.domain.MagicTaskStatus;

/** Immutable task input. Window and status are immutable values; owner domain validates all fields. */
public record TaskDraft(String title, String description, String requiredTalent, TimeWindow timeWindow, MagicTaskStatus status, String category) { }
