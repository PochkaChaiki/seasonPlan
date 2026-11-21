package com.seasonplan.lifecycle.config;

import com.seasonplan.lifecycle.repository.SeasonPlanRepository;
import com.seasonplan.lifecycle.repository.MagicTaskRepository;
import com.seasonplan.lifecycle.repository.FairyAssignmentRepository;
import com.seasonplan.lifecycle.repository.AuditLogRepository;
import com.seasonplan.lifecycle.notification.NotificationOutbox;
import com.seasonplan.lifecycle.integration.pollen.PollenTreeClient;
import com.seasonplan.lifecycle.infrastructure.fake.FakePollenTreeClient;
import com.seasonplan.lifecycle.infrastructure.fake.InMemoryAuditLogRepository;
import com.seasonplan.lifecycle.infrastructure.fake.InMemoryFairyAssignmentRepository;
import com.seasonplan.lifecycle.infrastructure.fake.InMemoryMagicTaskRepository;
import com.seasonplan.lifecycle.infrastructure.fake.InMemorySeasonPlanRepository;

import com.seasonplan.lifecycle.infrastructure.fake.*;
import com.seasonplan.lifecycle.service.PollenService;

import com.seasonplan.security.identity.ActorRole;
import com.seasonplan.lifecycle.domain.PollenReservationStatus;
import com.seasonplan.lifecycle.service.SeasonPlanService;
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
        new ApplicationContextRunner().withUserConfiguration(LifecycleConfiguration.class)
                .withBean(SeasonPlanRepository.class, InMemorySeasonPlanRepository::new)
                .withBean(AuditLogRepository.class, InMemoryAuditLogRepository::new)
                .withBean(MagicTaskRepository.class, InMemoryMagicTaskRepository::new)
                .withBean(FairyAssignmentRepository.class, InMemoryFairyAssignmentRepository::new)
                .withBean(com.seasonplan.lifecycle.repository.MagicIncidentRepository.class, () -> mock(com.seasonplan.lifecycle.repository.MagicIncidentRepository.class))
                .withBean(com.seasonplan.lifecycle.repository.CorrectionActionRepository.class, () -> mock(com.seasonplan.lifecycle.repository.CorrectionActionRepository.class))
                .withBean(com.seasonplan.lifecycle.repository.ClimateMetricRepository.class, () -> mock(com.seasonplan.lifecycle.repository.ClimateMetricRepository.class))
                .withBean(com.seasonplan.lifecycle.repository.RitualSessionRepository.class, () -> mock(com.seasonplan.lifecycle.repository.RitualSessionRepository.class))
                .withBean(com.seasonplan.lifecycle.infrastructure.jdbc.JdbcNotificationOutbox.class, () -> mock(com.seasonplan.lifecycle.infrastructure.jdbc.JdbcNotificationOutbox.class))
                .withPropertyValues("seasonplan.execution-rules.version=test", "seasonplan.execution-rules.required-events=LAUNCH", "seasonplan.incident-rules.version=test", "seasonplan.incident-rules.severity.RITUAL=HIGH", "seasonplan.incident-rules.severity.POLLEN=HIGH", "seasonplan.incident-rules.severity.CLIMATE=HIGH", "seasonplan.incident-rules.severity.ASSIGNMENT=HIGH", "seasonplan.incident-rules.severity.SECURITY=HIGH",
                        "seasonplan.execution-rules.metrics.temperature.tolerance=2", "seasonplan.execution-rules.metrics.temperature.owner=owner", "seasonplan.execution-rules.metrics.temperature.response-seconds=60",
                        "seasonplan.execution-rules.metrics.humidity.tolerance=2", "seasonplan.execution-rules.metrics.humidity.owner=owner", "seasonplan.execution-rules.metrics.humidity.response-seconds=60",
                        "seasonplan.execution-rules.metrics.pollen_density.tolerance=2", "seasonplan.execution-rules.metrics.pollen_density.owner=owner", "seasonplan.execution-rules.metrics.pollen_density.response-seconds=60")
                .withBean(PollenTreeClient.class, () -> new FakePollenTreeClient(new BigDecimal("500")))
                .withBean(JdbcTemplate.class, () -> jdbc)
                .withBean(PlatformTransactionManager.class, () -> mock(PlatformTransactionManager.class))
                .run(context -> {
                    assertThat(context).hasNotFailed().hasSingleBean(PollenService.class).hasSingleBean(com.seasonplan.lifecycle.service.MonitoringService.class).hasSingleBean(com.seasonplan.lifecycle.service.RitualService.class).hasSingleBean(com.seasonplan.lifecycle.service.IncidentResponseService.class);
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
                    verifyNoInteractions(jdbc);
                    context.getBean(SeasonPlanRepository.class).save(com.seasonplan.lifecycle.domain.SeasonPlan.rehydrate(
                            plan.id(), plan.seasonType(), plan.targetTerritories(), plan.timeWindows(), plan.naturalPhenomenonParameters(),
                            plan.targetClimateMetrics(), com.seasonplan.lifecycle.domain.SeasonPlanStatus.ACTIVE, null, plan.readinessChecklist(),
                            plan.createdBy(), plan.updatedBy(), plan.createdAt(), plan.updatedAt()));
                    assertThatThrownBy(() -> pollen.recordConsumption(plan.id(), java.util.UUID.randomUUID(), new BigDecimal("80"),
                            ActorRole.POLLEN_GUARDIAN, "guardian"))
                            .isInstanceOf(org.springframework.beans.factory.NoSuchBeanDefinitionException.class);
                    assertThat(context.getBean(PollenTreeClient.class).summary(plan.id()).orElseThrow().actualGrams())
                            .isEqualByComparingTo("0");
                });
    }
}
