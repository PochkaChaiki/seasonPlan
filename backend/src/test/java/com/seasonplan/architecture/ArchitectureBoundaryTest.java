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
                "java.sql", "java.net", "com.seasonplan.lifecycle.application", "com.seasonplan.lifecycle.persistence",
                "com.seasonplan.lifecycle.adapter", "com.seasonplan.lifecycle.api", "com.seasonplan.lifecycle.config",
                "com.seasonplan.lifecycle.service", "com.seasonplan.security"))).isEmpty();
    }

    @Test
    void applicationServicesDoNotDependOnPeerApplicationServicesForUseCaseData() throws IOException {
        assertThat(violations(ROOT.resolve("application"), List.of("private final SeasonPlanService", "private final MagicTaskService"))).isEmpty();
    }

    @Test
    void lifecycleControllerDoesNotOwnApiModelDefinitions() {
        assertThat(com.seasonplan.lifecycle.api.LifecycleController.class.getDeclaredClasses()).isEmpty();
    }

    @Test
    void useCasesHaveExplicitFrameworkIndependentApplicationPackages() throws IOException {
        Path root = ROOT.resolve("application");
        for (String useCase : List.of("planning", "approval", "preparation", "execution", "completion", "port", "shared")) {
            assertThat(root.resolve(useCase)).isDirectory();
            try (var sources = Files.walk(root.resolve(useCase))) {
                assertThat(sources.anyMatch(path -> path.toString().endsWith(".java"))).isTrue();
            }
        }
        assertThat(violations(root, List.of("org.springframework", "com.fasterxml.jackson", "jakarta.", "java.sql", "java.net",
                "com.seasonplan.lifecycle.adapter", "com.seasonplan.lifecycle.persistence", "com.seasonplan.lifecycle.api",
                "com.seasonplan.lifecycle.config", "com.seasonplan.lifecycle.service", "com.seasonplan.security"))).isEmpty();
    }

    @Test
    void temporaryIntegrationsLiveOutsideTheApplicationCore() {
        assertThat(ROOT.resolve("service/AllowingNaturalCalendarService.java")).doesNotExist();
        assertThat(ROOT.resolve("service/NotificationService.java")).doesNotExist();
        assertThat(ROOT.resolve("adapter/fake/AllowingNaturalCalendarService.java")).isRegularFile();
        assertThat(ROOT.resolve("adapter/fake/NotificationService.java")).isRegularFile();
    }

    private List<String> violations(Path root, List<String> forbidden) throws IOException {
        try (var sources = Files.walk(root)) {
            return sources.filter(path -> path.toString().endsWith(".java"))
                    .filter(path -> {
                        String source = source(path);
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
