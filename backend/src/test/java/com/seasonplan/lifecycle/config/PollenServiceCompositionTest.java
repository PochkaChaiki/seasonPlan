package com.seasonplan.lifecycle.config;

import com.seasonplan.lifecycle.adapter.fake.*;
import com.seasonplan.lifecycle.application.pollen.PollenService;
import com.seasonplan.lifecycle.application.port.*;
import com.seasonplan.lifecycle.domain.ActorRole;
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
                .withBean(PollenTreeClient.class, () -> new FakePollenTreeClient(new BigDecimal("500")))
                .withBean(JdbcTemplate.class, () -> jdbc)
                .withBean(PlatformTransactionManager.class, () -> mock(PlatformTransactionManager.class))
                .run(context -> {
                    assertThat(context).hasNotFailed().hasSingleBean(PollenService.class).doesNotHaveBean(NotificationOutbox.class);
                    clearInvocations(jdbc); // Ignore Spring's InitializingBean callback, not an application query.
                    var plans = context.getBean(SeasonPlanService.class);
                    var plan = plans.createDraft(SeasonPlanTestFixture.validPlanDraft("planner"), ActorRole.FAIRY_PLANNER, "planner");
                    var pollen = context.getBean(PollenService.class);
                    var reservation = pollen.requestReservation(plan.id(), 100, ActorRole.POLLEN_GUARDIAN, "guardian");
                    pollen.confirmReservation(reservation.id(), 100, "ok", ActorRole.POLLEN_GUARDIAN, "guardian");
                    assertThat(pollen.get(reservation.id()).status()).isEqualTo(PollenReservationStatus.CONFIRMED);
                    assertThat(pollen.findLatestBySeasonPlanId(plan.id()).orElseThrow().id()).isEqualTo(reservation.id());
                    assertThat(pollen.usage(plan.id()).orElseThrow().actualGrams()).isEqualByComparingTo("0");
                    assertThat(context.getBean(com.seasonplan.lifecycle.application.preparation.ReadinessChecklistService.class)
                            .calculateChecklist(plan.id())).doesNotContainKey("pollenReservation");
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
