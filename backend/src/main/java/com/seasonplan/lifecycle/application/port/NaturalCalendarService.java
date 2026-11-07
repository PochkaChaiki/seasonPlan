package com.seasonplan.lifecycle.application.port;

import com.seasonplan.lifecycle.domain.SeasonPlan;

import java.util.List;

public interface NaturalCalendarService {
    List<String> validate(SeasonPlan plan);
}
