package com.seasonplan.reference.api;

import java.util.List;

/** Validates reference-owned calendar data and returns an immutable list of calendar errors. */
public interface NaturalCalendarValidation {
    List<String> validate(CalendarRequest request);
}
