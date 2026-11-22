package com.seasonplan.architecture;

import org.junit.jupiter.api.Test;
import java.nio.file.*;
import static org.assertj.core.api.Assertions.assertThat;

class IdentityAuthorizationBoundaryTest {
    private static final Path ROOT = Path.of("src/main/java/com/seasonplan");
    @Test void businessModulesDoNotDependBackOnWorkflows() throws Exception {
        try (var files = Files.walk(ROOT)) {
            for (Path file : files.filter(path -> path.toString().endsWith(".java")).toList()) {
                String relative = ROOT.relativize(file).toString().replace('\\', '/');
                if (!relative.startsWith("workflows/") && !relative.startsWith("bootstrap/"))
                    assertThat(Files.readString(file)).as(relative).doesNotContain("com.seasonplan.workflows");
            }
        }
    }
    @Test void identityOwnsAuthorizationWithoutForeignAggregates() throws Exception {
        Path policy = ROOT.resolve("identity/domain/LifecycleAuthorizationPolicy.java");
        assertThat(policy).isRegularFile();
        assertThat(Files.readString(policy)).doesNotContain("com.seasonplan.season", "com.seasonplan.response", "com.seasonplan.workflows");
        assertThat(ROOT.resolve("identity/api/LifecycleAccessPolicy.java")).isRegularFile();
        assertThat(ROOT.resolve("identity/api/LifecyclePermission.java")).isRegularFile();
        assertThat(ROOT.resolve("workflows/domain/authorization/LifecycleAccessPolicy.java")).doesNotExist();
    }
}
