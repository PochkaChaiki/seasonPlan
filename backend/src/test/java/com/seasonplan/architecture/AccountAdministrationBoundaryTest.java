package com.seasonplan.architecture;

import org.junit.jupiter.api.Test;
import java.nio.file.Files;
import java.nio.file.Path;
import static org.assertj.core.api.Assertions.assertThat;

class AccountAdministrationBoundaryTest {
    @Test
    void accountCoreUsesNeutralPortsAndOwnsCommands() throws Exception {
        Path root = Path.of("src/main/java/com/seasonplan/security");
        String service = Files.readString(root.resolve("service/LocalUserAdministrationService.java"));
        String contract = Files.readString(root.resolve("service/LocalUserAdministration.java"));
        assertThat(service).doesNotContain("org.springframework", "JdbcTemplate", "PasswordEncoder", "LocalUserDetails", "insert into", "update users");
        assertThat(contract).doesNotContain("LocalUserAdministrationService", "LocalUserDetails", "org.springframework");
        assertThat(root.resolve("repository/UserAccountRepository.java")).isRegularFile();
        assertThat(root.resolve("service/PasswordHasher.java")).isRegularFile();
        assertThat(root.resolve("service/AccountTransaction.java")).isRegularFile();
        assertThat(root.resolve("infrastructure/jdbc/JdbcUserAccountRepository.java")).isRegularFile();
        assertThat(root.resolve("config/AccountAdministrationConfiguration.java")).isRegularFile();
        for (String directory : java.util.List.of("service", "repository", "domain", "identity", "exception")) {
            try (var files = Files.walk(root.resolve(directory))) {
                for (Path file : files.filter(p -> p.toString().endsWith(".java")).toList()) {
                    assertThat(Files.readString(file)).as(file.toString()).doesNotContain("org.springframework", "jakarta.", "java.sql", "com.seasonplan.security.infrastructure", "com.seasonplan.lifecycle.");
                }
            }
        }
        assertThat(Files.readString(root.resolve("repository/UserAccountRepository.java")))
                .doesNotContain("com.seasonplan.security.service", "JdbcTemplate");
    }
}
