package com.seasonplan.bootstrap;

import com.seasonplan.reference.adapter.out.configuration.ConfiguredExecutionRules;
import com.seasonplan.response.adapter.out.configuration.ConfiguredIncidentRuleProvider;

import com.seasonplan.reference.adapter.out.fake.AllowingNaturalCalendarClient;
import com.seasonplan.season.api.CommandTransaction;
import com.seasonplan.season.adapter.out.persistence.JdbcCommandTransaction;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import com.seasonplan.audit.api.AuditTrail;
import com.seasonplan.work.application.port.FairyAssignmentRepository;
import com.seasonplan.work.application.MagicTaskService;
import com.seasonplan.work.application.port.MagicTaskRepository;
import com.seasonplan.reference.application.port.NaturalCalendarClient;
import com.seasonplan.reference.application.CalendarValidationService;
import com.seasonplan.reference.api.NaturalCalendarValidation;
import com.seasonplan.pollen.application.port.PollenTreeClient;
import com.seasonplan.notification.api.NotificationOutbox;
import com.seasonplan.pollen.application.PollenService;
import org.springframework.beans.factory.ObjectProvider;
import com.seasonplan.season.application.port.SeasonPlanRepository;
import com.seasonplan.season.api.SeasonQueries;
import com.seasonplan.season.application.SeasonQueryService;
import com.seasonplan.season.application.SeasonPlanService;
import com.seasonplan.monitoring.application.PinnedMetricRuleProvider;
import com.seasonplan.workflows.application.ComputedRitualReadinessChecker;
import com.seasonplan.ritual.api.RitualReadinessChecker;
import com.seasonplan.ritual.application.RitualService;
import com.seasonplan.monitoring.application.MonitoringService;
import com.seasonplan.response.application.IncidentResponseService;
import com.seasonplan.response.application.port.MagicIncidentRepository;
import com.seasonplan.response.application.port.CorrectionActionRepository;
import com.seasonplan.monitoring.application.port.ClimateMetricRepository;
import com.seasonplan.ritual.application.port.RitualSessionRepository;
import com.seasonplan.notification.adapter.out.persistence.JdbcNotificationOutbox;
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
    DeploymentExecutionRules deploymentExecutionRules(ConfiguredExecutionRules rules, ConfiguredIncidentRuleProvider incidents) {
        return new DeploymentExecutionRules(rules, incidents);
    }

    @Bean
    NaturalCalendarValidation naturalCalendarValidation(NaturalCalendarClient client) {
        return new CalendarValidationService(client);
    }

    @Bean
    SeasonQueries seasonQueries(SeasonPlanRepository plans) { return new SeasonQueryService(plans); }

    @Bean
    PinnedMetricRuleProvider metricRuleProvider(SeasonQueries seasons) {
        return new PinnedMetricRuleProvider(seasons);
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
    NaturalCalendarClient naturalCalendarService(org.springframework.core.env.Environment environment) {
        if (!environment.acceptsProfiles(org.springframework.core.env.Profiles.of("prod"))
                && environment.acceptsProfiles(org.springframework.core.env.Profiles.of("dev", "test"))) {
            return new AllowingNaturalCalendarClient();
        }
        return plan -> java.util.List.of("natural calendar validation is unavailable");
    }

    @Bean
    com.seasonplan.season.api.SeasonPlanning seasonPlanning(SeasonPlanService owner, SeasonQueries queries) {
        return new com.seasonplan.season.application.SeasonPlanningFacade(owner, queries);
    }
    @Bean
    com.seasonplan.work.api.WorkPreparation workPreparation(MagicTaskService owner, com.seasonplan.work.api.WorkQueries queries) {
        return new com.seasonplan.work.application.WorkPreparationFacade(owner, queries);
    }
    @Bean
    com.seasonplan.pollen.api.PollenOperations pollenOperations(PollenService owner) {
        return new com.seasonplan.pollen.application.PollenOperationsFacade(owner);
    }

    @Bean
    SeasonPlanService seasonPlanService(
            SeasonPlanRepository plans,
            com.seasonplan.season.api.ResourceEvidenceSource resources,
            AuditTrail auditLogService,
            CommandTransaction transactions,
            ObjectProvider<NotificationOutbox> outbox,
            NaturalCalendarValidation naturalCalendarService,
            DeploymentExecutionRules executionRules,
            com.seasonplan.season.api.SeasonReadiness readiness,
            Clock clock
    ) {
        return new SeasonPlanService(plans, resources, auditLogService, transactions,
                event -> outbox.getObject().enqueue(event), naturalCalendarService, clock, executionRules::snapshot, readiness);
    }

    @Bean
    com.seasonplan.work.api.WorkQueries workQueries(MagicTaskRepository tasks, FairyAssignmentRepository assignments) {
        return new com.seasonplan.work.application.WorkQueryService(tasks, assignments);
    }
    @Bean
    com.seasonplan.response.api.ResponseQueries responseQueries(MagicIncidentRepository incidents, CorrectionActionRepository actions) {
        return new com.seasonplan.response.application.ResponseQueryService(incidents, actions);
    }
    @Bean
    com.seasonplan.ritual.api.RitualQueries ritualQueries(RitualSessionRepository rituals) {
        return new com.seasonplan.ritual.application.RitualQueryService(rituals);
    }
    @Bean
    com.seasonplan.monitoring.api.MetricQueries metricQueries(ClimateMetricRepository metrics) {
        return new com.seasonplan.monitoring.application.MetricQueryService(metrics);
    }

    @Bean
    com.seasonplan.season.api.ResourceEvidenceSource resourceEvidenceSource(com.seasonplan.pollen.api.PollenOperations pollen) {
        return new com.seasonplan.workflows.application.PollenResourceEvidenceSource(pollen);
    }

    @Bean
    com.seasonplan.ritual.api.ReadinessEvaluation readinessEvaluation() {
        return new com.seasonplan.ritual.domain.RitualReadinessPolicy();
    }

    @Bean
    ComputedRitualReadinessChecker ritualReadinessChecker(SeasonQueries seasons, com.seasonplan.work.api.WorkQueries work,
            com.seasonplan.pollen.api.PollenOperations pollen, com.seasonplan.response.api.ResponseQueries response,
            com.seasonplan.ritual.api.ReadinessEvaluation evaluation) {
        return new ComputedRitualReadinessChecker(seasons, work, pollen, response, evaluation);
    }

    @Bean
    com.seasonplan.season.api.SeasonLifecycle seasonLifecycle(SeasonPlanRepository plans, CommandTransaction transactions, AuditTrail audit, Clock clock) {
        return new com.seasonplan.season.application.SeasonLifecycleService(plans, transactions, audit, clock);
    }

    @Bean
    RitualService ritualService(SeasonQueries seasons, com.seasonplan.season.api.SeasonLifecycle lifecycle, RitualSessionRepository sessions, com.seasonplan.response.api.ResponseQueries response,
            RitualReadinessChecker readiness, CommandTransaction transactions, AuditTrail audit, Clock clock) {
        return new RitualService(seasons, lifecycle, sessions, response, readiness, transactions, audit, clock);
    }

    @Bean
    com.seasonplan.response.api.MetricReactions metricReactions(MagicIncidentRepository incidents,
            CorrectionActionRepository actions, com.seasonplan.season.api.SeasonLifecycle lifecycle,
            CommandTransaction transactions, JdbcNotificationOutbox outbox, AuditTrail audit, Clock clock) {
        return new com.seasonplan.response.application.MetricReactionService(incidents, actions, lifecycle, transactions, outbox, audit, clock);
    }

    @Bean
    MonitoringService monitoringService(SeasonQueries seasons, ClimateMetricRepository metrics,
            PinnedMetricRuleProvider rules, com.seasonplan.response.api.MetricReactions reactions,
            CommandTransaction transactions, AuditTrail audit, Clock clock) {
        return new MonitoringService(seasons, metrics, rules, reactions, transactions, audit, clock);
    }

    @Bean
    IncidentResponseService incidentResponseService(SeasonQueries seasons, com.seasonplan.season.api.SeasonLifecycle lifecycle, MagicIncidentRepository incidents,
            CorrectionActionRepository actions, ConfiguredIncidentRuleProvider rules, JdbcNotificationOutbox outbox,
            CommandTransaction transactions, AuditTrail audit, Clock clock) {
        return new IncidentResponseService(seasons, lifecycle, incidents, actions, rules, transactions, outbox, audit, clock);
    }

    @Bean
    CommandTransaction commandTransaction(JdbcTemplate jdbc, PlatformTransactionManager manager) {
        return new JdbcCommandTransaction(jdbc, manager);
    }

    @Bean
    com.seasonplan.pollen.application.port.PollenIntentRepository pollenIntents(JdbcTemplate jdbc, com.fasterxml.jackson.databind.ObjectMapper mapper) {
        return new com.seasonplan.pollen.adapter.out.persistence.JdbcPollenIntentRepository(jdbc, mapper);
    }

    @Bean
    com.seasonplan.pollen.application.port.RemoteCommandBoundary pollenCommandBoundary() {
        return new com.seasonplan.pollen.adapter.out.persistence.SpringRemoteCommandBoundary();
    }

    @Bean
    PollenService pollenService(
            SeasonQueries seasons,
            PollenTreeClient client,
            @org.springframework.beans.factory.annotation.Qualifier("pollenConsumptionOutbox") ObjectProvider<NotificationOutbox> outbox,
            CommandTransaction transactions,
            AuditTrail auditLogService,
            Clock clock,
            com.seasonplan.pollen.application.port.PollenIntentRepository intents,
            com.seasonplan.pollen.application.port.RemoteCommandBoundary boundary
    ) {
        // Reservations do not depend on UC-4 delivery. Consumption resolves the outbox before spending.
        return new PollenService(seasons, client, outbox::getObject, transactions, auditLogService, clock, intents, boundary);
    }

    @Bean
    MagicTaskService magicTaskService(
            MagicTaskRepository tasks,
            FairyAssignmentRepository assignments,
            SeasonQueries seasons,
            AuditTrail auditLogService,
            CommandTransaction transactions,
            Clock clock
    ) {
        return new MagicTaskService(tasks, assignments, seasons, auditLogService, transactions, clock);
    }
}
