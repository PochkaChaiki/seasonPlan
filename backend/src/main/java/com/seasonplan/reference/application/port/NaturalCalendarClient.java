package com.seasonplan.reference.application.port;

import com.seasonplan.reference.api.CalendarRequest;

import java.util.List;

public interface NaturalCalendarClient {
    List<String> validate(CalendarRequest plan);
}
