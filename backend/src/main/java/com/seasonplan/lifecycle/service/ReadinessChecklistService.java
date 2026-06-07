package com.seasonplan.lifecycle.service;

import com.seasonplan.lifecycle.domain.ActorRole;
import com.seasonplan.lifecycle.domain.AssignmentStatus;
import com.seasonplan.lifecycle.domain.ChecklistItem;
import com.seasonplan.lifecycle.domain.MagicTask;
import com.seasonplan.lifecycle.domain.MagicTaskStatus;
import com.seasonplan.lifecycle.domain.PollenReservationStatus;
import com.seasonplan.lifecycle.domain.SeasonPlan;

import java.time.Clock;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class ReadinessChecklistService {
    private final SeasonPlanRepository plans;
    private final PollenReservationRepository pollenReservations;
    private final MagicTaskRepository magicTasks;
    private final FairyAssignmentRepository fairyAssignments;
    private final Clock clock;

    public ReadinessChecklistService(
            SeasonPlanRepository plans,
            PollenReservationRepository pollenReservations,
            MagicTaskRepository magicTasks,
            FairyAssignmentRepository fairyAssignments,
            Clock clock
    ) {
        this.plans = plans;
        this.pollenReservations = pollenReservations;
        this.magicTasks = magicTasks;
        this.fairyAssignments = fairyAssignments;
        this.clock = clock;
    }

    public Map<String, List<String>> calculateChecklist(UUID seasonPlanId) {
        LinkedHashMap<String, List<String>> errors = new LinkedHashMap<>();
        SeasonPlan plan = plans.findById(seasonPlanId).orElseThrow(() -> new NotFoundException("season plan not found: " + seasonPlanId));
        if (pollenReservations.findLatestBySeasonPlanId(seasonPlanId)
                .filter(reservation -> reservation.status() == PollenReservationStatus.CONFIRMED)
                .isEmpty()) {
            ValidationSupport.add(errors, "pollenReservation", "confirmed pollen reservation is required");
        }
        List<MagicTask> tasks = magicTasks.findBySeasonPlanId(seasonPlanId);
        if (tasks.isEmpty()) {
            ValidationSupport.add(errors, "magicTasks", "at least one magic task is required");
        }
        for (MagicTask task : tasks) {
            if (task.status() != MagicTaskStatus.READY) {
                ValidationSupport.add(errors, "magicTasks", "all magic tasks must be READY");
            }
            var confirmedAssignments = fairyAssignments.findByMagicTaskId(task.id()).stream()
                    .filter(assignment -> assignment.status() == AssignmentStatus.CONFIRMED)
                    .toList();
            if (confirmedAssignments.isEmpty()) {
                ValidationSupport.add(errors, "assignments", "every task requires a confirmed fairy assignment");
            }
            boolean compatible = confirmedAssignments.stream().anyMatch(assignment -> task.requiredTalent().equals(assignment.talent()));
            if (!confirmedAssignments.isEmpty() && !compatible) {
                ValidationSupport.add(errors, "assignments", "assigned fairy talent must match required task talent");
            }
        }
        if (plan.readinessChecklist().isEmpty() || plan.readinessChecklist().stream().anyMatch(item -> !item.complete())) {
            ValidationSupport.add(errors, "readinessChecklist", "all readiness checklist items must be complete");
        }
        return errors;
    }

    public void assertReadyForRitual(UUID seasonPlanId) {
        LinkedHashMap<String, List<String>> errors = new LinkedHashMap<>(calculateChecklist(seasonPlanId));
        ValidationSupport.throwIfAny(errors);
    }

    public void completeItem(UUID seasonPlanId, String key, ActorRole actorRole, String actorId) {
        ValidationSupport.requireAnyRole(actorRole, ActorRole.TALENT_COORDINATOR, ActorRole.POLLEN_GUARDIAN);
        SeasonPlan plan = plans.findById(seasonPlanId).orElseThrow(() -> new NotFoundException("season plan not found: " + seasonPlanId));
        plan.completeReadinessChecklistItem(key, actorId, clock.instant());
        plans.save(plan);
    }
}
