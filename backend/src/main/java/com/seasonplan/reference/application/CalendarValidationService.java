package com.seasonplan.reference.application;

import com.seasonplan.reference.api.CalendarRequest;
import com.seasonplan.reference.api.NaturalCalendarValidation;
import com.seasonplan.reference.application.port.NaturalCalendarClient;
import java.util.List;
import java.util.Objects;

/** Keeps the external calendar port private and snapshots adapter results at the public boundary. */
public final class CalendarValidationService implements NaturalCalendarValidation {
    private final NaturalCalendarClient client;
    public CalendarValidationService(NaturalCalendarClient client) { this.client = Objects.requireNonNull(client); }
    @Override public List<String> validate(CalendarRequest request) { return List.copyOf(client.validate(request)); }
}
