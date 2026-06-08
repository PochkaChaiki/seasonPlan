package com.seasonplan.architecture;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class ArchitectureBoundaryTest {
    @Test
    void domainSourcesDoNotDependOnFrameworkOrSerializationPackages() throws IOException {
        Path domainRoot = Path.of("src/main/java/com/seasonplan/lifecycle/domain");

        var violations = Files.walk(domainRoot)
                .filter(path -> path.toString().endsWith(".java"))
                .filter(path -> source(path).contains("com.fasterxml.jackson")
                        || source(path).contains("org.springframework")
                        || source(path).contains("java.sql")
                        || source(path).contains("com.seasonplan.lifecycle.persistence"))
                .map(domainRoot::relativize)
                .map(Path::toString)
                .sorted()
                .toList();

        assertThat(violations).isEmpty();
    }

    @Test
    void applicationServicesDoNotDependOnPeerApplicationServicesForUseCaseData() throws IOException {
        Path serviceRoot = Path.of("src/main/java/com/seasonplan/lifecycle/service");

        var violations = Files.walk(serviceRoot)
                .filter(path -> path.toString().endsWith("Service.java"))
                .filter(path -> source(path).contains("private final SeasonPlanService")
                        || source(path).contains("private final MagicTaskService"))
                .map(serviceRoot::relativize)
                .map(Path::toString)
                .sorted()
                .toList();

        assertThat(violations).isEmpty();
    }

    private String source(Path path) {
        try {
            return Files.readString(path);
        } catch (IOException error) {
            throw new IllegalStateException(error);
        }
    }
}
