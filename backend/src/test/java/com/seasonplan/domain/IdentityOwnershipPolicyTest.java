package com.seasonplan.domain;

import com.seasonplan.identity.api.*;
import com.seasonplan.identity.domain.LifecycleAuthorizationPolicy;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import java.util.Set;
import java.util.UUID;
import static org.assertj.core.api.Assertions.*;

class IdentityOwnershipPolicyTest {
    @ParameterizedTest @EnumSource(ActorRole.class)
    void draftOwnershipAlwaysRequiresTheCreator(ActorRole role) {
        assertThatCode(() -> LifecycleAuthorizationPolicy.requireDraftOwner(role, "owner", "owner")).doesNotThrowAnyException();
        assertThatThrownBy(() -> LifecycleAuthorizationPolicy.requireDraftOwner(role, "other", "owner")).isInstanceOf(AccessDeniedForRoleException.class);
        assertThatThrownBy(() -> LifecycleAuthorizationPolicy.requireDraftOwner(role, null, "owner")).isInstanceOf(AccessDeniedForRoleException.class);
    }
    @ParameterizedTest @EnumSource(ActorRole.class)
    void correctionOwnershipRemainsRequiredForParticipantAndCoordinator(ActorRole role) {
        assertThatCode(() -> LifecycleAuthorizationPolicy.requireCorrectionOwner(role, "owner", "owner")).doesNotThrowAnyException();
        if (role == ActorRole.FAIRY_PARTICIPANT || role == ActorRole.TALENT_COORDINATOR)
            assertThatThrownBy(() -> LifecycleAuthorizationPolicy.requireCorrectionOwner(role, "other", "owner")).isInstanceOf(AccessDeniedForRoleException.class);
        else assertThatCode(() -> LifecycleAuthorizationPolicy.requireCorrectionOwner(role, "other", "owner")).doesNotThrowAnyException();
    }
    @Test void reportOwnershipRemainsRequiredForPlannerWithoutRestrictingChief() {
        assertThatCode(() -> LifecycleAccessPolicy.requireReportOwner(ActorRole.FAIRY_PLANNER, "owner", "owner")).doesNotThrowAnyException();
        assertThatThrownBy(() -> LifecycleAccessPolicy.requireReportOwner(ActorRole.FAIRY_PLANNER, "other", "owner")).isInstanceOf(AccessDeniedForRoleException.class);
        assertThatCode(() -> LifecycleAccessPolicy.requireReportOwner(ActorRole.CHIEF_GUARDIAN, "other", "owner")).doesNotThrowAnyException();
    }
    @Test void historyRequiresChiefOrCreatorWithPlannerRoleNotAdministratorAuthority() {
        UUID owner = UUID.randomUUID();
        var planner = new CurrentActor(owner, "planner", Set.of(ActorRole.FAIRY_PLANNER));
        var chief = new CurrentActor(UUID.randomUUID(), "chief", Set.of(ActorRole.CHIEF_GUARDIAN));
        assertThatCode(() -> LifecycleAccessPolicy.requireReviewHistory(planner, owner.toString())).doesNotThrowAnyException();
        assertThatCode(() -> LifecycleAccessPolicy.requireReviewHistory(chief, owner.toString())).doesNotThrowAnyException();
        assertThatThrownBy(() -> LifecycleAccessPolicy.requireReviewHistory(planner, UUID.randomUUID().toString())).isInstanceOf(AccessDeniedForRoleException.class);
        var participant = new CurrentActor(owner, "participant", Set.of(ActorRole.FAIRY_PARTICIPANT));
        var administrator = new CurrentActor(owner, "administrator", Set.of(), true);
        assertThatThrownBy(() -> LifecycleAccessPolicy.requireReviewHistory(participant, owner.toString())).isInstanceOf(AccessDeniedForRoleException.class);
        assertThatThrownBy(() -> LifecycleAccessPolicy.requireReviewHistory(administrator, owner.toString())).isInstanceOf(AccessDeniedForRoleException.class);
    }
}
