package com.seasonplan.bootstrap;

import com.seasonplan.bootstrap.LifecycleConfiguration;

import com.seasonplan.reference.adapter.out.fake.AllowingNaturalCalendarClient;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import static org.assertj.core.api.Assertions.assertThat;

class NaturalCalendarConfigurationTest {
    @Test
    void productionAndDefaultFailClosedEvenWithDevProfile() {
        var configuration = new LifecycleConfiguration();
        var defaultClient = configuration.naturalCalendarService(new MockEnvironment());
        var production = new MockEnvironment();
        production.setActiveProfiles("prod", "dev");
        var productionClient = configuration.naturalCalendarService(production);
        assertThat(defaultClient).isNotInstanceOf(AllowingNaturalCalendarClient.class);
        assertThat(defaultClient.validate(null)).isNotEmpty();
        assertThat(productionClient).isNotInstanceOf(AllowingNaturalCalendarClient.class);
        assertThat(productionClient.validate(null)).isNotEmpty();
    }

    @Test
    void testProfileCanUseFake() {
        var environment = new MockEnvironment();
        environment.setActiveProfiles("test");
        assertThat(new LifecycleConfiguration().naturalCalendarService(environment))
                .isInstanceOf(AllowingNaturalCalendarClient.class);
    }
    @Test
    void publicBoundaryRetainsProfileBehaviorAndFakeCalendarRejection() {
        var configuration = new LifecycleConfiguration();
        var allowed = new com.seasonplan.reference.api.CalendarRequest("SPRING", java.util.List.of("north"), java.util.List.of(), java.util.Map.of(), java.util.Map.of());
        var rejected = new com.seasonplan.reference.api.CalendarRequest("SPRING", java.util.List.of("north"), java.util.List.of(), java.util.Map.of("calendarAllowed", "false"), java.util.Map.of());
        for (String[] profiles : new String[][]{{}, {"prod"}, {"prod", "dev"}, {"prod", "test"}}) {
            var environment = new MockEnvironment();
            environment.setActiveProfiles(profiles);
            var api = configuration.naturalCalendarValidation(configuration.naturalCalendarService(environment));
            assertThat(api.validate(allowed)).containsExactly("natural calendar validation is unavailable");
        }
        for (String profile : new String[]{"dev", "test"}) {
            var environment = new MockEnvironment();
            environment.setActiveProfiles(profile);
            var api = configuration.naturalCalendarValidation(configuration.naturalCalendarService(environment));
            assertThat(api.validate(allowed)).isEmpty();
            assertThat(api.validate(rejected)).containsExactly("natural calendar rules reject this season plan");
        }
    }
}
