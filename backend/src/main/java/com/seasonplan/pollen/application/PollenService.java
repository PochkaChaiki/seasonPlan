package com.seasonplan.pollen.application;

import com.seasonplan.pollen.domain.*;
import com.seasonplan.pollen.domain.PollenIntent.*;
import com.seasonplan.pollen.api.PollenCommandPendingException;
import com.seasonplan.pollen.application.port.*;
import com.seasonplan.season.api.CommandTransaction;
import com.seasonplan.season.api.SeasonQueries;
import com.seasonplan.audit.api.*;
import com.seasonplan.identity.api.*;
import com.seasonplan.notification.api.NotificationOutbox;
import com.seasonplan.shared.application.NotFoundException;
import com.seasonplan.shared.domain.DomainValidationException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.function.Supplier;

/** Pollen owner. Durable reservation phases never hold a DB transaction across provider mutation. */
public class PollenService {
    private final SeasonQueries seasons;
    private final PollenTreeClient client;
    private final Supplier<NotificationOutbox> outbox;
    private final CommandTransaction transactions;
    private final AuditTrail audit;
    private final Clock clock;
    private final PollenIntentRepository intents;
    private final RemoteCommandBoundary boundary;

    public PollenService(SeasonQueries seasons, PollenTreeClient client, Supplier<NotificationOutbox> outbox,
                         CommandTransaction transactions, AuditTrail audit, Clock clock,
                         PollenIntentRepository intents, RemoteCommandBoundary boundary) {
        this.seasons = seasons; this.client = client; this.outbox = outbox; this.transactions = transactions;
        this.audit = audit; this.clock = clock; this.intents = Objects.requireNonNull(intents); this.boundary = Objects.requireNonNull(boundary);
    }

    public PollenReservation requestReservation(UUID planId, int amount, ActorRole role, String actorId) {
        return requestReservation(planId, null, amount, role, actorId);
    }
    public PollenReservation requestReservation(UUID planId, UUID requestId, int amount, ActorRole role, String actorId) {
        LifecycleAccessPolicy.require(role, LifecyclePermission.REQUEST_POLLEN);
        PollenConsumptionPolicy.requirePositive(amount, "requestedAmount");
        boundary.requireOutsideTransaction();
        return execute(prepare(Kind.REQUEST, planId, requestId, null, amount, null, actorId));
    }
    public PollenReservation confirmReservation(UUID id, int amount, String comment, ActorRole role, String actorId) {
        LifecycleAccessPolicy.require(role, LifecyclePermission.CONFIRM_POLLEN);
        PollenConsumptionPolicy.requirePositive(amount, "confirmedAmount");
        boundary.requireOutsideTransaction();
        return decision(Kind.CONFIRM, id, amount, comment, actorId);
    }
    public PollenReservation rejectReservation(UUID id, String comment, ActorRole role, String actorId) {
        LifecycleAccessPolicy.require(role, LifecyclePermission.REJECT_POLLEN);
        boundary.requireOutsideTransaction();
        return decision(Kind.REJECT, id, null, comment, actorId);
    }
    public PollenReservation reconcileOperation(UUID operationId, ActorRole role, String actorId) {
        LifecycleAccessPolicy.require(role, LifecyclePermission.REQUEST_POLLEN);
        boundary.requireOutsideTransaction();
        var intent = intents.find(operationId).orElseThrow(() -> new NotFoundException("pollen operation not found"));
        return execute(new PollenIntentRepository.Claim(false, intent));
    }
    private PollenReservation decision(Kind kind, UUID id, Integer amount, String comment, String actorId) {
        ReservationBinding binding = intents.binding(id).orElseGet(() -> {
            get(id); // Preserve missing-resource semantics. A GET must never invent a revision binding.
            throw new DomainValidationException(Map.of("pollenReservation", List.of("legacy or unbound reservation; request resources for the current revision")));
        });
        return execute(prepare(kind, binding.planId(), id, id, amount, comment, actorId));
    }

    private PollenIntentRepository.Claim prepare(Kind kind, UUID planId, UUID suppliedKey, UUID reservationId,
                                                Integer amount, String comment, String actorId) {
        return transactions.execute(planId, () -> {
            var plan = seasons.get(planId);
            UUID key = suppliedKey != null ? suppliedKey : eventId("legacy-request:" + planId + ":" + plan.materialRevision() + ":" + amount + ":" + actorId);
            UUID operationId = eventId("pollen-operation:" + kind + ":" + key);
            var existing = intents.find(operationId);
            var payload = new Payload(kind, planId, plan.materialRevision(), key, kind == Kind.REQUEST ? UUID.randomUUID() : null,
                    reservationId, amount, comment, actorId);
            if (existing.isPresent()) {
                existing.get().payload().requireSameInput(payload);
                return new PollenIntentRepository.Claim(false, existing.get());
            }
            PollenIntentPolicy.requireMutable(plan.status());
            if (kind != Kind.REQUEST) PollenIntentPolicy.requireBinding(intents.binding(reservationId).orElseThrow(), plan.materialRevision());
            requireSettled(planId);
            var intent = new PollenIntent(operationId, payload, State.DISPATCHED, null, null, clock.instant().truncatedTo(ChronoUnit.MICROS));
            var claim = intents.insert(intent);
            claim.value().payload().requireSameInput(payload);
            return claim;
        });
    }

