package com.seasonplan.architecture;

import org.junit.jupiter.api.Test;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import static org.assertj.core.api.Assertions.assertThat;

class ArchitectureBoundaryTest {
    private static final Path ROOT = Path.of("src/main/java/com/seasonplan/lifecycle");

    @Test
    void domainSourcesDoNotDependOnFrameworkOrSerializationPackages() throws IOException {
        assertThat(violations(ROOT.resolve("domain"), List.of("com.fasterxml.jackson", "org.springframework", "jakarta.",
                "java.sql", "java.net", "com.seasonplan.lifecycle.infrastructure", "com.seasonplan.lifecycle.api", "com.seasonplan.lifecycle.config",
                "com.seasonplan.lifecycle.repository", "com.seasonplan.lifecycle.integration", "com.seasonplan.lifecycle.notification",
                "com.seasonplan.lifecycle.exception", "com.seasonplan.lifecycle.service", "com.seasonplan.lifecycle.authorization",
                "com.seasonplan.security"))).isEmpty();
    }

    @Test
    void servicesAreCohesiveRatherThanUseCaseFacades() throws IOException {
        Path services = ROOT.resolve("service");
        try (var sources = Files.walk(services)) {
            assertThat(sources.filter(path -> path.toString().endsWith("Service.java"))
                    .filter(path -> source(path).matches("(?s).*\\bpublic\\s+(?:final\\s+)?class\\s+\\w+Service\\b.*"))
                    .map(path -> path.getFileName().toString()).sorted().toList())
                    .containsExactly("AuditLogService.java", "IncidentResponseService.java", "MagicTaskService.java",
                            "MonitoringService.java", "PollenService.java", "RitualService.java",
                            "SeasonPlanService.java", "SeasonReportService.java");
        }
        assertThat(violations(services, List.of("new PlanningService", "new ApprovalService", "new PreparationService",
                "FairyAssignmentService", "ReadinessChecklistService", "Consumer<SeasonPlan>"))).isEmpty();
    }

    @Test
    void lifecycleControllerDoesNotOwnApiModelDefinitions() {
        assertThat(com.seasonplan.lifecycle.api.LifecycleController.class.getDeclaredClasses()).isEmpty();
    }

    @Test
    void controllersDependOnlyOnCoarseGrainedLifecycleServices() {
        assertThat(java.util.Arrays.stream(com.seasonplan.lifecycle.api.LifecycleController.class.getDeclaredFields())
                .map(field -> field.getType().getSimpleName()).toList())
                .containsExactlyInAnyOrder("SeasonPlanService", "PollenService", "MagicTaskService", "CurrentActorProvider");
    }

    @Test
    void temporaryIntegrationsLiveOutsideTheApplicationCore() {
        assertThat(ROOT.resolve("service/AllowingNaturalCalendarService.java")).doesNotExist();
        assertThat(ROOT.resolve("service/NotificationService.java")).doesNotExist();
        assertThat(ROOT.resolve("infrastructure/fake/AllowingNaturalCalendarClient.java")).isRegularFile();
    }

    @Test
    void obsoleteInMemoryNotificationPathIsRemoved() throws IOException {
        assertThat(ROOT.resolve("infrastructure/fake/NotificationService.java")).doesNotExist();
        assertThat(ROOT.resolve("application/port/PlannerNotifications.java")).doesNotExist();
        assertThat(Files.readString(ROOT.resolve("config/LifecycleConfiguration.java")))
                .doesNotContain("NotificationService");
    }

    private List<String> violations(Path root, List<String> forbidden) throws IOException {
        try (var sources = Files.walk(root)) {
            return sources.filter(path -> path.toString().endsWith(".java"))
                    .filter(path -> {
                        String source = source(path).replace("import com.seasonplan.security.identity.ActorRole;", "");
                        return forbidden.stream().anyMatch(source::contains);
                    })
                    .map(root::relativize).map(Path::toString).sorted().toList();
        }
    }

    private String source(Path path) {
        try { return Files.readString(path); }
        catch (IOException error) { throw new IllegalStateException(error); }
    }
}
