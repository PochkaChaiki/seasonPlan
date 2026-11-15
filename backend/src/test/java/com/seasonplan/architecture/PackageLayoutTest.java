package com.seasonplan.architecture;

import org.junit.jupiter.api.Test;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import static org.assertj.core.api.Assertions.assertThat;

class PackageLayoutTest {
    private static final Path ROOT = Path.of("src/main/java/com/seasonplan/lifecycle");

    @Test
    void contractsHaveDedicatedLocationsAndOldNamespacesAreGone() throws IOException {
        for (String path : List.of(
                "repository/SeasonPlanRepository.java", "repository/MagicTaskRepository.java", "repository/FairyAssignmentRepository.java",
                "repository/AuditLogRepository.java", "repository/NotificationInboxRepository.java", "repository/RitualSessionRepository.java",
                "repository/ClimateMetricRepository.java", "repository/MagicIncidentRepository.java", "repository/CorrectionActionRepository.java",
                "repository/SeasonReportRepository.java", "service/RitualReadinessChecker.java", "service/MetricRuleProvider.java",
                "service/IncidentRuleProvider.java", "service/RequiredTaskResults.java", "service/CatalogVersionProvider.java",
                "service/SeasonReportExporter.java", "service/CommandTransaction.java",
                "service/ValidationSupport.java", "integration/pollen/PollenTreeClient.java", "integration/pollen/PollenTreeUnavailableException.java",
                "integration/calendar/NaturalCalendarClient.java", "notification/NotificationOutbox.java", "notification/NotificationDeliveryGateway.java",
                "notification/NotificationRecipientResolver.java", "exception/NotFoundException.java",
                "infrastructure/fake/AllowingNaturalCalendarClient.java", "infrastructure/http/RestPollenTreeClient.java",
                "infrastructure/jdbc/JdbcSeasonPlanRepository.java", "infrastructure/mail/SmtpNotificationEmailGateway.java")) {
            assertThat(ROOT.resolve(path)).as(path).isRegularFile();
        }
        for (String path : List.of("application", "adapter", "persistence")) assertThat(ROOT.resolve(path)).doesNotExist();
        try (var files = Files.walk(ROOT)) {
            for (Path file : files.filter(p -> p.toString().endsWith(".java")).toList()) {
                assertThat(Files.readString(file)).as(file.toString()).doesNotContain(
                        "com.seasonplan.lifecycle.application", "com.seasonplan.lifecycle.adapter", "com.seasonplan.lifecycle.persistence",
                        "ExecutionPorts", "CompletionPorts");
            }
        }
    }

    @Test
    void splitContractTypesAndValidationSupportHaveTheIntendedOwnership() throws Exception {
        assertThat(com.seasonplan.lifecycle.service.RitualReadinessChecker.Readiness.class.getEnclosingClass())
                .isEqualTo(com.seasonplan.lifecycle.service.RitualReadinessChecker.class);
        assertThat(com.seasonplan.lifecycle.service.MetricRuleProvider.MetricRule.class.getEnclosingClass())
                .isEqualTo(com.seasonplan.lifecycle.service.MetricRuleProvider.class);
        assertThat(java.lang.reflect.Modifier.isPublic(Class.forName("com.seasonplan.lifecycle.service.ValidationSupport").getModifiers())).isFalse();
        try (var files = Files.walk(ROOT.resolve("repository"))) {
            for (Path file : files.filter(p -> p.toString().endsWith(".java")).toList()) {
                assertThat(Files.readString(file)).as(file.toString())
                        .doesNotContain("import com.seasonplan.lifecycle.service.", "import com.seasonplan.lifecycle.notification.",
                                "import com.seasonplan.lifecycle.integration.");
            }
        }
    }

    @Test
    void contractsAndServicesAreFrameworkFreeAndServicesDoNotDependOnDeliveryImplementations() throws IOException {
        for (String path : List.of("domain", "repository", "service", "integration", "notification", "exception", "authorization")) {
            Path directory = ROOT.resolve(path);
            assertThat(directory).isDirectory();
            try (var files = Files.walk(directory)) {
                for (Path file : files.filter(p -> p.toString().endsWith(".java")).toList()) {
                    assertThat(Files.readString(file)
                            .replace("import com.seasonplan.security.identity.ActorRole;", "")
                            .replace("import static com.seasonplan.security.identity.ActorRole.*;", "")
                            .replace("import com.seasonplan.security.identity.CurrentActor;", "")
                            .replace("import com.seasonplan.security.identity.CurrentActorProvider;", "")
                            .replace("import com.seasonplan.security.exception.AccessDeniedForRoleException;", ""))
                            .as(file.toString()).doesNotContain(
                            "org.springframework", "com.fasterxml.jackson", "jakarta.", "java.sql", "java.net",
                            "com.seasonplan.lifecycle.infrastructure", "com.seasonplan.lifecycle.api",
                            "com.seasonplan.lifecycle.config", "com.seasonplan.security");
                }
            }
        }
        try (var files = Files.walk(ROOT.resolve("api"))) {
            for (Path file : files.filter(p -> p.toString().endsWith(".java")).toList()) {
                assertThat(Files.readString(file)
                        .replace("import com.seasonplan.security.identity.CurrentActor;", "")
                        .replace("import com.seasonplan.security.identity.CurrentActorProvider;", ""))
                        .as(file.toString()).doesNotContain("com.seasonplan.security.");
            }
        }
        assertThat(Files.readString(ROOT.resolve("service/ValidationSupport.java"))).doesNotContain("requireRole", "requireAnyRole");
        assertThat(Files.readString(ROOT.getParent().resolve("security/identity/CurrentActor.java"))).doesNotContain("requiredRole");
    }
}
