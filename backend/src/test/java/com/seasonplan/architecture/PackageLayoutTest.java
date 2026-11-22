package com.seasonplan.architecture;

import org.junit.jupiter.api.Test;
import java.nio.file.*;
import java.util.List;
import static org.assertj.core.api.Assertions.assertThat;

class PackageLayoutTest {
    private static final Path ROOT = Path.of("src/main/java/com/seasonplan");

    @Test void contractsAndAdaptersHaveCapabilityOwnedLocations() {
        for (String path : List.of("season/application/port/SeasonPlanRepository.java", "work/application/port/MagicTaskRepository.java",
                "work/application/port/FairyAssignmentRepository.java", "monitoring/application/port/ClimateMetricRepository.java",
                "response/application/port/MagicIncidentRepository.java", "response/application/port/CorrectionActionRepository.java",
                "ritual/application/port/RitualSessionRepository.java", "reporting/application/port/SeasonReportRepository.java",
                "notification/application/port/NotificationInboxRepository.java", "pollen/application/port/PollenTreeClient.java",
                "reference/application/port/NaturalCalendarClient.java", "audit/application/port/AuditLogRepository.java",
                "season/api/SeasonPlanning.java", "pollen/api/PollenOperations.java", "work/api/WorkPreparation.java",
                "season/adapter/out/persistence/JdbcSeasonPlanRepository.java", "notification/adapter/out/mail/SmtpNotificationEmailGateway.java")) {
            assertThat(ROOT.resolve(path)).as(path).isRegularFile();
        }
    }

    @Test void noProductionTypesRemainInLegacyLayerFirstNamespaces() throws Exception {
        assertThat(ROOT.resolve("lifecycle")).doesNotExist();
        assertThat(ROOT.resolve("security")).doesNotExist();
        try (var files = Files.walk(ROOT)) {
            for (Path path : files.filter(p -> p.toString().endsWith(".java")).toList()) {
                assertThat(Files.readString(path)).as(path.toString()).doesNotContain("com.seasonplan.lifecycle.", "com.seasonplan.security.");
            }
        }
    }

    @Test void domainValidationUtilitiesDoNotOwnAuthorization() throws Exception {
        assertThat(Files.readString(ROOT.resolve("shared/domain/ValidationSupport.java"))).doesNotContain("requireRole", "requireAnyRole");
        assertThat(Files.readString(ROOT.resolve("identity/api/CurrentActor.java"))).doesNotContain("requiredRole");
        assertThat(com.seasonplan.ritual.api.RitualReadinessChecker.Readiness.class.getEnclosingClass()).isEqualTo(com.seasonplan.ritual.api.RitualReadinessChecker.class);
        assertThat(com.seasonplan.monitoring.application.port.MetricRuleProvider.MetricRule.class.getEnclosingClass()).isEqualTo(com.seasonplan.monitoring.application.port.MetricRuleProvider.class);
    }
}
