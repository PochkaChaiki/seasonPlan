package com.seasonplan.architecture;

import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class DevelopmentComposeReadinessContractTest {
    @Test
    void frontendWaitsUntilBackendCanServeCsrfRequests() throws IOException {
        Map<String, Object> compose = new Yaml().load(Files.readString(composeFile()));
        Map<String, Object> services = map(compose.get("services"));
        Map<String, Object> backend = map(services.get("backend"));
        Map<String, Object> frontend = map(services.get("frontend"));

        Map<String, Object> healthcheck = map(backend.get("healthcheck"));
        assertThat(healthcheck).as("backend healthcheck").isNotNull();
        assertThat(list(healthcheck.get("test")))
                .anySatisfy(command -> assertThat(command.toString()).contains("/auth/csrf"));

        Map<String, Object> frontendDependencies = map(frontend.get("depends_on"));
        assertThat(frontendDependencies).as("frontend dependencies").isNotNull();
        assertThat(map(frontendDependencies.get("backend")))
                .containsEntry("condition", "service_healthy");
    }

    private static Path composeFile() {
        for (Path candidate : List.of(Path.of("docker-compose.yml"), Path.of("..", "docker-compose.yml"))) {
            if (Files.isRegularFile(candidate)) {
                return candidate;
            }
        }
        throw new IllegalStateException("docker-compose.yml not found from " + Path.of("").toAbsolutePath());
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> map(Object value) {
        return (Map<String, Object>) value;
    }

    @SuppressWarnings("unchecked")
    private static List<Object> list(Object value) {
        return (List<Object>) value;
    }
}
