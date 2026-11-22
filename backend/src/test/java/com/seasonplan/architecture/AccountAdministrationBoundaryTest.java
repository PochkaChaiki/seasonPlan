package com.seasonplan.architecture;

import org.junit.jupiter.api.Test;
import java.nio.file.*;
import java.util.List;
import static org.assertj.core.api.Assertions.assertThat;

class AccountAdministrationBoundaryTest {
    @Test void accountCoreUsesNeutralPortsAndDomainValidation() throws Exception {
        Path root = Path.of("src/main/java/com/seasonplan/identity");
        String service = Files.readString(root.resolve("application/LocalUserAdministrationService.java"));
        String contract = Files.readString(root.resolve("api/LocalUserAdministration.java"));
        assertThat(service).doesNotContain("org.springframework", "JdbcTemplate", "PasswordEncoder", "LocalUserDetails", "insert into", "update users");
        assertThat(contract).doesNotContain("LocalUserAdministrationService", "LocalUserDetails", "org.springframework");
        for (String path : List.of("application/port/UserAccountRepository.java", "application/port/PasswordHasher.java",
                "application/port/AccountTransaction.java", "adapter/out/persistence/JdbcUserAccountRepository.java", "domain/AccountValidation.java")) {
            assertThat(root.resolve(path)).isRegularFile();
        }
        for (String directory : List.of("application", "domain", "api")) {
            try (var files = Files.walk(root.resolve(directory))) {
                for (Path file : files.filter(p -> p.toString().endsWith(".java")).toList()) {
                    assertThat(Files.readString(file)).as(file.toString()).doesNotContain("org.springframework", "jakarta.", "java.sql", "com.seasonplan.identity.adapter.");
                }
            }
        }
        assertThat(Files.readString(root.resolve("application/port/UserAccountRepository.java"))).doesNotContain("LocalUserAdministrationService", "JdbcTemplate");
    }
}
