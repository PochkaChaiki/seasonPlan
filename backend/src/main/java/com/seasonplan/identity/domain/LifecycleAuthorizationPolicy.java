package com.seasonplan.identity.domain;

import com.seasonplan.identity.api.LifecyclePermission;
import com.seasonplan.identity.api.AccessDeniedForRoleException;
import com.seasonplan.identity.api.ActorRole;
import com.seasonplan.identity.api.CurrentActor;
import java.util.List;
import static com.seasonplan.identity.api.ActorRole.*;

public final class LifecycleAuthorizationPolicy {
    private LifecycleAuthorizationPolicy() { }

    private static List<ActorRole> allowed(LifecyclePermission permission) {
        return switch (permission) {
            case CREATE_DRAFT, UPDATE_DRAFT, SUBMIT_PLAN -> List.of(FAIRY_PLANNER);
            case VERIFY_PLAN, APPROVE_PLAN, RETURN_PLAN, OVERRIDE_SEVERITY, APPROVE_REPORT, ARCHIVE_PLAN -> List.of(CHIEF_GUARDIAN);
            case COMPLETE_CHECKLIST -> List.of(TALENT_COORDINATOR, POLLEN_GUARDIAN);
            case MARK_RITUAL_READY, CREATE_TASK, UPDATE_TASK, ASSIGN_FAIRY, START_RITUAL, RECORD_RITUAL_EVENT, FINISH_RITUAL -> List.of(TALENT_COORDINATOR);
            case REQUEST_POLLEN, CONFIRM_POLLEN, REJECT_POLLEN, RECORD_POLLEN_CONSUMPTION -> List.of(POLLEN_GUARDIAN);
            case RECORD_MEASUREMENT, IMPORT_MEASUREMENTS -> List.of(CLIMATE_BALANCER);
            case REGISTER_INCIDENT, CREATE_CORRECTION, RESOLVE_INCIDENT, REOPEN_INCIDENT -> List.of(CHIEF_GUARDIAN, MAGIC_ANOMALY_CONTROLLER, CLIMATE_BALANCER);
            case CHANGE_CORRECTION -> List.of(CHIEF_GUARDIAN, MAGIC_ANOMALY_CONTROLLER, CLIMATE_BALANCER, TALENT_COORDINATOR, FAIRY_PARTICIPANT);
            case GENERATE_REPORT -> List.of(FAIRY_PLANNER, CHIEF_GUARDIAN);
        };
    }

    public static void require(ActorRole role, LifecyclePermission permission) {
        if (role == null || !allowed(permission).contains(role)) throw new AccessDeniedForRoleException(role);
    }

    public static void requireDraftOwner(ActorRole role, String actorId, String creatorId) {
        if (actorId == null || !actorId.equals(creatorId)) throw new AccessDeniedForRoleException(role);
    }

    public static void requireReviewHistory(CurrentActor actor, String creatorId) {
        if (!actor.roles().contains(CHIEF_GUARDIAN)
                && !(actor.roles().contains(FAIRY_PLANNER) && actor.userId().toString().equals(creatorId))) {
            throw new AccessDeniedForRoleException((ActorRole) null);
        }
    }

    /** Additional resource check after GENERATE_REPORT permission has been established. */
    public static void requireReportOwner(ActorRole role, String actorId, String creatorId) {
        if (role == FAIRY_PLANNER && !actorId.equals(creatorId)) throw new AccessDeniedForRoleException(role);
    }

    /** Additional resource check after CHANGE_CORRECTION permission has been established. */
    public static void requireCorrectionOwner(ActorRole role, String actorId, String ownerId) {
        if ((role == FAIRY_PARTICIPANT || role == TALENT_COORDINATOR) && !ownerId.equals(actorId)) {
            throw new AccessDeniedForRoleException(role);
        }
    }

    public static ActorRole roleFor(CurrentActor actor, LifecyclePermission permission) {
        for (ActorRole role : allowed(permission)) {
            if (actor.roles().contains(role)) return role;
        }
        throw new AccessDeniedForRoleException("authenticated actor lacks required role");
    }
}
