package com.seasonplan.lifecycle.config;

import com.seasonplan.reference.api.CalendarRequest;
import com.seasonplan.reference.application.CalendarValidationService;
import com.seasonplan.shared.domain.TimeWindow;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.assertj.core.api.Assertions.*;

class CalendarValidationBoundaryTest {
    @Test void calendarRequestSnapshotsCollectionsWithoutChangingInvalidDraftValues() {
        var territories = new ArrayList<>(Arrays.asList("north", null));
        var windows = new ArrayList<TimeWindow>();
        var parameters = new HashMap<>(Map.of("calendarAllowed", "false"));
        var targets = new HashMap<String, Double>();
        targets.put("temperature", null);
        var request = new CalendarRequest("SPRING", territories, windows, parameters, targets);
        territories.clear(); windows.add(new TimeWindow(null, null)); parameters.clear(); targets.clear();
        assertThat(request.targetTerritories()).containsExactly("north", null);
        assertThat(request.timeWindows()).isEmpty();
        assertThat(request.naturalPhenomenonParameters()).containsEntry("calendarAllowed", "false");
        assertThat(request.targetClimateMetrics()).containsEntry("temperature", null);
        assertThatThrownBy(() -> request.targetTerritories().clear()).isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> request.timeWindows().clear()).isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> request.naturalPhenomenonParameters().clear()).isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> request.targetClimateMetrics().clear()).isInstanceOf(UnsupportedOperationException.class);
    }
    @Test void ownerPassesReferenceDataToAdapterAndReturnsImmutableValidationResults() {
        var errors = new ArrayList<>(List.of("calendar unavailable"));
        var request = new CalendarRequest("SPRING", List.of(), List.of(), Map.of(), Map.of());
        var service = new CalendarValidationService(input -> { assertThat(input).isSameAs(request); return errors; });
        var result = service.validate(request);
        errors.clear();
        assertThat(result).containsExactly("calendar unavailable");
        assertThatThrownBy(result::clear).isInstanceOf(UnsupportedOperationException.class);
    }
}
