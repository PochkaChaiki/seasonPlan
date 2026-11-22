package com.seasonplan.architecture;

import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;
import java.nio.file.*;
import java.util.Map;
import static org.assertj.core.api.Assertions.assertThat;

class RuntimeDatabaseConfigurationTest {
    @SuppressWarnings("unchecked")
    @Test void composeSeparatesRuntimeAndMigrationCredentials() throws Exception {
        Map<String, Object> compose = new Yaml().load(Files.readString(Path.of("..", "docker-compose.yml")));
        var services = (Map<String, Object>) compose.get("services");
        var backend = (Map<String, Object>) services.get("backend");
        var environment = (Map<String, Object>) backend.get("environment");
        assertThat(environment).containsEntry("SPRING_DATASOURCE_USERNAME", "${DB_RUNTIME_USER}")
                .containsEntry("SPRING_DATASOURCE_PASSWORD", "${DB_RUNTIME_PASSWORD}")
                .containsEntry("SPRING_LIQUIBASE_USER", "${POSTGRES_USER}")
                .containsEntry("SPRING_LIQUIBASE_PASSWORD", "${POSTGRES_PASSWORD}");
        assertThat(Files.readString(Path.of("src/main/resources/application.yml")))
                .contains("DB_RUNTIME_USER", "DB_RUNTIME_PASSWORD", "DB_MIGRATION_USER", "DB_MIGRATION_PASSWORD");
        Path attributes = Path.of("..", ".gitattributes");
        assertThat(attributes).isRegularFile();
        assertThat(Files.readString(attributes)).contains("infrastructure/postgres/*.sh text eol=lf");
    }
}
