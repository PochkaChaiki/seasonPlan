package com.seasonplan.architecture;

import com.seasonplan.lifecycle.application.pollen.PollenService;
import org.junit.jupiter.api.Test;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import static org.assertj.core.api.Assertions.assertThat;

class PollenBoundarySimplificationTest {
    private static final Path ROOT = Path.of("src/main/java/com/seasonplan/lifecycle");

    @Test void otherUseCasesCannotBypassPollenServiceToAccessTheExternalClient() throws IOException {
        try (var sources = Files.walk(ROOT)) {
            var violations = sources.filter(path -> path.toString().endsWith(".java"))
                    .filter(path -> {
                        String relative = ROOT.relativize(path).toString().replace('\\', '/');
                        return !relative.startsWith("adapter/") && !relative.startsWith("config/")
                                && !relative.equals("application/port/PollenTreeClient.java")
                                && !relative.equals("application/pollen/PollenService.java");
                    })
                    .filter(path -> {
                        try { return Files.readString(path).matches("(?s).*\\b(?:PollenTreeClient|RestPollenTreeClient|FakePollenTreeClient)\\b.*"); }
                        catch (IOException error) { throw new IllegalStateException(error); }
                    })
                    .map(ROOT::relativize).toList();
            assertThat(violations).as("all pollen access must go through PollenService").isEmpty();
        }
    }

    @Test void pollenServiceDoesNotExposeExternalClientTypesToItsCallers() {
        for (var method : PollenService.class.getDeclaredMethods()) {
            if (java.lang.reflect.Modifier.isPublic(method.getModifiers())) {
                assertThat(method.getGenericReturnType().getTypeName()).as("return type of %s", method.getName())
                        .doesNotContain("PollenTreeClient");
            }
        }
    }

    @Test void pollenHasOneApplicationServiceOneExternalClientAndNoLocalReservationRepository() {
        assertThat(ROOT.resolve("application/pollen/PollenService.java")).isRegularFile();
        assertThat(ROOT.resolve("application/port/PollenTreeClient.java")).isRegularFile();
        for (String obsolete : List.of(
                "application/preparation/PollenReservationService.java",
                "application/execution/PollenConsumptionService.java",
                "application/port/PollenTree.java",
                "application/port/PollenTreeReservations.java",
                "application/port/PollenReservationRepository.java",
                "persistence/JdbcPollenReservationRepository.java",
                "adapter/fake/InMemoryPollenReservationRepository.java")) {
            assertThat(ROOT.resolve(obsolete)).as("obsolete abstraction %s", obsolete).doesNotExist();
        }
    }
}
