package com.seasonplan.lifecycle.domain;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

public record RitualSession(UUID id, UUID seasonPlanId, int attempt, String starter, Instant startedAt,
                            Instant completedAt, List<Participant> participants, List<Event> events,
                            Result result, String failureCode, String failureMessage) {
    public enum Result { SUCCESS, FAILED }
    public record Participant(String fairyId, String role, Instant confirmedAt) {
        public Participant {
            requireText(fairyId); requireText(role); Objects.requireNonNull(confirmedAt);
        }
    }
    public record Event(Instant occurredAt, String type, String payload) {
        public Event { Objects.requireNonNull(occurredAt); requireText(type); Objects.requireNonNull(payload); }
    }
    public RitualSession {
        Objects.requireNonNull(id); Objects.requireNonNull(seasonPlanId); Objects.requireNonNull(startedAt); requireText(starter);
        if (attempt < 1) throw new IllegalArgumentException("attempt must be positive");
        participants = List.copyOf(participants); events = List.copyOf(events);
        if (participants.isEmpty()) throw new IllegalArgumentException("participants are required");
        if ((result == null) != (completedAt == null)) throw new IllegalArgumentException("result requires completion time");
        if (completedAt != null && (completedAt.isBefore(startedAt)
                || (!events.isEmpty() && completedAt.isBefore(events.get(events.size() - 1).occurredAt())))) {
            throw new IllegalArgumentException("completion precedes ritual events");
        }
        if (result == Result.FAILED) { requireText(failureCode); requireText(failureMessage); }
        else if (failureCode != null || failureMessage != null) throw new IllegalArgumentException("only failed sessions have failure details");
    }
    public static RitualSession start(UUID id, UUID planId, int attempt, String actor, Instant now, List<Participant> participants) {
        return new RitualSession(id, planId, attempt, actor, now, null, participants, List.of(), null, null, null);
    }
    public boolean inProgress() { return result == null; }
    public RitualSession addEvent(Event event) {
        requireInProgress();
        Instant previous = events.isEmpty() ? startedAt : events.get(events.size() - 1).occurredAt();
        if (event.occurredAt().isBefore(previous)) throw new IllegalArgumentException("ritual events must be ordered");
        List<Event> updated = new ArrayList<>(events); updated.add(event);
        return new RitualSession(id, seasonPlanId, attempt, starter, startedAt, null, participants, updated, null, null, null);
    }
    public RitualSession succeed(Instant now) { return finish(Result.SUCCESS, null, null, now); }
    public RitualSession fail(String code, String message, Instant now) { return finish(Result.FAILED, code, message, now); }
    private RitualSession finish(Result result, String code, String message, Instant now) {
        requireInProgress();
        return new RitualSession(id, seasonPlanId, attempt, starter, startedAt, now, participants, events, result, code, message);
    }
    private void requireInProgress() {
        if (!inProgress()) throw new IllegalArgumentException("completed ritual is immutable");
    }
    private static void requireText(String value) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException("non-blank value required");
    }
}
