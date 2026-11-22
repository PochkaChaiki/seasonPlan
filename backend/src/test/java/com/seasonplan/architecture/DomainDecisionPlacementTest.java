package com.seasonplan.architecture;

import org.junit.jupiter.api.Test;
import java.nio.file.*;
import java.util.List;
import static org.assertj.core.api.Assertions.assertThat;

class DomainDecisionPlacementTest {
    @Test void initialReadinessEligibilityAndRecipientsAreOwnedByDomain() throws Exception {
        Path root = Path.of("src/main/java/com/seasonplan");
        assertThat(Files.readString(root.resolve("season/application/SeasonPlanService.java"))).doesNotContain("new ChecklistItem(", "Set.of(plan.createdBy())");
        assertThat(Files.readString(root.resolve("response/application/IncidentResponseService.java"))).doesNotContain("Set.of(owner)");
        for (String path : List.of("monitoring/application/MonitoringService.java", "pollen/application/PollenService.java", "reporting/application/SeasonReportService.java", "ritual/application/RitualService.java")) {
            assertThat(Files.readString(root.resolve(path))).as(path).doesNotContain("ServiceSupport.plan(plans, planId, SeasonPlanStatus", "ServiceSupport.plan(plans, session.seasonPlanId(), SeasonPlanStatus");
        }
    }

    @Test void catalogsCapacityAndReactionRulesAreNotAdapterOrUseCasePolicies() throws Exception {
        Path root = Path.of("src/main/java/com/seasonplan");
        for (String path : List.of("reference/domain/SeasonalRequirementsCatalog.java", "pollen/domain/PollenCapacityPolicy.java", "monitoring/domain/MetricReactionPolicy.java")) {
            assertThat(root.resolve(path)).isRegularFile();
        }
        assertThat(Files.readString(root.resolve("reference/adapter/out/configuration/ConfiguredExecutionRules.java")))
                .doesNotContain("WEATHER_WEAVER", "FROST_WARDEN", "HARVEST_WARDEN");
        assertThat(Files.readString(root.resolve("monitoring/application/MonitoringService.java")))
                .doesNotContain("MagicIncident.Type.CLIMATE", "MagicIncident.Severity.CRITICAL", "clock.instant().plus(rule.responseTime())");
        assertThat(Files.readString(root.resolve("response/application/IncidentResponseService.java")))
                .doesNotContain("\"CHIEF_GUARDIAN\"", "\"MAGIC_ANOMALY_CONTROLLER\"");
    }

    @Test void stateAndImportIdentityRulesAreNotApplicationHelpers() throws Exception {
        Path root = Path.of("src/main/java/com/seasonplan");
        assertThat(Files.readString(root.resolve("season/application/ServiceSupport.java")))
                .doesNotContain("Arrays.stream(allowed)", "current.status() == SeasonPlanStatus.EMERGENCY");
        assertThat(Files.readString(root.resolve("monitoring/application/MonitoringService.java")))
                .doesNotContain("unit does not match metric catalog", "import key reused for different measurement");
    }

    @Test void executionAndAccountDecisionsAlsoBelongToTheirDomains() throws Exception {
        Path root = Path.of("src/main/java/com/seasonplan");
        for (String path : List.of("ritual/domain/RitualReadinessPolicy.java", "pollen/domain/PollenConsumptionPolicy.java",
                "response/domain/IncidentResponsePolicy.java", "reporting/domain/ReportPolicy.java", "identity/domain/AccountAdministrationPolicy.java")) {
            assertThat(root.resolve(path)).as(path).isRegularFile();
            assertThat(Files.readString(root.resolve(path))).doesNotContain("Repository", "org.springframework", ".application.", ".adapter.");
        }
        assertThat(Files.readString(root.resolve("workflows/application/ComputedRitualReadinessChecker.java")))
                .contains("evaluation.evaluate(").doesNotContain("confirmedRoles", "knownCategories", "new TreeSet");
        assertThat(Files.readString(root.resolve("pollen/application/PollenService.java")))
                .contains("PollenConsumptionPolicy.").doesNotContain("new BigDecimal(\"0.8\")", ".setScale(");
        assertThat(Files.readString(root.resolve("reporting/application/SeasonReportService.java")))
                .contains("ReportPolicy.").doesNotContain("year - 3", "current.equals(draft.snapshot())");
    }

    @Test void planningAndSchedulingDecisionsBelongToDomainPolicies() throws Exception {
        Path root = Path.of("src/main/java/com/seasonplan");
        for (String path : List.of("season/domain/SeasonPlanPolicy.java", "work/domain/TaskPreparationPolicy.java")) {
            assertThat(root.resolve(path)).as(path).isRegularFile();
            assertThat(Files.readString(root.resolve(path))).doesNotContain("Repository", "org.springframework", "com.fasterxml.jackson", ".application.", ".adapter.");
        }
        assertThat(Files.readString(root.resolve("season/application/SeasonPlanService.java")))
                .contains("SeasonPlanPolicy.").doesNotContain("for (TimeWindow", "toLowerCase(java.util.Locale.ROOT)", "Double.isFinite");
        assertThat(Files.readString(root.resolve("work/application/MagicTaskService.java")))
                .contains("TaskPreparationPolicy.").doesNotContain(".overlaps(", "task.title().isBlank()", "task.category().isBlank()");
    }
}
