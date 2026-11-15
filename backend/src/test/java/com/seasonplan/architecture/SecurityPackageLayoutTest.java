package com.seasonplan.architecture;

import org.junit.jupiter.api.Test;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import static org.assertj.core.api.Assertions.assertThat;

class SecurityPackageLayoutTest {
    @Test
    void lifecycleServicesDelegateResourceAuthorizationToPolicy() throws Exception {
        Path services = Path.of("src/main/java/com/seasonplan/lifecycle/service");
        assertThat(Files.readString(services.resolve("SeasonPlanService.java")))
                .doesNotContain("actor.roles().contains");
        assertThat(Files.readString(services.resolve("SeasonReportService.java")))
                .doesNotContain("role == ActorRole.FAIRY_PLANNER");
        assertThat(Files.readString(services.resolve("IncidentResponseService.java")))
                .doesNotContain("role == ActorRole.FAIRY_PARTICIPANT");
    }

    @Test
    void securityPackagesOwnIdentityAndDoNotDependOnLifecycle() throws Exception {
        Path security = Path.of("src/main/java/com/seasonplan/security");
        for (String file : List.of("identity/ActorRole.java", "identity/CurrentActor.java", "identity/CurrentActorProvider.java",
                "api/AuthController.java", "api/AdminUserController.java", "api/UserAdministrationExceptionHandler.java",
                "domain/ManagedUser.java", "domain/UsernameNormalizer.java", "service/LocalUserAdministration.java",
                "service/LocalUserAdministrationService.java", "exception/UserAdministrationException.java", "exception/AccessDeniedForRoleException.java",
                "infrastructure/jdbc/JdbcLocalUserDetailsService.java", "infrastructure/spring/LocalUserDetails.java",
                "infrastructure/spring/SecurityCurrentActorProvider.java", "infrastructure/spring/JsonAuthenticationHandlers.java",
                "config/SecurityConfiguration.java", "config/DevelopmentAdministratorBootstrap.java")) {
            assertThat(security.resolve(file)).as(file).isRegularFile();
        }
        try (var files = Files.list(security)) {
            assertThat(files.filter(p -> p.toString().endsWith(".java"))).isEmpty();
        }
        try (var files = Files.walk(security)) {
            for (Path file : files.filter(p -> p.toString().endsWith(".java")).toList()) {
                assertThat(Files.readString(file)).as(file.toString()).doesNotContain("import com.seasonplan.lifecycle.");
            }
        }
        for (String directory : List.of("identity", "domain", "exception")) {
            try (var files = Files.walk(security.resolve(directory))) {
                for (Path file : files.filter(p -> p.toString().endsWith(".java")).toList()) {
                    assertThat(Files.readString(file)).as(file.toString()).doesNotContain("org.springframework", "jakarta.", "java.sql");
                }
            }
        }
        Path lifecycle = Path.of("src/main/java/com/seasonplan/lifecycle");
        for (String file : List.of("domain/ActorRole.java", "domain/CurrentActor.java", "service/CurrentActorProvider.java",
                "exception/AccessDeniedForRoleException.java", "config/SecurityConfiguration.java")) {
            assertThat(lifecycle.resolve(file)).doesNotExist();
        }
    }
}
