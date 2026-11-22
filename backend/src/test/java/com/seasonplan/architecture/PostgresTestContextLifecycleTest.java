package com.seasonplan.architecture;

import org.junit.jupiter.api.Test;
import org.springframework.test.annotation.DirtiesContext;
import static org.assertj.core.api.Assertions.assertThat;

class PostgresTestContextLifecycleTest {
    @Test void springContextsCloseAtTheEndOfTheirContainerOwningClass() throws Exception {
        for (String name : new String[]{"com.seasonplan.lifecycle.infrastructure.jdbc.PostgresLifecyclePersistenceTest",
                "com.seasonplan.lifecycle.infrastructure.jdbc.PostgresExecutionRepositoriesTest",
                "com.seasonplan.security.infrastructure.jdbc.PostgresAccountAdministrationTest"}) {
            var annotation = Class.forName(name).getAnnotation(DirtiesContext.class);
            assertThat(annotation).as(name).isNotNull();
            assertThat(annotation.classMode()).isEqualTo(DirtiesContext.ClassMode.AFTER_CLASS);
            var cleanup = Class.forName(name).getDeclaredMethod("closeApplicationBeforeContainerStops", org.springframework.context.ConfigurableApplicationContext.class);
            assertThat(cleanup.getAnnotation(org.junit.jupiter.api.AfterAll.class)).isNotNull();
            assertThat(java.lang.reflect.Modifier.isStatic(cleanup.getModifiers())).isTrue();
        }
    }
}
