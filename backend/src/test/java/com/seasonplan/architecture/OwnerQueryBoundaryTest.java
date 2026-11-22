package com.seasonplan.architecture;

import org.junit.jupiter.api.Test;
import java.nio.file.*;
import java.util.*;
import java.util.regex.Pattern;
import static org.assertj.core.api.Assertions.assertThat;

class OwnerQueryBoundaryTest {
    @Test void noProductionCapabilityReferencesAnotherOwnersRepositoryIncludingFqns() throws Exception {
        Path root = Path.of("src/main/java/com/seasonplan");
        Map<String, String> repositoryOwners = new HashMap<>();
        List<Path> files;
        try (var walk = Files.walk(root)) { files = walk.filter(p -> p.toString().endsWith(".java")).toList(); }
        var declarations = Pattern.compile("(?:interface|class)\\s+(\\w*Repository)\\b");
        for (Path file : files) {
            var matcher = declarations.matcher(Files.readString(file));
            while (matcher.find()) repositoryOwners.put(matcher.group(1), root.relativize(file).getName(0).toString());
        }
        for (Path file : files) {
            String owner = root.relativize(file).getName(0).toString();
            if (owner.equals("bootstrap")) continue;
            String source = Files.readString(file);
            for (var repository : repositoryOwners.entrySet()) {
                if (!repository.getValue().equals(owner)) assertThat(Pattern.compile("\\b" + repository.getKey() + "\\b").matcher(source).find())
                        .as(file + " references foreign " + repository.getKey()).isFalse();
            }
        }
    }
    @Test void workReadContractsDoNotExposeMutableModelsAndPinnedRulesBelongToMonitoring() throws Exception {
        Path root = Path.of("src/main/java/com/seasonplan");
        for (String name : List.of("TaskFacts", "AssignmentFacts", "WorkQueries")) {
            Path file = root.resolve("work/api/" + name + ".java");
            assertThat(file).isRegularFile();
            assertThat(Files.readString(file)).doesNotContain("MagicTask;", "MagicTask ", "FairyAssignment", "Repository", "toAggregate");
        }
        assertThat(root.resolve("monitoring/application/PinnedMetricRuleProvider.java")).isRegularFile();
        assertThat(root.resolve("workflows/application/PinnedMetricRuleProvider.java")).doesNotExist();
    }
}