    private PollenReservation execute(PollenIntentRepository.Claim claim) {
        PollenIntent intent = claim.value();
        if (claim.inserted()) {
            try {
                Payload p = intent.payload();
                PollenReservation result = switch (p.kind()) {
                    case REQUEST -> client.requestReservation(p.planId(), p.providerKey(), p.amount(), p.actorId());
                    case CONFIRM -> client.confirmReservation(p.reservationId(), p.amount(), p.comment(), p.actorId());
                    case REJECT -> client.rejectReservation(p.reservationId(), p.comment(), p.actorId());
                };
                intent = remember(intent, ReservationReceipt.copyOf(result));
            } catch (PollenProviderRejectedException denial) {
                PollenIntent rejected = intent.denied(denial.fieldErrors().keySet().stream().findFirst().orElse("pollenReservation"));
                transactions.execute(intent.payload().planId(), () -> { intents.update(rejected); return null; });
                throw denial;
            } catch (NotFoundException missing) {
                PollenIntent rejected = intent.denied("notFound");
                transactions.execute(intent.payload().planId(), () -> { intents.update(rejected); return null; });
                throw missing;
            } catch (RuntimeException uncertain) {
                markUnknown(intent);
                throw new PollenCommandPendingException(intent.id());
            }
        }
        // RECEIVED is committed before local audit/application. Audit failure cannot erase its receipt.
        for (;;) {
            switch (intent.nextStep()) {
                case RECONCILE -> {
                    Optional<PollenReservation> observed;
                    try { observed = reconcile(intent); }
                    catch (RuntimeException failure) { markUnknown(intent); throw new PollenCommandPendingException(intent.id()); }
                    if (observed.isEmpty()) { markUnknown(intent); throw new PollenCommandPendingException(intent.id()); }
                    try { intent = remember(intent, ReservationReceipt.copyOf(observed.get())); }
                    catch (RuntimeException failure) { markUnknown(intent); throw new PollenCommandPendingException(intent.id()); }
                }
                case APPLY -> intent = apply(intent);
                case RETURN -> {
                    PollenIntentPolicy.requireRevision(intent.payload().materialRevision(), seasons.get(intent.payload().planId()).materialRevision());
                    return intent.receipt().snapshot();
                }
                case OBSOLETE -> PollenIntentPolicy.obsolete();
                case DENIED -> {
                    if ("notFound".equals(intent.denialField())) throw new NotFoundException("Pollen Tree resource not found");
                    throw new PollenProviderRejectedException(Map.of(intent.denialField(), List.of("Pollen Tree rejected the operation")));
                }
            }
        }
    }
    private PollenIntent remember(PollenIntent original, ReservationReceipt receipt) {
        return transactions.execute(original.payload().planId(), () -> {
            var recorded = intents.find(original.id()).orElseThrow().received(receipt);
            intents.update(recorded);
            return recorded;
        });
    }
    private void markUnknown(PollenIntent original) {
        transactions.execute(original.payload().planId(), () -> {
            var current = intents.find(original.id()).orElseThrow();
            intents.update(current.uncertain());
            return null;
        });
    }
    private Optional<PollenReservation> reconcile(PollenIntent intent) {
        Payload p = intent.payload();
        if (p.kind() == Kind.REQUEST) return client.lookupRequest(p.providerKey());
        return client.findById(p.reservationId()).filter(value ->
                value.seasonPlanId().equals(p.planId()) && Objects.equals(value.comment(), p.comment()) && Objects.equals(value.confirmedBy(), p.actorId())
                && (p.kind() == Kind.CONFIRM && value.status() == PollenReservationStatus.CONFIRMED && Objects.equals(value.confirmedAmount(), p.amount())
                    || p.kind() == Kind.REJECT && value.status() == PollenReservationStatus.REJECTED));
    }
    private PollenIntent apply(PollenIntent original) {
        return transactions.execute(original.payload().planId(), () -> {
            var current = intents.find(original.id()).orElseThrow();
            if (current.nextStep() != Step.APPLY) return current;
            var plan = seasons.get(current.payload().planId());
            var applied = current.applied(plan.materialRevision(), PollenIntentPolicy.terminal(plan.status()));
            if (current.payload().kind() == Kind.REQUEST)
                intents.bind(new ReservationBinding(current.receipt().id(), current.payload().planId(), current.payload().materialRevision(), current.id()));
            current.auditAction().ifPresent(action -> audit.appendOnce(eventId("pollen-audit:" + current.id()), plan.id(), action,
                    current.payload().actorId(), "reservationId=" + current.receipt().id() + "; materialRevision=" + current.payload().materialRevision() + "; " + Objects.toString(current.payload().comment(), "")));
            intents.update(applied);
            return applied;
        });
    }

