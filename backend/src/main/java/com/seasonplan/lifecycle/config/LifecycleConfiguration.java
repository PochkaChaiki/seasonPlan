package com.seasonplan.lifecycle.config;

import com.seasonplan.lifecycle.service.AllowingNaturalCalendarService;
import com.seasonplan.lifecycle.service.AuditLogService;
import com.seasonplan.lifecycle.service.FairyAssignmentService;
import com.seasonplan.lifecycle.service.InMemoryFairyAssignmentRepository;
import com.seasonplan.lifecycle.service.InMemoryMagicTaskRepository;
import com.seasonplan.lifecycle.service.InMemoryPollenReservationRepository;
import com.seasonplan.lifecycle.service.InMemorySeasonPlanRepository;
import com.seasonplan.lifecycle.service.MagicTaskService;
import com.seasonplan.lifecycle.service.NaturalCalendarService;
import com.seasonplan.lifecycle.service.NotificationService;
import com.seasonplan.lifecycle.service.PollenReservationService;
import com.seasonplan.lifecycle.service.ReadinessChecklistService;
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
    InMemorySeasonPlanRepository seasonPlanRepository() {
        return new InMemorySeasonPlanRepository();
    }

    @Bean
    InMemoryPollenReservationRepository pollenReservationRepository() {
        return new InMemoryPollenReservationRepository();
    }

    @Bean
    InMemoryMagicTaskRepository magicTaskRepository() {
        return new InMemoryMagicTaskRepository();
    }

    @Bean
    InMemoryFairyAssignmentRepository fairyAssignmentRepository() {
        return new InMemoryFairyAssignmentRepository();
    }

    @Bean
    AuditLogService auditLogService(Clock clock) {
        return new AuditLogService(clock);
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
            InMemorySeasonPlanRepository plans,
            InMemoryPollenReservationRepository pollenReservations,
            InMemoryMagicTaskRepository magicTasks,
            InMemoryFairyAssignmentRepository fairyAssignments
    ) {
        ReadinessChecklistService service = new ReadinessChecklistService();
        service.configure(plans, pollenReservations, magicTasks, fairyAssignments);
        return service;
    }

    @Bean
    SeasonPlanService seasonPlanService(
            InMemorySeasonPlanRepository plans,
            InMemoryPollenReservationRepository pollenReservations,
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
            InMemoryPollenReservationRepository reservations,
            SeasonPlanService seasonPlanService,
            AuditLogService auditLogService
    ) {
        return new PollenReservationService(reservations, seasonPlanService, auditLogService);
    }

    @Bean
    MagicTaskService magicTaskService(
            InMemoryMagicTaskRepository tasks,
            SeasonPlanService seasonPlanService,
            AuditLogService auditLogService
    ) {
        return new MagicTaskService(tasks, seasonPlanService, auditLogService);
    }

    @Bean
    FairyAssignmentService fairyAssignmentService(
            InMemoryFairyAssignmentRepository assignments,
            MagicTaskService magicTaskService,
            AuditLogService auditLogService
    ) {
        return new FairyAssignmentService(assignments, magicTaskService, auditLogService);
    }
}
