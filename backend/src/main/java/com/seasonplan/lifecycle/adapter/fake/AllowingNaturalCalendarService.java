package com.seasonplan.lifecycle.adapter.fake;

import com.seasonplan.lifecycle.application.port.NaturalCalendarService;

import com.seasonplan.lifecycle.domain.SeasonPlan;

import java.util.List;

public class AllowingNaturalCalendarService implements NaturalCalendarService {
    @Override
    public List<String> validate(SeasonPlan plan) {
        if ("false".equalsIgnoreCase(plan.naturalPhenomenonParameters().get("calendarAllowed"))) {
            return List.of("natural calendar rules reject this season plan");
        }
        return List.of();
    }
}
