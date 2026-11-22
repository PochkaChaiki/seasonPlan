package com.seasonplan.architecture;

import org.junit.jupiter.api.Test;
import java.nio.file.Files;
import java.nio.file.Path;
import static org.assertj.core.api.Assertions.assertThat;

class AuditBoundaryTest {
    @Test
    void auditHasIndependentCoreAndConsumersUseOnlyItsApi() throws Exception {
        Path root = Path.of("src/main/java/com/seasonplan");
        assertThat(root.resolve("audit/api/AuditTrail.java")).isRegularFile();
        for (String dir : new String[]{"audit/api", "audit/application", "audit/domain"}) {
            try (var files = Files.walk(root.resolve(dir))) {
                for (Path file : files.filter(p -> p.toString().endsWith(".java")).toList()) {
                    assertThat(Files.readString(file)).as(file.toString()).doesNotContain("com.seasonplan.lifecycle", "org.springframework", "java.sql");
                }
            }
        }
        try (var files = Files.walk(root)) {
            for (Path file : files.filter(p -> p.toString().endsWith(".java"))
                    .filter(p -> !root.relativize(p).startsWith("audit") && !root.relativize(p).startsWith("bootstrap")).toList()) {
                assertThat(Files.readString(file)).as(file.toString()).doesNotContain("com.seasonplan.audit.application", "com.seasonplan.audit.adapter");
            }
        }
    }
}
