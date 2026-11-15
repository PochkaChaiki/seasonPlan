package com.seasonplan.architecture;

import com.seasonplan.lifecycle.authorization.LifecycleAccessPolicy;
import com.seasonplan.lifecycle.authorization.LifecyclePermission;
import com.seasonplan.security.exception.AccessDeniedForRoleException;
import com.seasonplan.security.identity.ActorRole;
import com.seasonplan.security.identity.CurrentActor;
import org.junit.jupiter.api.Test;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import static com.seasonplan.lifecycle.authorization.LifecyclePermission.*;
import static com.seasonplan.security.identity.ActorRole.*;
import static org.assertj.core.api.Assertions.*;

class LifecyclePermissionMatrixTest {
    @Test void everyPermissionHasExactlyTheDocumentedRolesAndSelectionOrder() {
        Map<LifecyclePermission, List<ActorRole>> expected = new EnumMap<>(LifecyclePermission.class);
        for (var p : List.of(CREATE_DRAFT, UPDATE_DRAFT, SUBMIT_PLAN)) expected.put(p, List.of(FAIRY_PLANNER));
        for (var p : List.of(VERIFY_PLAN, APPROVE_PLAN, RETURN_PLAN, OVERRIDE_SEVERITY, APPROVE_REPORT, ARCHIVE_PLAN)) expected.put(p, List.of(CHIEF_GUARDIAN));
        expected.put(COMPLETE_CHECKLIST, List.of(TALENT_COORDINATOR, POLLEN_GUARDIAN));
        for (var p : List.of(MARK_RITUAL_READY, CREATE_TASK, UPDATE_TASK, ASSIGN_FAIRY, START_RITUAL, RECORD_RITUAL_EVENT, FINISH_RITUAL)) expected.put(p, List.of(TALENT_COORDINATOR));
        for (var p : List.of(REQUEST_POLLEN, CONFIRM_POLLEN, REJECT_POLLEN, RECORD_POLLEN_CONSUMPTION)) expected.put(p, List.of(POLLEN_GUARDIAN));
        for (var p : List.of(RECORD_MEASUREMENT, IMPORT_MEASUREMENTS)) expected.put(p, List.of(CLIMATE_BALANCER));
        for (var p : List.of(REGISTER_INCIDENT, CREATE_CORRECTION, RESOLVE_INCIDENT, REOPEN_INCIDENT)) expected.put(p, List.of(CHIEF_GUARDIAN, MAGIC_ANOMALY_CONTROLLER, CLIMATE_BALANCER));
        expected.put(CHANGE_CORRECTION, List.of(CHIEF_GUARDIAN, MAGIC_ANOMALY_CONTROLLER, CLIMATE_BALANCER, TALENT_COORDINATOR, FAIRY_PARTICIPANT));
        expected.put(GENERATE_REPORT, List.of(FAIRY_PLANNER, CHIEF_GUARDIAN));
        assertThat(expected.keySet()).containsExactlyInAnyOrder(LifecyclePermission.values());
        for (var entry : expected.entrySet()) {
            for (ActorRole role : ActorRole.values()) {
                if (entry.getValue().contains(role)) {
                    assertThatCode(() -> LifecycleAccessPolicy.require(role, entry.getKey())).doesNotThrowAnyException();
                    var singleRole = new CurrentActor(UUID.randomUUID(), "single", Set.of(role));
                    assertThat(LifecycleAccessPolicy.roleFor(singleRole, entry.getKey())).isEqualTo(role);
                } else {
                    assertThatThrownBy(() -> LifecycleAccessPolicy.require(role, entry.getKey())).isInstanceOf(AccessDeniedForRoleException.class);
                    var singleRole = new CurrentActor(UUID.randomUUID(), "single", Set.of(role));
                    assertThatThrownBy(() -> LifecycleAccessPolicy.roleFor(singleRole, entry.getKey())).isInstanceOf(AccessDeniedForRoleException.class);
                }
            }
            var allRoles = new CurrentActor(UUID.randomUUID(), "multi", Set.of(ActorRole.values()));
            assertThat(LifecycleAccessPolicy.roleFor(allRoles, entry.getKey())).isEqualTo(entry.getValue().get(0));
            var administratorOnly = new CurrentActor(UUID.randomUUID(), "admin", Set.of(), true);
            assertThatThrownBy(() -> LifecycleAccessPolicy.roleFor(administratorOnly, entry.getKey())).isInstanceOf(AccessDeniedForRoleException.class);
        }
    }
}
