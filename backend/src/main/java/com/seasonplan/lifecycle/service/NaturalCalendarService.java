package com.seasonplan.lifecycle.service;

import com.seasonplan.lifecycle.domain.SeasonPlan;

import java.util.List;

public interface NaturalCalendarService {
    List<String> validate(SeasonPlan plan);
}
