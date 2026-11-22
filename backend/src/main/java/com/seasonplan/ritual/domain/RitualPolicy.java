package com.seasonplan.ritual.domain;

import com.seasonplan.ritual.api.RitualReadinessChecker.Readiness;
import com.seasonplan.season.domain.SeasonPlanStatus;
import com.seasonplan.shared.domain.DomainValidationException;
import java.time.Instant;
import java.util.*;

/** Launch and completion decisions. No persistence, provider access or framework dependencies. */
public final class RitualPolicy {
    private RitualPolicy() { }
    public record Failure(String code, String message) { }
    public static void requireStartStatus(SeasonPlanStatus status) {
        if (status != SeasonPlanStatus.RITUAL_READY) reject("status", "operation is not allowed in " + status);
    }

    public static void requireNoRunningOrSuccessfulAttempt(List<RitualSession> previous) {
        if (previous.stream().anyMatch(session -> session.inProgress() || session.result() == RitualSession.Result.SUCCESS)) reject("ritualSession", "an attempt is already running or successful");
    }
    public static RitualSession start(UUID id, UUID planId, List<RitualSession> previous, Readiness check, boolean critical, String actor, Instant now) {
        requireNoRunningOrSuccessfulAttempt(previous);
        if (!check.blockers().isEmpty() || critical) reject("readiness", "ritual readiness failed: " + check.blockers());
        int attempt = previous.stream().mapToInt(RitualSession::attempt).max().orElse(0) + 1;
        return RitualSession.start(id, planId, attempt, actor, now, check.participants());
    }
    public static void requireDiagnosticStatus(SeasonPlanStatus status) {
        if (status != SeasonPlanStatus.RITUAL_READY && status != SeasonPlanStatus.EMERGENCY) reject("status", "operation is not allowed in " + status);
    }
    public static void validateFailure(String code, String message) {
        if ((code == null) != (message == null)) throw new IllegalArgumentException("failure code and message must be supplied together");
    }
    public static boolean needsReadiness(String failureCode) { return failureCode == null; }
    public static void requireFinishable(SeasonPlanStatus status, String failureCode) {
        requireDiagnosticStatus(status);
        if (status == SeasonPlanStatus.EMERGENCY && failureCode == null) reject("status", "successful ritual completion is prohibited in EMERGENCY");
    }
    public static RitualSession finish(RitualSession session, SeasonPlanStatus status, String code, String message,
                                        Readiness check, boolean critical, Instant now) {
        validateFailure(code, message);
        requireFinishable(status, code);
        if (code != null) return session.fail(code, message, now);
        Failure failure = completionFailure(session, check.blockers(), check.participants(), check.requiredEvents(), critical);
        return failure == null ? session.succeed(now) : session.fail(failure.code(), failure.message(), now);
    }
    public static Failure completionFailure(RitualSession session, List<String> blockers,
            List<RitualSession.Participant> participants, Collection<String> requiredEvents, boolean criticalIncident) {
        if (!blockers.isEmpty() || criticalIncident || !new HashSet<>(participants).equals(new HashSet<>(session.participants())))
            return new Failure("READINESS_BLOCKED", "Проверка готовности не пройдена");
        if (!session.events().stream().map(RitualSession.Event::type).toList().containsAll(requiredEvents))
            return new Failure("MISSING_EVENTS", "Не завершены обязательные события ритуала");
        return null;
    }
    private static void reject(String field, String message) { throw new DomainValidationException(Map.of(field, List.of(message))); }
}