    /** Immutable binding metadata only; never derive a binding from current season state or a provider GET. */
    public Long bindingRevision(UUID reservationId, UUID planId) {
        return intents.binding(reservationId).filter(binding -> binding.planId().equals(planId)).map(ReservationBinding::materialRevision).orElse(null);
    }
    public boolean unresolved(UUID planId) { return intents.hasUnresolved(planId) || intents.unresolvedConsumption(planId).isPresent(); }
    public PollenReservation get(UUID id) { return client.findById(id).orElseThrow(() -> new NotFoundException("pollen reservation not found: " + id)); }
    public Optional<PollenReservation> findLatestBySeasonPlanId(UUID id) { return client.findLatestBySeasonPlanId(id); }
    public Optional<PollenReservation> findAuthoritativeConfirmedBySeasonPlanId(UUID id) { return client.findAuthoritativeConfirmedBySeasonPlanId(id); }
    public Optional<PollenUsage> usage(UUID id) { requireSettled(id); return client.summary(id).map(PollenTreeClient.Summary::snapshot); }
    public PollenCapacity capacity() { return client.capacity(); }

    public PollenUsage reconcileConsumption(UUID operationId, ActorRole role, String actorId) {
        LifecycleAccessPolicy.require(role, LifecyclePermission.RECORD_POLLEN_CONSUMPTION);
        boundary.requireOutsideTransaction();
        var notifications = Objects.requireNonNull(outbox.get(), "consumption outbox is required");
        return completeConsumption(intents.findConsumption(operationId).orElseThrow(() -> new NotFoundException("consumption operation not found")), notifications);
    }
    public PollenUsage recordConsumption(UUID planId, UUID clientEventId, BigDecimal grams, ActorRole role, String actorId) {
        LifecycleAccessPolicy.require(role, LifecyclePermission.RECORD_POLLEN_CONSUMPTION);
        Objects.requireNonNull(clientEventId);
        BigDecimal amount = PollenConsumptionPolicy.normalize(grams);
        boundary.requireOutsideTransaction();
        NotificationOutbox notifications = Objects.requireNonNull(outbox.get(), "consumption outbox is required");
        UUID id = eventId("pollen-consumption-operation:" + planId + ":" + clientEventId);
        var existing = intents.findConsumption(id);
        if (existing.isPresent()) {
            existing.get().requireSame(planId, amount, actorId);
            return completeConsumption(existing.get(), notifications);
        }
        // Historical keys must not be reinterpreted as fresh provider nonces. Unsupported lookup fails closed.
        var lookup = client.lookupConsumption(planId, clientEventId);
        var historical = lookup != null && lookup.status() == PollenTreeClient.EventLookupStatus.ACCEPTED ? lookup.receipt() : null;
        if (historical != null) historical.requireHistoricalAmount(planId, clientEventId, amount);
        var expected = historical != null ? historical.reservationId() : client.summary(planId).map(PollenTreeClient.Summary::reservationId).orElse(null);
        if (lookup != null && lookup.status() == PollenTreeClient.EventLookupStatus.ABSENT) PollenConsumptionPolicy.requireAgreement(expected);
        var claim = transactions.execute(planId, () -> {
            var raced = intents.findConsumption(id);
            if (raced.isPresent()) { raced.get().requireSame(planId, amount, actorId); return new PollenIntentRepository.ConsumptionClaim(false, raced.get()); }
            var plan = seasons.get(planId);
            PollenConsumptionPolicy.requireConsumptionState(plan.status());
            requireSettled(planId);
            var intent = new ConsumptionIntent(id, planId, plan.materialRevision(), clientEventId, amount, actorId, expected,
                    ConsumptionIntent.State.DISPATCHED, null, clock.instant().truncatedTo(ChronoUnit.MICROS));
            if (lookup == null || lookup.status() != PollenTreeClient.EventLookupStatus.ABSENT && lookup.status() != PollenTreeClient.EventLookupStatus.ACCEPTED) intent = intent.uncertain();
            else if (lookup.status() == PollenTreeClient.EventLookupStatus.ACCEPTED) intent = intent.received(lookup.receipt());
            return intents.insertConsumption(intent);
        });
        var intent = claim.value();
        if (claim.inserted() && intent.state() == ConsumptionIntent.State.DISPATCHED) {
            try {
                var summary = client.recordConsumption(planId, clientEventId, amount);
                intent = rememberConsumption(intent, new ConsumptionIntent.Receipt(planId, clientEventId, amount,
                        summary.reservationId(), summary.confirmedGrams(), summary.actualGrams(), summary.version()));
            } catch (PollenProviderRejectedException denial) {
                final var dispatched = intent;
                transactions.execute(planId, () -> {
                    intents.updateConsumption(intents.findConsumption(dispatched.id()).orElseThrow().denied()); return null;
                });
                throw denial;
            } catch (RuntimeException uncertain) {
                unknownConsumption(intent);
                throw new PollenCommandPendingException(id);
            }
        }
        return completeConsumption(intent, notifications);
    }
    private void requireSettled(UUID planId) {
        PollenIntentPolicy.requireSettled(intents.unresolvedOperation(planId));
        PollenIntentPolicy.requireSettled(intents.unresolvedConsumption(planId));
    }
    private ConsumptionIntent rememberConsumption(ConsumptionIntent original, ConsumptionIntent.Receipt receipt) {
        return transactions.execute(original.planId(), () -> {
            var known = intents.findConsumption(original.id()).orElseThrow().received(receipt);
            intents.updateConsumption(known);
            return known;
        });
    }
    private void unknownConsumption(ConsumptionIntent original) {
        transactions.execute(original.planId(), () -> {
            intents.updateConsumption(intents.findConsumption(original.id()).orElseThrow().uncertain()); return null;
        });
    }
    private PollenUsage completeConsumption(ConsumptionIntent original, NotificationOutbox notifications) {
        var intent = original;
        if (intent.state() == ConsumptionIntent.State.DENIED)
            throw new PollenProviderRejectedException(Map.of("grams", List.of("Pollen Tree rejected the operation")));
        if (intent.receipt() == null) {
            PollenTreeClient.EventLookup lookup;
            try { lookup = client.lookupConsumption(intent.planId(), intent.clientKey()); }
            catch (RuntimeException uncertain) { unknownConsumption(intent); throw new PollenCommandPendingException(intent.id()); }
            if (lookup == null || lookup.status() != PollenTreeClient.EventLookupStatus.ACCEPTED) {
                unknownConsumption(intent); throw new PollenCommandPendingException(intent.id());
            }
            intent = rememberConsumption(intent, lookup.receipt());
        }
        final var known = intent;
        var applied = transactions.execute(intent.planId(), () -> {
            var current = intents.findConsumption(known.id()).orElseThrow();
            if (current.state() != ConsumptionIntent.State.RECEIVED) return current;
            var plan = seasons.get(current.planId());
            var result = current.apply(plan.materialRevision(), PollenConsumptionPolicy.eligible(plan.status()));
            if (result.state() == ConsumptionIntent.State.APPLIED) {
                var r = current.receipt();
                var summary = new PollenTreeClient.Summary(r.reservationId(), r.confirmedGrams(), r.actualGrams(), r.version());
                for (var warning : PollenConsumptionPolicy.warnings(r.snapshot())) notify(notifications, current.planId(), current.clientKey(), summary, warning);
                audit.appendOnce(eventId("pollen-consumption:" + current.planId() + ":" + current.clientKey()), current.planId(),
                        AuditAction.POLLEN_CONSUMPTION_RECORDED, current.actorId(), current.clientKey().toString());
            }
            intents.updateConsumption(result);
            return result;
        });
        if (applied.state() != ConsumptionIntent.State.APPLIED) throw new PollenCommandPendingException(applied.id());
        return applied.receipt().snapshot();
    }
    private UUID eventId(String key) { return UUID.nameUUIDFromBytes(key.getBytes(StandardCharsets.UTF_8)); }
    private void notify(NotificationOutbox notifications, UUID planId, UUID eventId, PollenTreeClient.Summary summary, PollenConsumptionPolicy.Warning warning) {
        notifications.enqueue(new NotificationOutbox.Event("pollen:" + summary.reservationId() + ":" + warning.threshold() + ":" + eventId,
                planId, "POLLEN_" + warning.threshold(), PollenConsumptionPolicy.recipients(warning), Set.of(NotificationOutbox.Channel.IN_APP, NotificationOutbox.Channel.EMAIL),
                "Расход пыльцы: " + summary.actualGrams() + " / " + summary.confirmedGrams() + " g", clock.instant()));
    }
}
