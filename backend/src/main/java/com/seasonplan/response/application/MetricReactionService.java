package com.seasonplan.response.application;

import com.seasonplan.audit.api.AuditAction;
import com.seasonplan.audit.api.AuditTrail;
import com.seasonplan.notification.api.NotificationOutbox;
import com.seasonplan.response.api.MetricReactions;
import com.seasonplan.response.application.port.CorrectionActionRepository;
import com.seasonplan.response.application.port.MagicIncidentRepository;
import com.seasonplan.response.domain.CorrectionAction;
import com.seasonplan.response.domain.MagicIncident;
import com.seasonplan.season.api.CommandTransaction;
import com.seasonplan.season.api.SeasonLifecycle;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.util.Set;
import java.util.UUID;

/** Owns response persistence and effects for immutable, domain-evaluated metric specifications. */
public final class MetricReactionService implements MetricReactions {
    private final MagicIncidentRepository incidents;
    private final CorrectionActionRepository actions;
    private final SeasonLifecycle lifecycle;
    private final CommandTransaction transactions;
    private final NotificationOutbox outbox;
    private final AuditTrail audit;
    private final Clock clock;

    public MetricReactionService(MagicIncidentRepository incidents, CorrectionActionRepository actions,
                                 SeasonLifecycle lifecycle, CommandTransaction transactions,
                                 NotificationOutbox outbox, AuditTrail audit, Clock clock) {
        this.incidents = incidents; this.actions = actions; this.lifecycle = lifecycle;
        this.transactions = transactions; this.outbox = outbox; this.audit = audit; this.clock = clock;
    }
    @Override public Result react(UUID planId, UUID metricId, Reaction specification, String actorId) {
        return transactions.execute(planId, () -> {
            UUID incidentId = null, correctionId = null;
            if (specification.hasIncident()) {
                // Stable owner identity permits retry without a second incident or a new persistence schema.
                incidentId = UUID.nameUUIDFromBytes(("metric-incident:" + planId + ":" + metricId).getBytes(StandardCharsets.UTF_8));
                if (incidents.find(incidentId).isEmpty()) {
                    var spec = specification.incident();
                    var incident = new MagicIncident(incidentId, planId, MagicIncident.Type.valueOf(spec.type().name()),
                            MagicIncident.Severity.valueOf(spec.severity().name()), MagicIncident.Status.OPEN,
                            spec.cause(), spec.consequences(), actorId, clock.instant());
                    incidents.save(incident);
                    if (incident.critical()) lifecycle.enterEmergency(planId, actorId);
                    audit.append(planId, AuditAction.MAGIC_INCIDENT_REGISTERED, actorId, incidentId.toString());
                    notify("critical-metric:" + metricId, planId, specification.criticalAlert());
                }
            }
            if (specification.hasCorrection()) {
                var spec = specification.correction();
                var existing = actions.byKey(spec.key());
                if (existing.isPresent()) correctionId = existing.get().id();
                else {
                    var action = CorrectionAction.open(UUID.randomUUID(), planId, incidentId, spec.key(), spec.description(), spec.owner(), spec.deadline());
                    actions.save(action);
                    correctionId = action.id();
                    audit.append(planId, AuditAction.CORRECTION_ACTION_CREATED, actorId, action.id().toString());
                    if (specification.correctionAlert() != null) notify("correction:" + action.id(), planId, specification.correctionAlert());
                }
            }
            return new Result(incidentId, correctionId);
        });
    }
    private void notify(String key, UUID planId, AlertSpec alert) {
        outbox.enqueue(new NotificationOutbox.Event(key, planId, alert.type(), alert.recipients(),
                Set.of(NotificationOutbox.Channel.IN_APP, NotificationOutbox.Channel.EMAIL), alert.message(), clock.instant()));
    }
}
