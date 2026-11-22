package com.seasonplan.architecture;

import org.junit.jupiter.api.Test;
import java.lang.reflect.*;
import java.nio.file.*;
import java.util.*;
import static org.assertj.core.api.Assertions.assertThat;

class ImmutableCommandContractTest {
    private final Set<Type> visited = new HashSet<>();
    @Test void publicCommandsNeverExposeMutableAggregatesEvenThroughNestedGenerics() {
        for (Class<?> api : List.of(com.seasonplan.season.api.SeasonPlanning.class, com.seasonplan.work.api.WorkPreparation.class,
                com.seasonplan.pollen.api.PollenOperations.class)) {
            for (Method method : api.getMethods()) {
                check(method.getGenericReturnType());
                for (Type input : method.getGenericParameterTypes()) check(input);
            }
        }
    }
    @Test void checklistCompletionReturnsItsCommandSnapshotWithoutASecondRead() throws Exception {
        assertThat(com.seasonplan.season.api.SeasonPlanning.class.getMethod("completeItem", UUID.class, String.class,
                com.seasonplan.identity.api.ActorRole.class, String.class).getReturnType()).isEqualTo(com.seasonplan.season.api.SeasonFacts.class);
        String source = Files.readString(Path.of("src/main/java/com/seasonplan/workflows/adapter/in/web/LifecycleController.java"));
        String method = source.substring(source.indexOf("public SeasonPlanResponse completeReadinessChecklistItem"), source.indexOf("@PostMapping(\"/season-plans/{id}/pollen-reservations\")"));
        assertThat(method).doesNotContain("seasonPlanService.get(");
    }
    private void check(Type type) {
        if (!visited.add(type)) return;
        if (type instanceof ParameterizedType generic) {
            check(generic.getRawType()); for (Type argument : generic.getActualTypeArguments()) check(argument);
        } else if (type instanceof Class<?> value) {
            assertThat(value.getSimpleName()).isNotIn("SeasonPlan", "MagicTask", "FairyAssignment", "PollenReservation", "ChecklistItem");
            if (value.isArray()) check(value.getComponentType());
            if (value.isRecord()) for (var component : value.getRecordComponents()) check(component.getGenericType());
        }
    }
    @Test void httpMappersUseOnlyImmutableCommandContracts() throws Exception {
        for (String name : List.of("LifecycleController", "LifecycleApiModels")) assertThat(Files.readString(Path.of("src/main/java/com/seasonplan/workflows/adapter/in/web/" + name + ".java")))
                .doesNotContain("import com.seasonplan.season.domain.SeasonPlan;", "import com.seasonplan.work.domain.MagicTask;", "import com.seasonplan.work.domain.FairyAssignment;", "import com.seasonplan.pollen.domain.PollenReservation;");
    }
}
