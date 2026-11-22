package com.seasonplan.architecture;

import org.junit.jupiter.api.Test;
import java.nio.file.*;
import java.util.List;
import static org.assertj.core.api.Assertions.assertThat;

class SeasonLifecycleOwnershipTest {
    private final Path root = Path.of("src/main/java/com/seasonplan");
    @Test void foreignServicesDoNotWriteSeasonState() throws Exception {
        for (String module : List.of("ritual/application/RitualService", "reporting/application/SeasonReportService", "response/application/IncidentResponseService")) {
            assertThat(Files.readString(root.resolve(module + ".java"))).as(module)
                    .doesNotContain("plans.save(", "plan.activate(", "plan.complete(", "plan.archive(", "ServiceSupport.enterEmergency(")
                    .contains("SeasonLifecycle");
        }
        assertThat(Files.readString(root.resolve("response/application/MetricReactionService.java"))).contains("SeasonLifecycle");
        assertThat(Files.readString(root.resolve("season/application/ServiceSupport.java"))).doesNotContain("void enterEmergency(");
    }
    @Test void seasonDomainUsesOnlyOwnerEvidence() throws Exception {
        try (var files = Files.walk(root.resolve("season/domain"))) {
            for (Path file : files.filter(p -> p.toString().endsWith(".java")).toList()) {
                assertThat(Files.readString(file)).as(file.toString()).doesNotContain("com.seasonplan.ritual.", "com.seasonplan.reporting.", "com.seasonplan.response.");
            }
        }
    }
    @Test void lifecycleOwnerHasNoForeignRepositoriesOrWorkflowDependencies() throws Exception {
        Path owner = root.resolve("season/application/SeasonLifecycleService.java");
        assertThat(owner).isRegularFile();
        assertThat(Files.readString(owner)).doesNotContain("com.seasonplan.workflows", "com.seasonplan.ritual", "com.seasonplan.reporting", "com.seasonplan.response", "com.seasonplan.monitoring");
    }
}
