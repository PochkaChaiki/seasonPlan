package com.seasonplan.architecture;

import org.junit.jupiter.api.Test;
import java.nio.file.*;
import java.util.*;
import static org.assertj.core.api.Assertions.assertThat;

class ArchitectureBoundaryTest {
    private static final Path ROOT = Path.of("src/main/java/com/seasonplan");

    @Test void domainSourcesDoNotDependOnFrameworksOrApplicationImplementations() throws Exception {
        try (var files = Files.walk(ROOT)) {
            for (Path file : files.filter(p -> p.toString().endsWith(".java") && normalized(p).contains("/domain/")).toList()) {
                assertThat(Files.readString(file)).as(file.toString()).doesNotContain("com.fasterxml.jackson", "org.springframework", "jakarta.",
                        "java.sql", "java.net", ".application.", ".adapter.", "com.seasonplan.bootstrap");
            }
        }
    }

    @Test void applicationsAreFrameworkFreeAndDoNotDependOnConcreteAdapters() throws Exception {
        try (var files = Files.walk(ROOT)) {
            for (Path file : files.filter(p -> p.toString().endsWith(".java") && normalized(p).contains("/application/")).toList()) {
                assertThat(Files.readString(file)).as(file.toString()).doesNotContain("org.springframework", "com.fasterxml.jackson", "jakarta.", "java.sql", "java.net", ".adapter.", "com.seasonplan.bootstrap");
            }
        }
    }

    @Test void lifecycleControllerDoesNotOwnApiModelDefinitions() {
        assertThat(com.seasonplan.workflows.adapter.in.web.LifecycleController.class.getDeclaredClasses()).isEmpty();
    }

    @Test void controllersDependOnPublicCapabilityContracts() {
        assertThat(Arrays.stream(com.seasonplan.workflows.adapter.in.web.LifecycleController.class.getDeclaredFields())
                .map(field -> field.getType().getPackageName()).toList())
                .containsExactlyInAnyOrder("com.seasonplan.season.api", "com.seasonplan.pollen.api", "com.seasonplan.work.api", "com.seasonplan.identity.api");
    }

    @Test void temporaryIntegrationsRemainOutsideTheCore() {
        assertThat(ROOT.resolve("reference/adapter/out/fake/AllowingNaturalCalendarClient.java")).isRegularFile();
        assertThat(ROOT.resolve("pollen/adapter/out/fake/FakePollenTreeClient.java")).isRegularFile();
        assertThat(ROOT.resolve("reference/application/AllowingNaturalCalendarClient.java")).doesNotExist();
        assertThat(ROOT.resolve("notification/application/NotificationService.java")).doesNotExist();
    }

    private static String normalized(Path path) { return path.toString().replace('\\', '/'); }
}
