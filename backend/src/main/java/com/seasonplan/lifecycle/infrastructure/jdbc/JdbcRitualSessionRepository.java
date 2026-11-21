package com.seasonplan.lifecycle.infrastructure.jdbc;

import com.seasonplan.lifecycle.domain.RitualSession;
import com.seasonplan.lifecycle.repository.RitualSessionRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

@Repository
public class JdbcRitualSessionRepository implements RitualSessionRepository {
    private final JdbcTemplate jdbc;
    public JdbcRitualSessionRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }
    @Override public Optional<RitualSession> find(UUID id) {
        return jdbc.query("select * from ritual_sessions where id = ?", this::map, id).stream().findFirst();
    }
    @Override public List<RitualSession> forPlan(UUID planId) {
        ExecutionJdbcSupport.requireCanonical(jdbc, "ritual_sessions", "season_plan_id", planId);
        return jdbc.query("select * from ritual_sessions where season_plan_id = ? order by attempt, id", this::map, planId);
    }
    @Override @Transactional public void save(RitualSession session) {
        ExecutionJdbcSupport.requireCanonical(jdbc, "ritual_sessions", "season_plan_id", session.seasonPlanId());
        jdbc.queryForObject("select id from season_plans where id = ? for update", UUID.class, session.seasonPlanId());
        List<UUID> existing = jdbc.query("select id from ritual_sessions where id = ? for update", (rs, row) -> rs.getObject("id", UUID.class), session.id());
        int inserted = 0;
        if (existing.isEmpty()) {
            inserted = jdbc.update("""
                    insert into ritual_sessions(id, season_plan_id, validation_status, attempt, starter, started_at, canonical)
                    values (?, ?, 'PENDING', ?, ?, ?, true) on conflict (id) do nothing
                    """, session.id(), session.seasonPlanId(), session.attempt(), session.starter(), ExecutionJdbcSupport.timestamp(session.startedAt()));
            jdbc.queryForObject("select id from ritual_sessions where id = ? for update", UUID.class, session.id());
        }
        if (inserted != 0) {
            for (int i = 0; i < session.participants().size(); i++) {
                RitualSession.Participant participant = session.participants().get(i);
                jdbc.update("insert into ritual_session_participants(session_id, position, fairy_id, role, confirmed_at) values (?, ?, ?, ?, ?)", session.id(), i, participant.fairyId(), participant.role(), ExecutionJdbcSupport.timestamp(participant.confirmedAt()));
            }
            jdbc.update("update ritual_sessions set participants_sealed = true where id = ?", session.id());
        }
        RitualSession current = find(session.id()).orElseThrow();
        if (!sameIdentity(current, session) || !isEventPrefix(current.events(), session.events())) throw new IllegalStateException("ritual identity, participants or event history conflict");
        if (!current.inProgress()) {
            if (current.result() != session.result() || !ExecutionJdbcSupport.sameTime(current.completedAt(), session.completedAt())
                    || !Objects.equals(current.failureCode(), session.failureCode()) || !Objects.equals(current.failureMessage(), session.failureMessage())
                    || current.events().size() != session.events().size()) throw new IllegalStateException("completed ritual is immutable");
            return;
        }
        for (int i = current.events().size(); i < session.events().size(); i++) {
            RitualSession.Event event = session.events().get(i);
            jdbc.update("insert into ritual_session_events(session_id, position, occurred_at, event_type, payload) values (?, ?, ?, ?, ?)", session.id(), i, ExecutionJdbcSupport.timestamp(event.occurredAt()), event.type(), event.payload());
        }
        if (!session.inProgress()) {
            jdbc.update("update ritual_sessions set result = ?, completed_at = ?, failure_code = ?, failure_message = ?, validation_status = ? where id = ?", session.result().name(), ExecutionJdbcSupport.timestamp(session.completedAt()), session.failureCode(), session.failureMessage(), session.result() == RitualSession.Result.SUCCESS ? "VALID" : "INVALID", session.id());
        }
    }
    private boolean sameIdentity(RitualSession a, RitualSession b) {
        if (!a.seasonPlanId().equals(b.seasonPlanId()) || a.attempt() != b.attempt() || !a.starter().equals(b.starter()) || !ExecutionJdbcSupport.sameTime(a.startedAt(), b.startedAt()) || a.participants().size() != b.participants().size()) return false;
        for (int i = 0; i < a.participants().size(); i++) {
            RitualSession.Participant x = a.participants().get(i), y = b.participants().get(i);
            if (!x.fairyId().equals(y.fairyId()) || !x.role().equals(y.role()) || !ExecutionJdbcSupport.sameTime(x.confirmedAt(), y.confirmedAt())) return false;
        }
        return true;
    }
    private boolean isEventPrefix(List<RitualSession.Event> existing, List<RitualSession.Event> supplied) {
        if (existing.size() > supplied.size()) return false;
        for (int i = 0; i < existing.size(); i++) {
            RitualSession.Event a = existing.get(i), b = supplied.get(i);
            if (!a.type().equals(b.type()) || !a.payload().equals(b.payload()) || !ExecutionJdbcSupport.sameTime(a.occurredAt(), b.occurredAt())) return false;
        }
        return true;
    }
    private RitualSession map(ResultSet rs, int row) throws SQLException {
        if (!rs.getBoolean("canonical")) throw new com.seasonplan.lifecycle.domain.LegacyExecutionMetadataException("ritual_sessions");
        UUID id = rs.getObject("id", UUID.class);
        List<RitualSession.Participant> participants = jdbc.query("select * from ritual_session_participants where session_id = ? order by position", (r, n) -> new RitualSession.Participant(r.getString("fairy_id"), r.getString("role"), ExecutionJdbcSupport.instant(r.getTimestamp("confirmed_at"))), id);
        List<RitualSession.Event> events = jdbc.query("select * from ritual_session_events where session_id = ? order by position", (r, n) -> new RitualSession.Event(ExecutionJdbcSupport.instant(r.getTimestamp("occurred_at")), r.getString("event_type"), r.getString("payload")), id);
        String result = rs.getString("result");
        return new RitualSession(id, rs.getObject("season_plan_id", UUID.class), rs.getInt("attempt"), rs.getString("starter"), ExecutionJdbcSupport.instant(rs.getTimestamp("started_at")), ExecutionJdbcSupport.instant(rs.getTimestamp("completed_at")), participants, events, result == null ? null : RitualSession.Result.valueOf(result), rs.getString("failure_code"), rs.getString("failure_message"));
    }
}
