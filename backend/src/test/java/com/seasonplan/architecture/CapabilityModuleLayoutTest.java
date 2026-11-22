package com.seasonplan.architecture;

import org.junit.jupiter.api.Test;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import static org.assertj.core.api.Assertions.assertThat;

class CapabilityModuleLayoutTest {
    @Test void implementedCapabilitiesOwnTheirProductionTypes() {
        Path root = Path.of("src/main/java/com/seasonplan");
        for (String type : List.of("season/domain/SeasonPlan.java", "pollen/domain/PollenReservation.java",
                "work/domain/MagicTask.java", "ritual/domain/RitualSession.java", "monitoring/domain/ClimateMetric.java",
                "response/domain/MagicIncident.java", "reporting/domain/SeasonReport.java", "identity/domain/UsernameNormalizer.java",
                "reference/domain/ExecutionRuleSnapshot.java", "notification/api/NotificationOutbox.java", "audit/api/AuditTrail.java")) {
            assertThat(root.resolve(type)).as("capability owns %s", type).isRegularFile();
        }
        assertThat(root.resolve("lifecycle")).doesNotExist();
        assertThat(root.resolve("security")).doesNotExist();
    }

    @Test void businessDomainDoesNotDependOnFrameworksOrAdapters() throws Exception {
        Path root = Path.of("src/main/java/com/seasonplan");
        try (var files = Files.walk(root)) {
            for (Path file : files.filter(p -> p.toString().endsWith(".java") && p.toString().replace('\\', '/').contains("/domain/")).toList()) {
                assertThat(Files.readString(file)).as(file.toString()).doesNotContain("org.springframework", "com.fasterxml.jackson",
                        "jakarta.", "java.sql", "java.net", ".adapter.", ".application.");
            }
        }
    }
}
