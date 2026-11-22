package com.seasonplan.architecture;

import org.junit.jupiter.api.Test;
import java.nio.file.*;
import java.util.List;
import static org.assertj.core.api.Assertions.assertThat;

class SecurityPackageLayoutTest {
    @Test void useCasesDelegateResourceAuthorizationToPolicy() throws Exception {
        Path root = Path.of("src/main/java/com/seasonplan");
        assertThat(Files.readString(root.resolve("season/application/SeasonPlanService.java"))).doesNotContain("actor.roles().contains");
        assertThat(Files.readString(root.resolve("reporting/application/SeasonReportService.java"))).doesNotContain("role == ActorRole.FAIRY_PLANNER");
        assertThat(Files.readString(root.resolve("response/application/IncidentResponseService.java"))).doesNotContain("role == ActorRole.FAIRY_PARTICIPANT");
    }

    @Test void identityOwnsAuthenticationAndItsCoreIsFrameworkIndependent() throws Exception {
        Path root = Path.of("src/main/java/com/seasonplan/identity");
        for (String file : List.of("api/ActorRole.java", "api/CurrentActor.java", "api/CurrentActorProvider.java", "api/LocalUserAdministration.java",
                "adapter/in/web/AuthController.java", "adapter/in/web/AdminUserController.java", "domain/ManagedUser.java", "domain/UsernameNormalizer.java",
                "adapter/out/persistence/JdbcLocalUserDetailsService.java", "adapter/out/spring/LocalUserDetails.java", "adapter/out/spring/SecurityCurrentActorProvider.java")) {
            assertThat(root.resolve(file)).as(file).isRegularFile();
        }
        try (var files = Files.walk(root)) {
            for (Path file : files.filter(p -> p.toString().endsWith(".java")).toList()) {
                assertThat(Files.readString(file)).as(file.toString()).doesNotContain("com.seasonplan.season.", "com.seasonplan.workflows.", "com.seasonplan.response.");
            }
        }
    }
}
