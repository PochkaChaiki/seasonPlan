package com.seasonplan.architecture;

import org.junit.jupiter.api.Test;
import java.nio.file.*;
import static org.assertj.core.api.Assertions.assertThat;

class SeasonQueryBoundaryTest {
    @Test void foreignConsumersNeverReadSeasonInternalsIncludingFullyQualifiedReferences() throws Exception {
        Path root = Path.of("src/main/java/com/seasonplan");
        try (var files = Files.walk(root)) {
            for (Path file : files.filter(p -> p.toString().endsWith(".java")).toList()) {
                String module = root.relativize(file).getName(0).toString();
                if (module.equals("season") || module.equals("bootstrap")) continue;
                assertThat(Files.readString(file)).as(file.toString()).doesNotContain("SeasonPlanRepository", "com.seasonplan.season.application.ServiceSupport", "ServiceSupport.plan(");
            }
        }
    }
    @Test void queryFactsAndContractDoNotExposeMutableAggregates() throws Exception {
        for (String name : new String[]{"SeasonFacts", "SeasonQueries"}) {
            Path file = Path.of("src/main/java/com/seasonplan/season/api/" + name + ".java");
            assertThat(file).isRegularFile();
            assertThat(Files.readString(file)).doesNotContain("SeasonPlan;", "SeasonPlan ", "ChecklistItem", "toAggregate", "Repository");
        }
    }
}
