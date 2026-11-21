package com.seasonplan.lifecycle.config;

import com.seasonplan.lifecycle.infrastructure.fake.AllowingNaturalCalendarClient;
import com.seasonplan.lifecycle.repository.AuditLogRepository;
import com.seasonplan.lifecycle.service.CommandTransaction;
import com.seasonplan.lifecycle.infrastructure.jdbc.JdbcCommandTransaction;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import com.seasonplan.lifecycle.service.AuditLogService;
import com.seasonplan.lifecycle.repository.FairyAssignmentRepository;
import com.seasonplan.lifecycle.service.MagicTaskService;
import com.seasonplan.lifecycle.repository.MagicTaskRepository;
import com.seasonplan.lifecycle.integration.calendar.NaturalCalendarClient;
import com.seasonplan.lifecycle.integration.pollen.PollenTreeClient;
import com.seasonplan.lifecycle.notification.NotificationOutbox;
import com.seasonplan.lifecycle.service.PollenService;
import org.springframework.beans.factory.ObjectProvider;
import com.seasonplan.lifecycle.repository.SeasonPlanRepository;
import com.seasonplan.lifecycle.service.SeasonPlanService;
import com.seasonplan.lifecycle.service.PinnedMetricRuleProvider;
import com.seasonplan.lifecycle.service.ComputedRitualReadinessChecker;
import com.seasonplan.lifecycle.service.RitualReadinessChecker;
import com.seasonplan.lifecycle.service.RitualService;
import com.seasonplan.lifecycle.service.MonitoringService;
import com.seasonplan.lifecycle.service.IncidentResponseService;
import com.seasonplan.lifecycle.repository.MagicIncidentRepository;
import com.seasonplan.lifecycle.repository.CorrectionActionRepository;
import com.seasonplan.lifecycle.repository.ClimateMetricRepository;
import com.seasonplan.lifecycle.repository.RitualSessionRepository;
import com.seasonplan.lifecycle.infrastructure.jdbc.JdbcNotificationOutbox;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

@Configuration
public class LifecycleConfiguration {
    @Bean
    ConfiguredExecutionRules executionRules(org.springframework.core.env.Environment environment) {
        return new ConfiguredExecutionRules(environment);
    }

    @Bean
    PinnedMetricRuleProvider metricRuleProvider(SeasonPlanRepository plans) {
        return new PinnedMetricRuleProvider(plans);
    }

    @Bean
    ConfiguredIncidentRuleProvider incidentRuleProvider(org.springframework.core.env.Environment environment) {
        return new ConfiguredIncidentRuleProvider(environment);
    }

    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }

    @Bean
    AuditLogService auditLogService(AuditLogRepository auditLogRepository, Clock clock) {
        return new AuditLogService(auditLogRepository, clock);
    }

    @Bean
    NaturalCalendarClient naturalCalendarService() {
        return new AllowingNaturalCalendarClient();
    }

    @Bean
    SeasonPlanService seasonPlanService(
            SeasonPlanRepository plans,
            PollenService pollen,
            AuditLogService auditLogService,
            CommandTransaction transactions,
            ObjectProvider<NotificationOutbox> outbox,
            NaturalCalendarClient naturalCalendarService,
            MagicTaskRepository magicTasks,
            FairyAssignmentRepository fairyAssignments,
            ConfiguredExecutionRules executionRules,
            RitualReadinessChecker readiness,
            Clock clock
    ) {
        return new SeasonPlanService(plans, pollen, magicTasks, fairyAssignments, auditLogService, transactions,
                event -> outbox.getObject().enqueue(event), naturalCalendarService, clock, executionRules::snapshot, readiness);
    }

    @Bean
    RitualReadinessChecker ritualReadinessChecker(SeasonPlanRepository plans, MagicTaskRepository tasks,
            FairyAssignmentRepository assignments, PollenService pollen, MagicIncidentRepository incidents) {
        return new ComputedRitualReadinessChecker(plans, tasks, assignments, pollen, incidents);
    }

    @Bean
    RitualService ritualService(SeasonPlanRepository plans, RitualSessionRepository sessions, MagicIncidentRepository incidents,
            RitualReadinessChecker readiness, CommandTransaction transactions, AuditLogService audit, Clock clock) {
        return new RitualService(plans, sessions, incidents, readiness, transactions, audit, clock);
    }

    @Bean
    MonitoringService monitoringService(SeasonPlanRepository plans, ClimateMetricRepository metrics, MagicIncidentRepository incidents,
            CorrectionActionRepository actions, PinnedMetricRuleProvider rules, JdbcNotificationOutbox outbox,
            CommandTransaction transactions, AuditLogService audit, Clock clock) {
        return new MonitoringService(plans, metrics, incidents, actions, rules, outbox, transactions, audit, clock);
    }

    @Bean
    IncidentResponseService incidentResponseService(SeasonPlanRepository plans, MagicIncidentRepository incidents,
            CorrectionActionRepository actions, ConfiguredIncidentRuleProvider rules, JdbcNotificationOutbox outbox,
            CommandTransaction transactions, AuditLogService audit, Clock clock) {
        return new IncidentResponseService(plans, incidents, actions, rules, transactions, outbox, audit, clock);
    }

    @Bean
    CommandTransaction commandTransaction(JdbcTemplate jdbc, PlatformTransactionManager manager) {
        return new JdbcCommandTransaction(jdbc, manager);
    }

    @Bean
    PollenService pollenService(
            SeasonPlanRepository plans,
            PollenTreeClient client,
            @org.springframework.beans.factory.annotation.Qualifier("pollenConsumptionOutbox") ObjectProvider<NotificationOutbox> outbox,
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
            FairyAssignmentRepository assignments,
            SeasonPlanRepository plans,
            AuditLogService auditLogService,
            CommandTransaction transactions,
            Clock clock
    ) {
        return new MagicTaskService(tasks, assignments, plans, auditLogService, transactions, clock);
    }
}
