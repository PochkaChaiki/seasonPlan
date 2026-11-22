package com.seasonplan.architecture;

import org.junit.jupiter.api.Test;
import java.nio.file.*;
import java.util.regex.Pattern;
import static org.assertj.core.api.Assertions.assertThat;

class ReferenceBoundaryTest {
    @Test void referenceHasNoSeasonResponseOrWorkflowDependencyIncludingFullyQualifiedReferences() throws Exception {
        var references = Pattern.compile("com\\.seasonplan\\.([a-z]+)\\.");
        try (var files = Files.walk(Path.of("src/main/java/com/seasonplan/reference"))) {
            for (Path file : files.filter(p -> p.toString().endsWith(".java")).toList()) {
                var matches = references.matcher(Files.readString(file));
                while (matches.find()) assertThat(matches.group(1)).as(file.toString()).isIn("reference", "shared");
            }
        }
    }
    @Test void seasonUsesOnlyThePublicCalendarValidationContract() throws Exception {
        assertThat(Files.readString(Path.of("src/main/java/com/seasonplan/season/application/SeasonPlanService.java")))
                .contains("com.seasonplan.reference.api.NaturalCalendarValidation")
                .doesNotContain("com.seasonplan.reference.application", "calendar.validate(plan)");
    }
}
