package com.seasonplan.architecture;

import org.junit.jupiter.api.Test;
import java.nio.file.*;
import static org.assertj.core.api.Assertions.assertThat;

class SharedCoreOwnershipTest {
    @Test void sharedCodeDoesNotKnowBusinessCapabilities() throws Exception {
        try (var files = Files.walk(Path.of("src/main/java/com/seasonplan/shared"))) {
            for (Path file : files.filter(path -> path.toString().endsWith(".java")).toList()) {
                assertThat(Files.readString(file)).as(file.toString()).doesNotContain(
                        "com.seasonplan.season", "com.seasonplan.pollen", "com.seasonplan.work.",
                        "com.seasonplan.ritual", "com.seasonplan.monitoring", "com.seasonplan.response",
                        "com.seasonplan.reporting", "com.seasonplan.reference", "com.seasonplan.workflows");
            }
        }
    }
    @Test void seasonPlanningHasNoUnusedWorkRepositoryDependencies() throws Exception {
        assertThat(Files.readString(Path.of("src/main/java/com/seasonplan/season/application/SeasonPlanService.java")))
                .doesNotContain("com.seasonplan.work.application.port", "MagicTaskRepository", "FairyAssignmentRepository");
    }
}
