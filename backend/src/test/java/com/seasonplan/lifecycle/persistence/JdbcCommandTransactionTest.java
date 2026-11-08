package com.seasonplan.lifecycle.persistence;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.AbstractPlatformTransactionManager;
import org.springframework.transaction.support.DefaultTransactionStatus;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class JdbcCommandTransactionTest {
    @Test void locksPlanAndCommitsLocalWritesInOneTransaction() {
        var events = new ArrayList<String>();
        var jdbc = mock(JdbcTemplate.class);
        UUID id = UUID.randomUUID();
        when(jdbc.queryForObject(contains("for update"), eq(UUID.class), eq(id))).thenAnswer(call -> {
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isTrue();
            events.add("lock");
            return id;
        });
        var transaction = new JdbcCommandTransaction(jdbc, new RecordingManager(events));
        assertThat(transaction.execute(id, () -> { events.add("writes"); return "result"; })).isEqualTo("result");
        assertThat(events).containsExactly("begin", "lock", "writes", "commit");
        assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
    }

    @Test void writeFailureRollsBackAndPropagatesWithoutCommitting() {
        var events = new ArrayList<String>();
        var jdbc = mock(JdbcTemplate.class);
        UUID id = UUID.randomUUID();
        when(jdbc.queryForObject(anyString(), eq(UUID.class), eq(id))).thenReturn(id);
        var transaction = new JdbcCommandTransaction(jdbc, new RecordingManager(events));
        var failure = new IllegalStateException("audit failed");
        assertThatThrownBy(() -> transaction.execute(id, () -> { throw failure; })).isSameAs(failure);
        assertThat(events).containsExactly("begin", "rollback");
    }

    private static class RecordingManager extends AbstractPlatformTransactionManager {
        private final List<String> events;
        RecordingManager(List<String> events) { this.events = events; }
        @Override protected Object doGetTransaction() { return new Object(); }
        @Override protected void doBegin(Object transaction, org.springframework.transaction.TransactionDefinition definition) { events.add("begin"); }
        @Override protected void doCommit(DefaultTransactionStatus status) { events.add("commit"); }
        @Override protected void doRollback(DefaultTransactionStatus status) { events.add("rollback"); }
    }
}
