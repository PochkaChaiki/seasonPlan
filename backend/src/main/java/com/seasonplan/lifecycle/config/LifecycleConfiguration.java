package com.seasonplan.lifecycle.config;

import com.seasonplan.lifecycle.adapter.fake.AllowingNaturalCalendarService;
import com.seasonplan.lifecycle.application.port.AuditLogRepository;
import com.seasonplan.lifecycle.application.port.CommandTransaction;
import com.seasonplan.lifecycle.persistence.JdbcCommandTransaction;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import com.seasonplan.lifecycle.application.shared.AuditLogService;
import com.seasonplan.lifecycle.application.preparation.FairyAssignmentService;
import com.seasonplan.lifecycle.application.port.FairyAssignmentRepository;
import com.seasonplan.lifecycle.application.preparation.FairySchedulePolicy;
import com.seasonplan.lifecycle.application.preparation.MagicTaskService;
import com.seasonplan.lifecycle.application.port.MagicTaskRepository;
import com.seasonplan.lifecycle.application.port.NaturalCalendarService;
import com.seasonplan.lifecycle.adapter.fake.NotificationService;
import com.seasonplan.lifecycle.application.port.PollenTreeClient;
import com.seasonplan.lifecycle.application.port.NotificationOutbox;
import com.seasonplan.lifecycle.application.pollen.PollenService;
import org.springframework.beans.factory.ObjectProvider;
import com.seasonplan.lifecycle.application.preparation.ReadinessChecklistService;
import com.seasonplan.lifecycle.application.port.SeasonPlanRepository;
import com.seasonplan.lifecycle.service.SeasonPlanService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

@Configuration
public class LifecycleConfiguration {
    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }

    @Bean
    AuditLogService auditLogService(AuditLogRepository auditLogRepository, Clock clock) {
        return new AuditLogService(auditLogRepository, clock);
    }

    @Bean
    NotificationService notificationService() {
        return new NotificationService();
    }

    @Bean
    NaturalCalendarService naturalCalendarService() {
        return new AllowingNaturalCalendarService();
    }

    @Bean
    ReadinessChecklistService readinessChecklistService(
            SeasonPlanRepository plans,
            PollenService pollen,
            MagicTaskRepository magicTasks,
            FairyAssignmentRepository fairyAssignments,
            Clock clock
    ) {
        return new ReadinessChecklistService(plans, pollen, magicTasks, fairyAssignments, clock);
    }

    @Bean
    SeasonPlanService seasonPlanService(
            SeasonPlanRepository plans,
            PollenService pollen,
            AuditLogService auditLogService,
            NotificationService notificationService,
            NaturalCalendarService naturalCalendarService,
            ReadinessChecklistService readinessChecklistService,
            Clock clock
    ) {
        return new SeasonPlanService(plans, pollen, auditLogService, notificationService,
                naturalCalendarService, readinessChecklistService, clock);
    }

    @Bean
    CommandTransaction commandTransaction(JdbcTemplate jdbc, PlatformTransactionManager manager) {
        return new JdbcCommandTransaction(jdbc, manager);
    }

    @Bean
    PollenService pollenService(
            SeasonPlanRepository plans,
            PollenTreeClient client,
            ObjectProvider<NotificationOutbox> outbox,
            CommandTransaction transactions,
            AuditLogService auditLogService,
            Clock clock
    ) {
        // Reservations do not depend on UC-4 delivery. Consumption resolves the outbox before spending.
        return new PollenService(plans, client, outbox::getObject, transactions, auditLogService, clock);
    }

    @Bean
    MagicTaskService magicTaskService(
            MagicTaskRepository tasks,
            SeasonPlanRepository plans,
            AuditLogService auditLogService
    ) {
        return new MagicTaskService(tasks, plans, auditLogService);
    }

    @Bean
    FairySchedulePolicy fairySchedulePolicy(
            FairyAssignmentRepository assignments,
            MagicTaskRepository magicTasks
    ) {
        return new FairySchedulePolicy(assignments, magicTasks);
    }

    @Bean
    FairyAssignmentService fairyAssignmentService(
            FairyAssignmentRepository assignments,
            MagicTaskRepository magicTasks,
            FairySchedulePolicy fairySchedulePolicy,
            AuditLogService auditLogService
    ) {
        return new FairyAssignmentService(assignments, magicTasks, fairySchedulePolicy, auditLogService);
    }
}
