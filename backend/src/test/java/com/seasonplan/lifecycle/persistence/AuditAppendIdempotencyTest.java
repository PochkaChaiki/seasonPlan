package com.seasonplan.lifecycle.persistence;

import com.seasonplan.lifecycle.adapter.fake.InMemoryAuditLogRepository;
import com.seasonplan.lifecycle.domain.AuditAction;
import com.seasonplan.lifecycle.domain.AuditLogEntry;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class AuditAppendIdempotencyTest {
    private final AuditLogEntry original = new AuditLogEntry(UUID.randomUUID(), UUID.randomUUID(),
            AuditAction.POLLEN_RESERVATION_CONFIRMED, "guardian", Instant.parse("2026-01-01T00:00:00Z"), "ok");
    private final AuditLogEntry retry = new AuditLogEntry(original.id(), original.seasonPlanId(), original.action(),
            original.actorId(), original.createdAt().plusSeconds(10), original.comment());

    @Test void inMemoryReplayPreservesOriginalTimestampAndRejectsChangedEvent() {
        var repository = new InMemoryAuditLogRepository();
        repository.append(original);
        assertThat(repository.append(retry)).isEqualTo(original);
        assertThat(repository.entriesFor(original.seasonPlanId())).containsExactly(original);
        assertThatThrownBy(() -> repository.append(changed())).isInstanceOf(IllegalStateException.class);
        assertThat(repository.entriesFor(original.seasonPlanId())).containsExactly(original);
    }

    @Test void jdbcReplayUsesInsertOnlyConflictHandlingAndReturnsOriginalEvent() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        doReturn(List.of(original)).when(jdbc).query(anyString(), org.mockito.ArgumentMatchers.<RowMapper<AuditLogEntry>>any(), eq(original.id()));
        var repository = new JdbcAuditLogRepository(jdbc);
        assertThat(repository.append(retry)).isEqualTo(original);
        verify(jdbc).update(contains("on conflict (id) do nothing"), eq(retry.id()), eq(retry.seasonPlanId()),
                eq(retry.action().name()), eq(retry.actorId()), any(java.sql.Timestamp.class), eq(retry.comment()));
        assertThatThrownBy(() -> repository.append(changed())).isInstanceOf(IllegalStateException.class);
    }

    private AuditLogEntry changed() {
        return new AuditLogEntry(original.id(), original.seasonPlanId(), original.action(), "another-actor", original.createdAt(), "changed");
    }
}
