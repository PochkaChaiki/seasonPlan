package com.seasonplan.lifecycle.config;

import com.seasonplan.bootstrap.LifecycleConfiguration;

import com.seasonplan.season.application.port.SeasonPlanRepository;
import com.seasonplan.work.application.port.MagicTaskRepository;
import com.seasonplan.work.application.port.FairyAssignmentRepository;
import com.seasonplan.audit.application.port.AuditLogRepository;
import com.seasonplan.notification.api.NotificationOutbox;
import com.seasonplan.pollen.application.port.PollenTreeClient;
import com.seasonplan.pollen.adapter.out.fake.FakePollenTreeClient;
import com.seasonplan.audit.adapter.out.persistence.InMemoryAuditLogRepository;
import com.seasonplan.work.adapter.out.fake.InMemoryFairyAssignmentRepository;
import com.seasonplan.work.adapter.out.fake.InMemoryMagicTaskRepository;
import com.seasonplan.season.adapter.out.fake.InMemorySeasonPlanRepository;

import com.seasonplan.pollen.application.PollenService;

import com.seasonplan.identity.api.ActorRole;
import com.seasonplan.pollen.domain.PollenReservationStatus;
import com.seasonplan.season.application.SeasonPlanService;
import com.seasonplan.lifecycle.service.SeasonPlanTestFixture;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;

import java.math.BigDecimal;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class PollenServiceCompositionTest {
    @Test void reservationsNeedNoOutboxAndUnconfiguredConsumptionFailsBeforeSpending() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        new ApplicationContextRunner().withPropertyValues("spring.profiles.active=test")
                .withUserConfiguration(LifecycleConfiguration.class, com.seasonplan.bootstrap.AuditConfiguration.class)
                .withBean(SeasonPlanRepository.class, InMemorySeasonPlanRepository::new)
                .withBean(AuditLogRepository.class, InMemoryAuditLogRepository::new)
                .withBean(MagicTaskRepository.class, InMemoryMagicTaskRepository::new)
                .withBean(FairyAssignmentRepository.class, InMemoryFairyAssignmentRepository::new)
                .withBean(com.seasonplan.response.application.port.MagicIncidentRepository.class, () -> mock(com.seasonplan.response.application.port.MagicIncidentRepository.class))
                .withBean(com.seasonplan.response.application.port.CorrectionActionRepository.class, () -> mock(com.seasonplan.response.application.port.CorrectionActionRepository.class))
                .withBean(com.seasonplan.monitoring.application.port.ClimateMetricRepository.class, () -> mock(com.seasonplan.monitoring.application.port.ClimateMetricRepository.class))
                .withBean(com.seasonplan.ritual.application.port.RitualSessionRepository.class, () -> mock(com.seasonplan.ritual.application.port.RitualSessionRepository.class))
                .withBean(com.seasonplan.notification.adapter.out.persistence.JdbcNotificationOutbox.class, () -> mock(com.seasonplan.notification.adapter.out.persistence.JdbcNotificationOutbox.class))
                .withPropertyValues("seasonplan.execution-rules.version=test", "seasonplan.execution-rules.required-events=LAUNCH", "seasonplan.incident-rules.version=test", "seasonplan.incident-rules.severity.RITUAL=HIGH", "seasonplan.incident-rules.severity.POLLEN=HIGH", "seasonplan.incident-rules.severity.CLIMATE=HIGH", "seasonplan.incident-rules.severity.ASSIGNMENT=HIGH", "seasonplan.incident-rules.severity.SECURITY=HIGH",
                        "seasonplan.execution-rules.metrics.temperature.tolerance=2", "seasonplan.execution-rules.metrics.temperature.owner=owner", "seasonplan.execution-rules.metrics.temperature.response-seconds=60",
                        "seasonplan.execution-rules.metrics.humidity.tolerance=2", "seasonplan.execution-rules.metrics.humidity.owner=owner", "seasonplan.execution-rules.metrics.humidity.response-seconds=60",
                        "seasonplan.execution-rules.metrics.pollen_density.tolerance=2", "seasonplan.execution-rules.metrics.pollen_density.owner=owner", "seasonplan.execution-rules.metrics.pollen_density.response-seconds=60")
                .withBean(PollenTreeClient.class, () -> new FakePollenTreeClient(new BigDecimal("500")))
                .withBean(com.fasterxml.jackson.databind.ObjectMapper.class, () -> new com.fasterxml.jackson.databind.ObjectMapper().findAndRegisterModules())
                .withBean("testPollenIntents", com.seasonplan.pollen.application.port.PollenIntentRepository.class, com.seasonplan.pollen.adapter.out.fake.InMemoryPollenIntentRepository::new, definition -> definition.setPrimary(true))
                .withBean(JdbcTemplate.class, () -> jdbc)
                .withBean(PlatformTransactionManager.class, () -> mock(PlatformTransactionManager.class))
                .run(context -> {
                    assertThat(context).hasNotFailed().hasSingleBean(PollenService.class).hasSingleBean(com.seasonplan.monitoring.application.MonitoringService.class).hasSingleBean(com.seasonplan.ritual.application.RitualService.class).hasSingleBean(com.seasonplan.response.application.IncidentResponseService.class);
                    clearInvocations(jdbc); // Ignore Spring's InitializingBean callback, not an application query.
                    var plans = context.getBean(SeasonPlanService.class);
                    var plan = plans.createDraft(SeasonPlanTestFixture.validPlanDraft("planner"), ActorRole.FAIRY_PLANNER, "planner");
                    var pollen = context.getBean(PollenService.class);
                    var reservation = pollen.requestReservation(plan.id(), 100, ActorRole.POLLEN_GUARDIAN, "guardian");
                    pollen.confirmReservation(reservation.id(), 100, "ok", ActorRole.POLLEN_GUARDIAN, "guardian");
                    assertThat(pollen.get(reservation.id()).status()).isEqualTo(PollenReservationStatus.CONFIRMED);
                    assertThat(pollen.findLatestBySeasonPlanId(plan.id()).orElseThrow().id()).isEqualTo(reservation.id());
                    assertThat(pollen.usage(plan.id()).orElseThrow().actualGrams()).isEqualByComparingTo("0");
                    assertThat(plans.calculateChecklist(plan.id())).doesNotContainKey("pollen-confirmed");
                    verify(jdbc, times(6)).queryForObject("select id from season_plans where id = ? for update", java.util.UUID.class, plan.id());
                    verifyNoMoreInteractions(jdbc);
                    var active = com.seasonplan.season.domain.SeasonPlan.rehydrate(
                            plan.id(), plan.seasonType(), plan.targetTerritories(), plan.timeWindows(), plan.naturalPhenomenonParameters(),
                            plan.targetClimateMetrics(), com.seasonplan.season.domain.SeasonPlanStatus.ACTIVE, null, plan.readinessChecklist(),
                            plan.createdBy(), plan.updatedBy(), plan.createdAt(), plan.updatedAt(), plan.materialRevisionStamp());
                    active.pinExecutionRules(plan.executionRules()); context.getBean(SeasonPlanRepository.class).save(active);
                    assertThatThrownBy(() -> pollen.recordConsumption(plan.id(), java.util.UUID.randomUUID(), new BigDecimal("80"),
                            ActorRole.POLLEN_GUARDIAN, "guardian"))
                            .isInstanceOf(org.springframework.beans.factory.NoSuchBeanDefinitionException.class);
                    assertThat(context.getBean(PollenTreeClient.class).summary(plan.id()).orElseThrow().actualGrams())
                            .isEqualByComparingTo("0");
                });
    }
}
