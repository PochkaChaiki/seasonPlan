package com.seasonplan.lifecycle.config;

import com.seasonplan.lifecycle.adapter.fake.AllowingNaturalCalendarService;
import com.seasonplan.lifecycle.application.port.AuditLogRepository;
import com.seasonplan.lifecycle.application.shared.AuditLogService;
import com.seasonplan.lifecycle.application.preparation.FairyAssignmentService;
import com.seasonplan.lifecycle.application.port.FairyAssignmentRepository;
import com.seasonplan.lifecycle.application.preparation.FairySchedulePolicy;
import com.seasonplan.lifecycle.application.preparation.MagicTaskService;
import com.seasonplan.lifecycle.application.port.MagicTaskRepository;
import com.seasonplan.lifecycle.application.port.NaturalCalendarService;
import com.seasonplan.lifecycle.adapter.fake.NotificationService;
import com.seasonplan.lifecycle.application.port.PollenReservationRepository;
import com.seasonplan.lifecycle.application.preparation.PollenReservationService;
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
            PollenReservationRepository pollenReservations,
            MagicTaskRepository magicTasks,
            FairyAssignmentRepository fairyAssignments,
            Clock clock
    ) {
        return new ReadinessChecklistService(plans, pollenReservations, magicTasks, fairyAssignments, clock);
    }

    @Bean
    SeasonPlanService seasonPlanService(
            SeasonPlanRepository plans,
            PollenReservationRepository pollenReservations,
            AuditLogService auditLogService,
            NotificationService notificationService,
            NaturalCalendarService naturalCalendarService,
            ReadinessChecklistService readinessChecklistService,
            Clock clock
    ) {
        return new SeasonPlanService(plans, pollenReservations, auditLogService, notificationService,
                naturalCalendarService, readinessChecklistService, clock);
    }

    @Bean
    PollenReservationService pollenReservationService(
            PollenReservationRepository reservations,
            SeasonPlanRepository plans,
            AuditLogService auditLogService
    ) {
        return new PollenReservationService(reservations, plans, auditLogService);
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
