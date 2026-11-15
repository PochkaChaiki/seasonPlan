package com.seasonplan.lifecycle.integration.calendar;

import com.seasonplan.lifecycle.domain.SeasonPlan;

import java.util.List;

public interface NaturalCalendarClient {
    List<String> validate(SeasonPlan plan);
}
