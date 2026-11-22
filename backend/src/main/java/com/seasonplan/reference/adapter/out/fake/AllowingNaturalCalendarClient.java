package com.seasonplan.reference.adapter.out.fake;

import com.seasonplan.reference.application.port.NaturalCalendarClient;

import com.seasonplan.reference.api.CalendarRequest;

import java.util.List;

public class AllowingNaturalCalendarClient implements NaturalCalendarClient {
    @Override
    public List<String> validate(CalendarRequest plan) {
        if ("false".equalsIgnoreCase(plan.naturalPhenomenonParameters().get("calendarAllowed"))) {
            return List.of("natural calendar rules reject this season plan");
        }
        return List.of();
    }
}
