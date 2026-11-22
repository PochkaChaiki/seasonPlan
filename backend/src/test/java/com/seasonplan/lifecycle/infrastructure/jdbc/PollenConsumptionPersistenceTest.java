package com.seasonplan.lifecycle.infrastructure.jdbc;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.seasonplan.audit.adapter.out.persistence.JdbcAuditLogRepository;
import com.seasonplan.audit.api.*;
import com.seasonplan.audit.application.AuditLogService;
import com.seasonplan.identity.api.ActorRole;
import com.seasonplan.notification.api.NotificationOutbox;
import com.seasonplan.notification.adapter.out.persistence.JdbcNotificationOutbox;
import com.seasonplan.pollen.adapter.out.fake.FakePollenTreeClient;
import com.seasonplan.pollen.adapter.out.persistence.*;
import com.seasonplan.pollen.api.*;
import com.seasonplan.pollen.application.*;
import com.seasonplan.pollen.application.port.*;
import com.seasonplan.pollen.domain.*;
import com.seasonplan.season.adapter.out.persistence.*;
import com.seasonplan.season.application.SeasonQueryService;
import com.seasonplan.season.domain.SeasonPlan;
import com.seasonplan.lifecycle.service.SeasonPlanTestFixture;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.*;
import org.springframework.transaction.support.*;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.*;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@Testcontainers
class PollenConsumptionPersistenceTest {
    @Container static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:14");
    static DriverManagerDataSource source;
    static JdbcTemplate jdbc;
    static final ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();
    JdbcSeasonPlanRepository plans;
    JdbcPollenIntentRepository intents;
    JdbcCommandTransaction commands;
    SeasonPlan plan;
    AuditTrail audit;
    NotificationOutbox outbox;
    FakePollenTreeClient provider;
    @BeforeAll static void migrate() throws Exception {
        source = new DriverManagerDataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
        jdbc = new JdbcTemplate(source);
        try (var connection = source.getConnection()) {
            new liquibase.Liquibase("db/changelog/db.changelog-master.yaml", new liquibase.resource.ClassLoaderResourceAccessor(),
                    new liquibase.database.jvm.JdbcConnection(connection)).update(new liquibase.Contexts(), new liquibase.LabelExpression());
        }
        jdbc.execute("create user consumption_runtime password 'runtime-test-password'");
        jdbc.execute("grant seasonplan_runtime to consumption_runtime");
        source = new DriverManagerDataSource(postgres.getJdbcUrl(), "consumption_runtime", "runtime-test-password");
        jdbc = new JdbcTemplate(source);
    }
    @BeforeEach void setup() {
        plans = new JdbcSeasonPlanRepository(jdbc, mapper);
        intents = new JdbcPollenIntentRepository(jdbc, mapper);
        commands = new JdbcCommandTransaction(jdbc, new DataSourceTransactionManager(source));
        var draft = SeasonPlanTestFixture.validPlanDraft("planner");
        plan = SeasonPlan.createDraft(UUID.randomUUID(), draft.seasonType(), draft.targetTerritories(), draft.timeWindows(),
                draft.naturalPhenomenonParameters(), draft.targetClimateMetrics(), List.of(), "planner", Instant.now());
        plan.pinExecutionRules(com.seasonplan.lifecycle.service.TestExecutionRules.snapshot());
        commands.create(() -> plans.save(plan));
        // Persistence fixture: an already ACTIVE legacy season. This does not create funding evidence.
        jdbc.update("update season_plans set status = 'ACTIVE' where id = ?", plan.id());
        audit = new AuditLogService(new JdbcAuditLogRepository(jdbc), Clock.systemUTC());
        outbox = new JdbcNotificationOutbox(jdbc, mapper);
        provider = spy(new FakePollenTreeClient(new BigDecimal("5000")));
        var r = provider.requestReservation(plan.id(), UUID.randomUUID(), 100, "original");
        provider.confirmReservation(r.id(), 100, "ok", "original");
        clearInvocations(provider);
    }
    PollenOperations owner(PollenTreeClient client, AuditTrail trail, NotificationOutbox notifications) {
        return new PollenOperationsFacade(new PollenService(new SeasonQueryService(new JdbcSeasonPlanRepository(jdbc, mapper)),
                client, () -> notifications, new JdbcCommandTransaction(jdbc, new DataSourceTransactionManager(source)), trail,
                Clock.systemUTC(), new JdbcPollenIntentRepository(jdbc, mapper), new SpringRemoteCommandBoundary()));
    }
    PollenOperations owner() { return owner(provider, audit, outbox); }
    PollenUsage spend(PollenOperations service, UUID key, String grams) {
        return service.recordConsumption(plan.id(), key, new BigDecimal(grams), ActorRole.POLLEN_GUARDIAN, "original");
    }
    @Test void committedIntentPrecedesUnlockedMutationAndAmbientEntryDoesNotTouchProvider() {
        doAnswer(call -> {
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
            var id = intents.unresolvedConsumption(plan.id()).orElseThrow();
            assertThat(intents.findConsumption(id).orElseThrow().expectedReservationId()).isNotNull();
            try (var connection = source.getConnection(); var statement = connection.createStatement()) {
                connection.setAutoCommit(false); statement.execute("set local lock_timeout = '500ms'");
                statement.execute("select id from season_plans where id = '" + plan.id() + "' for update");
                connection.rollback();
            }
            return call.callRealMethod();
        }).when(provider).recordConsumption(any(), any(), any());
        spend(owner(), UUID.randomUUID(), "80"); clearInvocations(provider);
        new TransactionTemplate(new DataSourceTransactionManager(source)).execute(status -> {
            assertThatThrownBy(() -> spend(owner(), UUID.randomUUID(), "1")).isInstanceOf(IllegalStateException.class); return null;
        });
        verifyNoInteractions(provider);
    }
    @ParameterizedTest @ValueSource(booleans = {false, true})
    void committedReceiptSurvivesActualAuditOrOutboxRollback(boolean failAudit) {
        var brokenAudit = mock(AuditTrail.class);
        when(brokenAudit.appendOnce(any(), any(), any(), any(), any())).thenAnswer(call -> {
            audit.appendOnce(call.getArgument(0), call.getArgument(1), call.getArgument(2), call.getArgument(3), call.getArgument(4));
            throw new IllegalStateException("audit failure");
        });
        NotificationOutbox brokenOutbox = event -> { outbox.enqueue(event); throw new IllegalStateException("outbox failure"); };
        var key = UUID.randomUUID();
        assertThatThrownBy(() -> spend(owner(provider, failAudit ? brokenAudit : audit, failAudit ? outbox : brokenOutbox), key, "80"))
                .isInstanceOf(IllegalStateException.class);
        var operation = intents.unresolvedConsumption(plan.id()).orElseThrow();
        assertThat(intents.findConsumption(operation).orElseThrow().state()).isEqualTo(ConsumptionIntent.State.RECEIVED);
        assertThat(audit.entriesFor(plan.id())).isEmpty();
        assertThat(jdbc.queryForObject("select count(*) from notification_outbox where season_plan_id = ?", Integer.class, plan.id())).isZero();
        assertThat(spend(owner(), key, "80").actualGrams()).isEqualByComparingTo("80");
        verify(provider, times(1)).recordConsumption(any(), any(), any());
        assertThat(audit.entriesFor(plan.id())).hasSize(1);
        assertThat(jdbc.queryForObject("select count(*) from notification_outbox where season_plan_id = ?", Integer.class, plan.id())).isEqualTo(1);
        assertThat(intents.unresolvedConsumption(plan.id())).isEmpty();
    }
    @Test void timeoutReconcilesAfterRestartByOperationIdAndOriginalSnapshotSurvivesLaterEvents() {
        var lost = new AtomicBoolean(true);
        doAnswer(call -> { var result = call.callRealMethod(); if (lost.getAndSet(false)) throw new IllegalStateException("lost response"); return result; })
                .when(provider).recordConsumption(any(), any(), any());
        var key = UUID.randomUUID();
        assertThatThrownBy(() -> spend(owner(), key, "80")).isInstanceOf(PollenCommandPendingException.class);
        var operation = intents.unresolvedConsumption(plan.id()).orElseThrow();
        assertThat(owner().reconcileConsumption(operation, ActorRole.POLLEN_GUARDIAN, "recovery-guardian").actualGrams()).isEqualByComparingTo("80");
        assertThat(audit.entriesFor(plan.id()).get(0).actorId()).isEqualTo("original");
        spend(owner(), UUID.randomUUID(), "20");
        assertThat(spend(owner(), key, "80").actualGrams()).isEqualByComparingTo("80");
        verify(provider, times(2)).recordConsumption(any(), any(), any());
        assertThatThrownBy(() -> spend(owner(), key, "81")).isInstanceOf(com.seasonplan.shared.domain.DomainValidationException.class);
    }
    @Test void unknownOutcomeBlocksOtherSpendsReservationsAndReportUsageWithoutRedispatch() {
        var unavailable = new AtomicBoolean(false);
        doAnswer(call -> unavailable.get() ? new PollenTreeClient.EventLookup(PollenTreeClient.EventLookupStatus.UNKNOWN, null) : call.callRealMethod())
                .when(provider).lookupConsumption(any(), any());
        doAnswer(call -> { call.callRealMethod(); unavailable.set(true); throw new IllegalStateException("lost"); })
                .when(provider).recordConsumption(any(), any(), any());
        var key = UUID.randomUUID();
        assertThatThrownBy(() -> spend(owner(), key, "80")).isInstanceOf(PollenCommandPendingException.class);
        assertThatThrownBy(() -> spend(owner(), key, "80")).isInstanceOf(PollenCommandPendingException.class);
        assertThatThrownBy(() -> spend(owner(), UUID.randomUUID(), "1")).isInstanceOf(PollenCommandPendingException.class);
        assertThatThrownBy(() -> owner().requestReservation(plan.id(), 100, ActorRole.POLLEN_GUARDIAN, "original")).isInstanceOf(PollenCommandPendingException.class);
        assertThatThrownBy(() -> owner().usage(plan.id())).isInstanceOf(PollenCommandPendingException.class);
        verify(provider, times(1)).recordConsumption(any(), any(), any()); verify(provider, never()).requestReservation(any(), any(), anyInt(), any());
    }
    @Test void acceptedButIneligibleReceiptIsRetainedAndQuarantined() {
        doAnswer(call -> { var accepted = call.callRealMethod(); jdbc.update("update season_plans set status = 'EMERGENCY' where id = ?", plan.id()); return accepted; })
                .when(provider).recordConsumption(any(), any(), any());
        assertThatThrownBy(() -> spend(owner(), UUID.randomUUID(), "80")).isInstanceOf(PollenCommandPendingException.class);
        var operation = intents.unresolvedConsumption(plan.id()).orElseThrow();
        var stored = new JdbcPollenIntentRepository(jdbc, mapper).findConsumption(operation).orElseThrow();
        assertThat(stored.state()).isEqualTo(ConsumptionIntent.State.QUARANTINED);
        assertThat(stored.receipt().actualGrams()).isEqualByComparingTo("80");
        assertThatThrownBy(() -> owner().reconcileConsumption(operation, ActorRole.POLLEN_GUARDIAN, "recovery")).isInstanceOf(PollenCommandPendingException.class);
        verify(provider, times(1)).recordConsumption(any(), any(), any());
    }
    @Test void concurrentRetryDispatchesOnlyOnce() throws Exception {
        var port = mock(PollenTreeClient.class); var entered = new CountDownLatch(1); var finish = new CountDownLatch(1);
        when(port.lookupConsumption(any(), any())).thenAnswer(call -> provider.lookupConsumption(call.getArgument(0), call.getArgument(1)));
        when(port.summary(any())).thenAnswer(call -> provider.summary(call.getArgument(0)));
        when(port.recordConsumption(any(), any(), any())).thenAnswer(call -> {
            entered.countDown(); if (!finish.await(10, TimeUnit.SECONDS)) throw new AssertionError("timeout");
            return provider.recordConsumption(call.getArgument(0), call.getArgument(1), call.getArgument(2));
        });
        var key = UUID.randomUUID(); var executor = Executors.newSingleThreadExecutor();
        try {
            var first = executor.submit(() -> spend(owner(port, audit, outbox), key, "80"));
            assertThat(entered.await(10, TimeUnit.SECONDS)).isTrue();
            assertThatThrownBy(() -> spend(owner(port, audit, outbox), key, "80")).isInstanceOf(PollenCommandPendingException.class);
            finish.countDown(); assertThat(first.get(10, TimeUnit.SECONDS).actualGrams()).isEqualByComparingTo("80");
            verify(port, times(1)).recordConsumption(any(), any(), any());
        } finally { finish.countDown(); executor.shutdownNow(); assertThat(executor.awaitTermination(10, TimeUnit.SECONDS)).isTrue(); }
    }
    @Test void runtimeCannotRewriteEvidenceAndMalformedInsertIsRejected() throws Exception {
        spend(owner(), UUID.randomUUID(), "80");
        try (var connection = source.getConnection(); var statement = connection.createStatement()) {
            for (String sql : List.of("update pollen_consumption_operations set intent = '{}'::jsonb",
                    "update pollen_consumption_operations set receipt = '{}'::jsonb", "delete from pollen_consumption_operations",
                    "truncate pollen_consumption_operations", "alter table pollen_consumption_operations disable trigger all")) {
                assertThatThrownBy(() -> statement.execute(sql)).isInstanceOfSatisfying(java.sql.SQLException.class,
                        error -> assertThat(error.getSQLState()).isEqualTo("42501"));
            }
        }
        assertThatThrownBy(() -> jdbc.update("insert into pollen_consumption_operations values (?,?,?,'{}'::jsonb,'UNKNOWN',null)",
                UUID.randomUUID(), plan.id(), plan.materialRevision())).isInstanceOf(org.springframework.dao.DataAccessException.class);
    }
}
