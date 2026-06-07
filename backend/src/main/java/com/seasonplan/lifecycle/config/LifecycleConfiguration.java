package com.seasonplan.lifecycle.config;

import com.seasonplan.lifecycle.service.AllowingNaturalCalendarService;
import com.seasonplan.lifecycle.service.AuditLogRepository;
import com.seasonplan.lifecycle.service.AuditLogService;
import com.seasonplan.lifecycle.service.FairyAssignmentService;
import com.seasonplan.lifecycle.service.FairyAssignmentRepository;
import com.seasonplan.lifecycle.service.MagicTaskService;
import com.seasonplan.lifecycle.service.MagicTaskRepository;
import com.seasonplan.lifecycle.service.NaturalCalendarService;
import com.seasonplan.lifecycle.service.NotificationService;
import com.seasonplan.lifecycle.service.PollenReservationRepository;
import com.seasonplan.lifecycle.service.PollenReservationService;
import com.seasonplan.lifecycle.service.ReadinessChecklistService;
import com.seasonplan.lifecycle.service.SeasonPlanRepository;
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
            FairyAssignmentRepository fairyAssignments
    ) {
        ReadinessChecklistService service = new ReadinessChecklistService();
        service.configure(plans, pollenReservations, magicTasks, fairyAssignments);
        return service;
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
            SeasonPlanService seasonPlanService,
            AuditLogService auditLogService
    ) {
        return new PollenReservationService(reservations, seasonPlanService, auditLogService);
    }

    @Bean
    MagicTaskService magicTaskService(
            MagicTaskRepository tasks,
            SeasonPlanService seasonPlanService,
            AuditLogService auditLogService
    ) {
        return new MagicTaskService(tasks, seasonPlanService, auditLogService);
    }

    @Bean
    FairyAssignmentService fairyAssignmentService(
            FairyAssignmentRepository assignments,
            MagicTaskService magicTaskService,
            AuditLogService auditLogService
    ) {
        return new FairyAssignmentService(assignments, magicTaskService, auditLogService);
    }
}
