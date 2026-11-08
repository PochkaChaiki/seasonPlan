package com.seasonplan.lifecycle.application.approval;

import com.seasonplan.lifecycle.domain.*;
import com.seasonplan.lifecycle.application.port.PlannerNotifications;
import com.seasonplan.lifecycle.application.pollen.PollenService;
import com.seasonplan.lifecycle.application.port.SeasonPlanRepository;
import com.seasonplan.lifecycle.application.shared.*;

import java.time.Clock;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.UUID;

/** UC-2: review decisions. Existing public behavior is retained by the facade. */
public class ApprovalService {
    private final SeasonPlanRepository plans;
    private final PollenService pollen;
    private final AuditLogService audit;
    private final PlannerNotifications notifications;
    private final Clock clock;

    public ApprovalService(SeasonPlanRepository plans, PollenService pollen, AuditLogService audit,
                           PlannerNotifications notifications, Clock clock) {
        this.plans = plans;
        this.pollen = pollen;
        this.audit = audit;
        this.notifications = notifications;
        this.clock = clock;
    }

    public SeasonPlan approve(UUID id, String comment, ActorRole role, String actorId) {
        ValidationSupport.requireRole(role, ActorRole.CHIEF_GUARDIAN);
        SeasonPlan plan = requireSubmitted(id);
        if (pollen.findLatestBySeasonPlanId(id)
                .filter(reservation -> reservation.status() == PollenReservationStatus.CONFIRMED)
                .isEmpty()) {
            throw new DomainValidationException(new LinkedHashMap<>() {{
                put("pollenReservation", List.of("confirmed pollen reservation is required"));
            }});
        }
        plan.approve(comment, actorId, clock.instant());
        audit.append(id, AuditAction.SEASON_PLAN_APPROVED, actorId, comment);
        notifications.notifyPlannerApprovalResult(plan.createdBy(), "APPROVED", comment);
        return plans.save(plan);
    }

    public SeasonPlan returnForRevision(UUID id, String comment, ActorRole role, String actorId) {
        ValidationSupport.requireRole(role, ActorRole.CHIEF_GUARDIAN);
        SeasonPlan plan = requireSubmitted(id);
        plan.returnForRevision(comment, actorId, clock.instant());
        audit.append(id, AuditAction.SEASON_PLAN_RETURNED, actorId, comment);
        notifications.notifyPlannerApprovalResult(plan.createdBy(), "RETURNED", comment);
        return plans.save(plan);
    }

    private SeasonPlan requireSubmitted(UUID id) {
        SeasonPlan plan = plans.findById(id).orElseThrow(() -> new NotFoundException("season plan not found: " + id));
        if (plan.status() != SeasonPlanStatus.SUBMITTED) {
            throw new DomainValidationException(new LinkedHashMap<>() {{
                put("status", List.of("only SUBMITTED plans can be reviewed"));
            }});
        }
        return plan;
    }
}
