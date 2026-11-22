package com.seasonplan.architecture;

import com.seasonplan.pollen.application.PollenService;
import org.junit.jupiter.api.Test;
import java.nio.file.*;
import static org.assertj.core.api.Assertions.assertThat;

class PollenBoundarySimplificationTest {
    private static final Path ROOT = Path.of("src/main/java/com/seasonplan");

    @Test void otherModulesCannotBypassPollenToCallItsExternalClient() throws Exception {
        try (var files = Files.walk(ROOT)) {
            for (Path file : files.filter(p -> p.toString().endsWith(".java")).toList()) {
                String path = ROOT.relativize(file).toString().replace('\\', '/');
                if (path.startsWith("pollen/") || path.startsWith("bootstrap/")) continue;
                assertThat(Files.readString(file)).as(path).doesNotContain("import com.seasonplan.pollen.application.port.PollenTreeClient", "RestPollenTreeClient", "FakePollenTreeClient");
            }
        }
    }

    @Test void publicPollenResultsDoNotExposeExternalClientTypes() {
        for (var method : PollenService.class.getDeclaredMethods()) {
            if (java.lang.reflect.Modifier.isPublic(method.getModifiers())) {
                assertThat(method.getGenericReturnType().getTypeName()).as(method.getName()).doesNotContain("PollenTreeClient");
            }
        }
    }

    @Test void pollenHasOneExternalBoundaryAndNoLocalStockRepository() throws Exception {
        assertThat(ROOT.resolve("pollen/api/PollenOperations.java")).isRegularFile();
        assertThat(ROOT.resolve("pollen/application/port/PollenTreeClient.java")).isRegularFile();
        try (var files = Files.walk(ROOT.resolve("pollen"))) {
            assertThat(files.filter(p -> p.getFileName().toString().contains("ReservationRepository")).toList()).isEmpty();
        }
    }
}
