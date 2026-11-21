package com.seasonplan.lifecycle.infrastructure.fake;

import com.seasonplan.lifecycle.integration.calendar.NaturalCalendarClient;

import com.seasonplan.lifecycle.domain.SeasonPlan;

import java.util.List;

public class AllowingNaturalCalendarClient implements NaturalCalendarClient {
    @Override
    public List<String> validate(SeasonPlan plan) {
        if ("false".equalsIgnoreCase(plan.naturalPhenomenonParameters().get("calendarAllowed"))) {
            return List.of("natural calendar rules reject this season plan");
        }
        return List.of();
    }
}
