package com.seasonplan.architecture;

import org.junit.jupiter.api.Test;
import java.nio.file.*;
import static org.assertj.core.api.Assertions.assertThat;

class MetricResponseOwnershipTest {
    private final Path root = Path.of("src/main/java/com/seasonplan");
    @Test void monitoringUsesOnlyResponsePublicSpecifications() throws Exception {
        for (String path : new String[]{"monitoring/application/MonitoringService.java", "monitoring/domain/MetricReactionPolicy.java"}) {
            assertThat(Files.readString(root.resolve(path))).as(path)
                    .doesNotContain("com.seasonplan.response.domain", "com.seasonplan.response.application", "MagicIncidentRepository", "CorrectionActionRepository")
                    .contains("com.seasonplan.response.api.MetricReactions");
        }
        assertThat(Files.readString(root.resolve("monitoring/application/MonitoringService.java")))
                .doesNotContain("SeasonLifecycle", "NotificationOutbox", "incidents.save", "actions.save", "MAGIC_INCIDENT_REGISTERED", "CORRECTION_ACTION_CREATED", "plans.save(", "plan.activate(", "plan.complete(", "plan.archive(");
    }
    @Test void responseMetricOwnerHasNoMonitoringOrWorkflowDependency() throws Exception {
        Path owner = root.resolve("response/application/MetricReactionService.java");
        assertThat(owner).isRegularFile();
        assertThat(Files.readString(owner)).doesNotContain("com.seasonplan.monitoring", "com.seasonplan.workflows");
        assertThat(Files.readString(root.resolve("response/api/MetricReactions.java")))
                .doesNotContain(".domain.", "Repository", "org.springframework");
    }
}
