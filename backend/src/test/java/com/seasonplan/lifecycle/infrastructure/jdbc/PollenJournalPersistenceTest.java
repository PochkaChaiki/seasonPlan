package com.seasonplan.lifecycle.infrastructure.jdbc;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.seasonplan.pollen.adapter.out.persistence.*;
import com.seasonplan.pollen.adapter.out.fake.FakePollenTreeClient;
import com.seasonplan.pollen.application.PollenService;
import com.seasonplan.pollen.application.port.*;
import com.seasonplan.pollen.domain.*;
import com.seasonplan.pollen.api.*;
import com.seasonplan.season.adapter.out.persistence.*;
import com.seasonplan.season.application.SeasonQueryService;
import com.seasonplan.season.domain.SeasonPlan;
import com.seasonplan.lifecycle.service.SeasonPlanTestFixture;
import com.seasonplan.identity.api.ActorRole;
import com.seasonplan.audit.api.AuditTrail;
import org.junit.jupiter.api.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.*;
import org.springframework.transaction.support.*;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@Testcontainers
class PollenJournalPersistenceTest {
    @Container static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:14");
    static DriverManagerDataSource source;
    static JdbcTemplate jdbc;
    static final ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();
    JdbcSeasonPlanRepository plans;
    JdbcPollenIntentRepository intents;
    JdbcCommandTransaction commands;
    SeasonPlan plan;
    AuditTrail audit;
    @BeforeAll static void migrate() throws Exception {
        source = new DriverManagerDataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
        jdbc = new JdbcTemplate(source);
        try (var connection = source.getConnection()) {
            new liquibase.Liquibase("db/changelog/db.changelog-master.yaml", new liquibase.resource.ClassLoaderResourceAccessor(), new liquibase.database.jvm.JdbcConnection(connection)).update(new liquibase.Contexts(), new liquibase.LabelExpression());
        }
        jdbc.execute("create user pollen_test_runtime password 'runtime-test-password'");
        jdbc.execute("grant seasonplan_runtime to pollen_test_runtime");
        source = new DriverManagerDataSource(postgres.getJdbcUrl(), "pollen_test_runtime", "runtime-test-password");
        jdbc = new JdbcTemplate(source);
    }
    @BeforeEach void setup() {
        plans = new JdbcSeasonPlanRepository(jdbc, mapper);
        intents = new JdbcPollenIntentRepository(jdbc, mapper);
        commands = new JdbcCommandTransaction(jdbc, new DataSourceTransactionManager(source));
        var draft = SeasonPlanTestFixture.validPlanDraft("planner");
        draft.setTargetTerritories(List.of("journal-fixture-" + UUID.randomUUID()));
        plan = SeasonPlan.createDraft(UUID.randomUUID(), draft.seasonType(), draft.targetTerritories(), draft.timeWindows(), draft.naturalPhenomenonParameters(), draft.targetClimateMetrics(), List.of(), "planner", Instant.now());
        plan.pinExecutionRules(com.seasonplan.lifecycle.service.TestExecutionRules.snapshot());
        commands.create(() -> plans.save(plan));
        audit = mock(AuditTrail.class);
    }
    PollenService service(PollenTreeClient client) { return new PollenService(new SeasonQueryService(new JdbcSeasonPlanRepository(jdbc, mapper)), client, () -> event -> {}, commands, audit, Clock.systemUTC(), new JdbcPollenIntentRepository(jdbc, mapper), new SpringRemoteCommandBoundary()); }
    void probe() {
        assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
        try (var connection = source.getConnection(); var statement = connection.createStatement()) {
            connection.setAutoCommit(false);
            statement.execute("set local lock_timeout = '500ms'");
            statement.execute("select id from season_plans where id = '" + plan.id() + "' for update");
            assertThat(jdbc.queryForObject("select count(*) from pollen_operations where season_plan_id = ?", Integer.class, plan.id())).isPositive();
            connection.rollback();
        } catch (Exception e) { throw new AssertionError(e); }
    }
    @Test void intentIsDurableAndEveryProviderMutationRunsWithoutSeasonLockOrTransaction() {
        var provider = spy(new FakePollenTreeClient(new java.math.BigDecimal("5000")));
        doAnswer(call -> { probe(); return call.callRealMethod(); }).when(provider).requestReservation(any(), any(), anyInt(), anyString());
        doAnswer(call -> { probe(); return call.callRealMethod(); }).when(provider).confirmReservation(any(), anyInt(), any(), anyString());
        doAnswer(call -> { probe(); return call.callRealMethod(); }).when(provider).rejectReservation(any(), anyString(), anyString());
        var owner = service(provider);
        var first = owner.requestReservation(plan.id(), UUID.randomUUID(), 100, ActorRole.POLLEN_GUARDIAN, "original");
        owner.confirmReservation(first.id(), 100, "ok", ActorRole.POLLEN_GUARDIAN, "original");
        var second = owner.requestReservation(plan.id(), UUID.randomUUID(), 100, ActorRole.POLLEN_GUARDIAN, "original");
        owner.rejectReservation(second.id(), "no", ActorRole.POLLEN_GUARDIAN, "original");
        assertThat(new JdbcPollenIntentRepository(jdbc, mapper).binding(first.id()).orElseThrow().materialRevision()).isEqualTo(plan.materialRevision());
        new TransactionTemplate(new DataSourceTransactionManager(source)).execute(status -> {
            assertThatThrownBy(() -> owner.requestReservation(plan.id(), UUID.randomUUID(), 50, ActorRole.POLLEN_GUARDIAN, "original")).isInstanceOf(IllegalStateException.class);
            return null;
        });
        verify(provider, times(2)).requestReservation(any(), any(), anyInt(), anyString());
    }
    @Test void acceptedTimeoutRestartsWithStableFreshProviderKeyAndNeverPostsAgain() {
        var provider = spy(new FakePollenTreeClient(new java.math.BigDecimal("5000")));
        UUID clientKey = UUID.randomUUID();
        var legacy = provider.requestReservation(plan.id(), clientKey, 100, "original");
        provider.confirmReservation(legacy.id(), 100, "old", "original");
        clearInvocations(provider);
        doAnswer(call -> { call.callRealMethod(); throw new PollenTreeUnavailableException("response lost"); }).when(provider).requestReservation(any(), any(), anyInt(), anyString());
        var owner = service(provider);
        UUID operation;
        try { owner.requestReservation(plan.id(), clientKey, 100, ActorRole.POLLEN_GUARDIAN, "original"); throw new AssertionError(); }
        catch (PollenCommandPendingException pending) { operation = pending.operationId(); }
        var stored = intents.find(operation).orElseThrow();
        assertThat(stored.payload().providerKey()).isNotEqualTo(clientKey);
        assertThatThrownBy(() -> service(provider).requestReservation(plan.id(), clientKey, 101, ActorRole.POLLEN_GUARDIAN, "original")).isInstanceOf(com.seasonplan.shared.domain.DomainValidationException.class);
        var recovered = service(provider).requestReservation(plan.id(), clientKey, 100, ActorRole.POLLEN_GUARDIAN, "original");
        assertThat(recovered.id()).isNotEqualTo(legacy.id());
        assertThat(recovered.status()).isEqualTo(PollenReservationStatus.PENDING);
        verify(provider, times(1)).requestReservation(any(), any(), anyInt(), anyString());
        assertThat(intents.find(operation).orElseThrow().payload()).isEqualTo(stored.payload());
    }
    @Test void knownReceiptSurvivesAuditFailureAndRestartRetriesOnlyLocalApplication() {
        var provider = spy(new FakePollenTreeClient(new java.math.BigDecimal("5000")));
        var reservation = service(provider).requestReservation(plan.id(), UUID.randomUUID(), 100, ActorRole.POLLEN_GUARDIAN, "original");
        var actualAudit = new com.seasonplan.audit.application.AuditLogService(new com.seasonplan.audit.adapter.out.persistence.JdbcAuditLogRepository(jdbc), Clock.systemUTC());
        when(audit.appendOnce(any(), any(), any(), any(), any())).thenAnswer(call -> {
            actualAudit.appendOnce(call.getArgument(0), call.getArgument(1), call.getArgument(2), call.getArgument(3), call.getArgument(4));
            throw new IllegalStateException("audit failed");
        });
        assertThatThrownBy(() -> service(provider).confirmReservation(reservation.id(), 100, "ok", ActorRole.POLLEN_GUARDIAN, "original")).hasMessage("audit failed");
        UUID operation = intents.unresolvedOperation(plan.id()).orElseThrow();
        assertThat(intents.find(operation).orElseThrow().state()).isEqualTo(PollenIntent.State.RECEIVED);
        assertThat(actualAudit.entriesFor(plan.id())).isEmpty();
        audit = actualAudit;
        assertThat(service(provider).confirmReservation(reservation.id(), 100, "ok", ActorRole.POLLEN_GUARDIAN, "original").status()).isEqualTo(PollenReservationStatus.CONFIRMED);
        verify(provider, times(1)).confirmReservation(any(), anyInt(), any(), anyString());
        assertThat(intents.hasUnresolved(plan.id())).isFalse();
        assertThat(actualAudit.entriesFor(plan.id())).hasSize(1);
    }
    @Test void bindingMustReferenceMatchingRequestPlanRevisionAndReceipt() {
        var provider = new FakePollenTreeClient(new java.math.BigDecimal("5000"));
        var reservation = service(provider).requestReservation(plan.id(), UUID.randomUUID(), 100, ActorRole.POLLEN_GUARDIAN, "original");
        var binding = intents.binding(reservation.id()).orElseThrow();
        assertThatThrownBy(() -> jdbc.update("insert into pollen_reservation_bindings values (?, ?, ?, ?)", UUID.randomUUID(), plan.id(), plan.materialRevision(), binding.requestOperationId())).isInstanceOf(org.springframework.dao.DataAccessException.class);
        var dispatched = new PollenIntent(UUID.randomUUID(), new PollenIntent.Payload(PollenIntent.Kind.REQUEST, plan.id(), plan.materialRevision(), UUID.randomUUID(), UUID.randomUUID(), null, 100, null, "original"), PollenIntent.State.DISPATCHED, null, null, Instant.now());
        commands.execute(plan.id(), () -> intents.insert(dispatched));
        assertThatThrownBy(() -> jdbc.update("insert into pollen_reservation_bindings values (?, ?, ?, ?)", UUID.randomUUID(), plan.id(), plan.materialRevision(), dispatched.id())).isInstanceOf(org.springframework.dao.DataAccessException.class);
    }
    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(booleans = {false, true})
    void operationIdRecoveryPreservesOriginalActorAfterClientKeyLoss(boolean reject) {
        var provider = spy(new FakePollenTreeClient(new java.math.BigDecimal("5000")));
        var reservation = service(provider).requestReservation(plan.id(), UUID.randomUUID(), 100, ActorRole.POLLEN_GUARDIAN, "original");
        if (reject) doAnswer(call -> { call.callRealMethod(); throw new PollenTreeUnavailableException("lost"); }).when(provider).rejectReservation(any(), any(), anyString());
        else doAnswer(call -> { call.callRealMethod(); throw new PollenTreeUnavailableException("lost"); }).when(provider).confirmReservation(any(), anyInt(), any(), anyString());
        UUID operation;
        try {
            if (reject) service(provider).rejectReservation(reservation.id(), "ok", ActorRole.POLLEN_GUARDIAN, "original");
            else service(provider).confirmReservation(reservation.id(), 100, "ok", ActorRole.POLLEN_GUARDIAN, "original");
            throw new AssertionError();
        }
        catch (PollenCommandPendingException pending) { operation = pending.operationId(); }
        var recovered = service(provider).reconcileOperation(operation, ActorRole.POLLEN_GUARDIAN, "recovery-guardian");
        assertThat(recovered.confirmedBy()).isEqualTo("original");
        verify(audit).appendOnce(any(), eq(plan.id()), eq(reject ? com.seasonplan.audit.api.AuditAction.POLLEN_RESERVATION_REJECTED : com.seasonplan.audit.api.AuditAction.POLLEN_RESERVATION_CONFIRMED), eq("original"), any());
        if (reject) verify(provider, times(1)).rejectReservation(any(), any(), anyString());
        else verify(provider, times(1)).confirmReservation(any(), anyInt(), any(), anyString());
    }
    @Test void concurrentDispatchOccursOnceAndAnotherUnresolvedIntentBlocksReplacement() throws Exception {
        var provider = mock(PollenTreeClient.class);
        var external = new FakePollenTreeClient(new java.math.BigDecimal("5000"));
        var entered = new CountDownLatch(1); var finish = new CountDownLatch(1);
        doAnswer(call -> { entered.countDown(); if (!finish.await(10, TimeUnit.SECONDS)) throw new AssertionError("timeout"); return external.requestReservation(call.getArgument(0), call.getArgument(1), call.getArgument(2), call.getArgument(3)); }).when(provider).requestReservation(any(), any(), anyInt(), anyString());
        UUID key = UUID.randomUUID();
        var executor = Executors.newSingleThreadExecutor();
        try {
            var dispatched = executor.submit(() -> service(provider).requestReservation(plan.id(), key, 100, ActorRole.POLLEN_GUARDIAN, "original"));
            assertThat(entered.await(10, TimeUnit.SECONDS)).isTrue();
            assertThatThrownBy(() -> service(provider).requestReservation(plan.id(), key, 100, ActorRole.POLLEN_GUARDIAN, "original")).isInstanceOf(PollenCommandPendingException.class);
            assertThatThrownBy(() -> service(provider).requestReservation(plan.id(), UUID.randomUUID(), 100, ActorRole.POLLEN_GUARDIAN, "original")).isInstanceOf(PollenCommandPendingException.class);
            finish.countDown(); assertThat(dispatched.get(10, TimeUnit.SECONDS).status()).isEqualTo(PollenReservationStatus.PENDING);
            verify(provider, times(1)).requestReservation(any(), any(), anyInt(), anyString());
        } finally { finish.countDown(); executor.shutdownNow(); assertThat(executor.awaitTermination(10, TimeUnit.SECONDS)).isTrue(); }
    }
    @Test void delayedObsoleteReceiptIsRetainedButNewRevisionNeedsFreshConfirmation() {
        var provider = spy(new FakePollenTreeClient(new java.math.BigDecimal("5000")));
        doAnswer(call -> {
            var accepted = call.callRealMethod();
            commands.execute(plan.id(), () -> {
                var changed = plans.findById(plan.id()).orElseThrow();
                var input = SeasonPlanTestFixture.validPlanDraft("planner"); input.setNaturalPhenomenonParameters(Map.of("bloom", "late"));
                changed.updateDraftFieldsFrom(input, "editor", Instant.now()); return plans.save(changed);
            });
            return accepted;
        }).when(provider).requestReservation(any(), any(), anyInt(), anyString());
        assertThatThrownBy(() -> service(provider).requestReservation(plan.id(), UUID.randomUUID(), 100, ActorRole.POLLEN_GUARDIAN, "original"))
                .isInstanceOf(com.seasonplan.shared.domain.DomainValidationException.class);
        var old = provider.findLatestBySeasonPlanId(plan.id()).orElseThrow();
        var binding = intents.binding(old.id()).orElseThrow();
        assertThat(intents.find(binding.requestOperationId()).orElseThrow().state()).isEqualTo(PollenIntent.State.OBSOLETE);
        assertThat(binding.materialRevision()).isNotEqualTo(new SeasonQueryService(plans).get(plan.id()).materialRevision());
        doCallRealMethod().when(provider).requestReservation(any(), any(), anyInt(), anyString());
        var fresh = service(provider).requestReservation(plan.id(), UUID.randomUUID(), 100, ActorRole.POLLEN_GUARDIAN, "original");
        service(provider).confirmReservation(fresh.id(), 100, "ok", ActorRole.POLLEN_GUARDIAN, "original");
        assertThat(new JdbcPollenIntentRepository(jdbc, mapper).binding(fresh.id()).orElseThrow().materialRevision()).isEqualTo(new SeasonQueryService(plans).get(plan.id()).materialRevision());
    }
    @Test void runtimeCannotRewriteImmutableEvidenceOrAlterTables() throws Exception {
        var provider = new FakePollenTreeClient(new java.math.BigDecimal("5000"));
        var reservation = service(provider).requestReservation(plan.id(), UUID.randomUUID(), 100, ActorRole.POLLEN_GUARDIAN, "original");
        var binding = intents.binding(reservation.id()).orElseThrow();
        try (var connection = source.getConnection(); var statement = connection.createStatement()) {
            statement.execute("set role seasonplan_runtime");
            try (var rows = statement.executeQuery("select current_user")) { rows.next(); assertThat(rows.getString(1)).isEqualTo("seasonplan_runtime"); }
            for (String sql : List.of(
                    "update pollen_operations set payload = '{}'::jsonb where id = '" + binding.requestOperationId() + "'",
                    "update pollen_operations set receipt = '{}'::jsonb where id = '" + binding.requestOperationId() + "'",
                    "update pollen_reservation_bindings set material_revision = 0",
                    "delete from pollen_operations", "truncate pollen_operations", "alter table pollen_operations disable trigger all")) {
                assertThatThrownBy(() -> statement.execute(sql)).isInstanceOf(java.sql.SQLException.class);
            }
        }
    }

    @Test void confirmedFundingCannotSurviveMaterialEditAndFreshConfirmationRestoresApprovalAfterReload() {
        audit = new com.seasonplan.audit.application.AuditLogService(new com.seasonplan.audit.adapter.out.persistence.JdbcAuditLogRepository(jdbc), Clock.systemUTC());
        var provider = new FakePollenTreeClient(new java.math.BigDecimal("5000"));
        var pollen = new com.seasonplan.pollen.application.PollenOperationsFacade(service(provider));
        var planning = new com.seasonplan.season.application.SeasonPlanService(plans,
                new com.seasonplan.workflows.application.PollenResourceEvidenceSource(pollen), audit, commands, event -> {}, input -> List.of(),
                Clock.systemUTC(), com.seasonplan.lifecycle.service.TestExecutionRules::snapshot, id -> List.of());
        planning.submitForApproval(plan.id(), ActorRole.FAIRY_PLANNER, "planner");
        var old = pollen.requestReservation(plan.id(), 100, ActorRole.POLLEN_GUARDIAN, "original");
        pollen.confirmReservation(old.id(), 100, "ok", ActorRole.POLLEN_GUARDIAN, "original");
        planning.verify(plan.id(), ActorRole.CHIEF_GUARDIAN, "chief");
        planning.returnForRevision(plan.id(), "change climate", ActorRole.CHIEF_GUARDIAN, "chief");
        var changed = SeasonPlanTestFixture.validPlanDraft("planner");
        changed.setTargetTerritories(plan.targetTerritories());
        changed.setTargetClimateMetrics(Map.of("temperature", 19.0, "humidity", 60.0, "pollen_density", 1.0));
        planning.updateDraft(plan.id(), changed, ActorRole.FAIRY_PLANNER, "planner");
        planning.submitForApproval(plan.id(), ActorRole.FAIRY_PLANNER, "planner");
        planning.verify(plan.id(), ActorRole.CHIEF_GUARDIAN, "chief");
        assertThatThrownBy(() -> planning.approve(plan.id(), "old funds", ActorRole.CHIEF_GUARDIAN, "chief")).isInstanceOf(com.seasonplan.shared.domain.DomainValidationException.class);
        var reloaded = new com.seasonplan.pollen.application.PollenOperationsFacade(service(provider));
        var currentPlan = new SeasonQueryService(new JdbcSeasonPlanRepository(jdbc, mapper)).get(plan.id());
        assertThat(new com.seasonplan.ritual.domain.RitualReadinessPolicy().evaluate(currentPlan, reloaded.findLatestBySeasonPlanId(plan.id()), reloaded.findAuthoritativeConfirmedBySeasonPlanId(plan.id()), List.of(), List.of()).blockers()).contains("pollen-confirmed");
        var fresh = reloaded.requestReservation(plan.id(), 100, ActorRole.POLLEN_GUARDIAN, "original");
        reloaded.confirmReservation(fresh.id(), 100, "current", ActorRole.POLLEN_GUARDIAN, "original");
        assertThat(new com.seasonplan.pollen.application.PollenOperationsFacade(service(provider)).get(fresh.id()).materialRevision()).isEqualTo(currentPlan.materialRevision());
        assertThat(new com.seasonplan.ritual.domain.RitualReadinessPolicy().evaluate(currentPlan, reloaded.findLatestBySeasonPlanId(plan.id()), reloaded.findAuthoritativeConfirmedBySeasonPlanId(plan.id()), List.of(), List.of()).blockers()).doesNotContain("pollen-confirmed");
        assertThat(planning.approve(plan.id(), "current funds", ActorRole.CHIEF_GUARDIAN, "chief").status()).isEqualTo(com.seasonplan.season.domain.SeasonPlanStatus.APPROVED);
    }

    @Test void consumptionCommitsBeforeUnlockedDispatchAndRecoversAfterAuditRollback() {
        jdbc.update("update season_plans set status = 'ACTIVE' where id = ?", plan.id());
        var provider = spy(new FakePollenTreeClient(new java.math.BigDecimal("5000")));
        var reservation = provider.requestReservation(plan.id(), UUID.randomUUID(), 100, "original");
        provider.confirmReservation(reservation.id(), 100, "ok", "original");
        doAnswer(call -> {
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
            assertThat(jdbc.queryForObject("select count(*) from pollen_consumption_operations where season_plan_id = ?", Integer.class, plan.id())).isEqualTo(1);
            try (var connection = source.getConnection(); var statement = connection.createStatement()) {
                connection.setAutoCommit(false);
                statement.execute("set local lock_timeout = '500ms'");
                statement.execute("select id from season_plans where id = '" + plan.id() + "' for update");
                connection.rollback();
            }
            return call.callRealMethod();
        }).when(provider).recordConsumption(any(), any(), any());
        var actualAudit = new com.seasonplan.audit.application.AuditLogService(new com.seasonplan.audit.adapter.out.persistence.JdbcAuditLogRepository(jdbc), Clock.systemUTC());
        when(audit.appendOnce(any(), any(), any(), any(), any())).thenAnswer(call -> {
            actualAudit.appendOnce(call.getArgument(0), call.getArgument(1), call.getArgument(2), call.getArgument(3), call.getArgument(4));
            throw new IllegalStateException("audit failed");
        });
        UUID key = UUID.randomUUID();
        assertThatThrownBy(() -> service(provider).recordConsumption(plan.id(), key, new java.math.BigDecimal("80"), ActorRole.POLLEN_GUARDIAN, "original")).hasMessage("audit failed");
        UUID operation = intents.unresolvedConsumption(plan.id()).orElseThrow();
        assertThat(intents.findConsumption(operation).orElseThrow().state()).isEqualTo(ConsumptionIntent.State.RECEIVED);
        assertThat(actualAudit.entriesFor(plan.id())).isEmpty();
        audit = actualAudit;
        assertThat(service(provider).reconcileConsumption(operation, ActorRole.POLLEN_GUARDIAN, "recoverer").actualGrams()).isEqualByComparingTo("80");
        verify(provider, times(1)).recordConsumption(any(), any(), any());
        new TransactionTemplate(new DataSourceTransactionManager(source)).execute(status -> {
            assertThatThrownBy(() -> service(provider).recordConsumption(plan.id(), UUID.randomUUID(), java.math.BigDecimal.ONE, ActorRole.POLLEN_GUARDIAN, "original")).isInstanceOf(IllegalStateException.class);
            return null;
        });
        verify(provider, times(1)).recordConsumption(any(), any(), any());
    }

    @Test void consumptionTimeoutReconcilesAfterRestartAndBlocksCrossKindReplacement() {
        jdbc.update("update season_plans set status = 'ACTIVE' where id = ?", plan.id());
        var provider = spy(new FakePollenTreeClient(new java.math.BigDecimal("5000")));
        var reservation = provider.requestReservation(plan.id(), UUID.randomUUID(), 100, "original");
        provider.confirmReservation(reservation.id(), 100, "ok", "original");
        doAnswer(call -> { call.callRealMethod(); throw new IllegalStateException("response lost"); }).when(provider).recordConsumption(any(), any(), any());
        UUID key = UUID.randomUUID();
        UUID operation;
        try { service(provider).recordConsumption(plan.id(), key, new java.math.BigDecimal("80"), ActorRole.POLLEN_GUARDIAN, "original"); throw new AssertionError(); }
        catch (PollenCommandPendingException pending) { operation = pending.operationId(); }
        assertThatThrownBy(() -> service(provider).requestReservation(plan.id(), UUID.randomUUID(), 50, ActorRole.POLLEN_GUARDIAN, "original")).isInstanceOf(PollenCommandPendingException.class);
        assertThatThrownBy(() -> service(provider).recordConsumption(plan.id(), UUID.randomUUID(), java.math.BigDecimal.ONE, ActorRole.POLLEN_GUARDIAN, "original")).isInstanceOf(PollenCommandPendingException.class);
        assertThatThrownBy(() -> service(provider).recordConsumption(plan.id(), key, java.math.BigDecimal.ONE, ActorRole.POLLEN_GUARDIAN, "original")).isInstanceOf(com.seasonplan.shared.domain.DomainValidationException.class);
        assertThatThrownBy(() -> service(provider).recordConsumption(plan.id(), key, new java.math.BigDecimal("80"), ActorRole.POLLEN_GUARDIAN, "other")).isInstanceOf(com.seasonplan.shared.domain.DomainValidationException.class);
        assertThat(service(provider).reconcileConsumption(operation, ActorRole.POLLEN_GUARDIAN, "recoverer").actualGrams()).isEqualByComparingTo("80");
        assertThat(intents.findConsumption(operation).orElseThrow().actorId()).isEqualTo("original");
        verify(provider, times(1)).recordConsumption(any(), any(), any());
        assertThatThrownBy(() -> jdbc.update("update pollen_consumption_operations set receipt = '{}'::jsonb where id = ?", operation)).isInstanceOf(org.springframework.dao.DataAccessException.class);
        assertThatThrownBy(() -> jdbc.execute("truncate pollen_consumption_operations")).isInstanceOf(org.springframework.dao.DataAccessException.class);
        assertThatThrownBy(() -> jdbc.update("delete from pollen_consumption_operations where id = ?", operation)).isInstanceOf(org.springframework.dao.DataAccessException.class);
        assertThatThrownBy(() -> jdbc.execute("alter table pollen_consumption_operations disable trigger all")).isInstanceOf(org.springframework.dao.DataAccessException.class);
    }

    @Test void schemaRejectsBindingWhoseReceiptNamesAnotherPlan() throws Exception {
        var id = UUID.randomUUID(); var reservation = UUID.randomUUID();
        var payload = new PollenIntent.Payload(PollenIntent.Kind.REQUEST, plan.id(), plan.materialRevision(), UUID.randomUUID(), UUID.randomUUID(), null, 100, null, "original");
        var wrong = new ReservationReceipt(reservation, UUID.randomUUID(), 100, null, PollenReservationStatus.PENDING, null, null);
        jdbc.update("insert into pollen_operations(id,season_plan_id,material_revision,payload,state,receipt,created_at) values (?,?,?,cast(? as jsonb),'RECEIVED',cast(? as jsonb),now())", id, plan.id(), plan.materialRevision(), mapper.writeValueAsString(payload), mapper.writeValueAsString(wrong));
        assertThatThrownBy(() -> jdbc.update("insert into pollen_reservation_bindings values (?,?,?,?)", reservation, plan.id(), plan.materialRevision(), id)).isInstanceOf(org.springframework.dao.DataAccessException.class);
    }


    FakePollenTreeClient consumptionProvider() {
        jdbc.update("update season_plans set status = 'ACTIVE' where id = ?", plan.id());
        var provider = spy(new FakePollenTreeClient(new java.math.BigDecimal("5000")));
        var r = provider.requestReservation(plan.id(), UUID.randomUUID(), 100, "original");
        provider.confirmReservation(r.id(), 100, "ok", "original");
        return provider;
    }
    @Test void consumptionIntentCommitsBeforeUnlockedDispatchAndRejectsAmbientTransaction() {
        var provider = consumptionProvider();
        doAnswer(call -> {
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
            assertThat(jdbc.queryForObject("select count(*) from pollen_consumption_operations where season_plan_id = ?", Integer.class, plan.id())).isEqualTo(1);
            try (var connection = source.getConnection(); var statement = connection.createStatement()) {
                connection.setAutoCommit(false);
                statement.execute("set local lock_timeout = '500ms'");
                statement.execute("select id from season_plans where id = '" + plan.id() + "' for update");
                connection.rollback();
            }
            return call.callRealMethod();
        }).when(provider).recordConsumption(any(), any(), any());
        service(provider).recordConsumption(plan.id(), UUID.randomUUID(), new java.math.BigDecimal("80"), ActorRole.POLLEN_GUARDIAN, "original");
        new TransactionTemplate(new DataSourceTransactionManager(source)).execute(status -> {
            assertThatThrownBy(() -> service(provider).recordConsumption(plan.id(), UUID.randomUUID(), java.math.BigDecimal.ONE, ActorRole.POLLEN_GUARDIAN, "original")).isInstanceOf(IllegalStateException.class);
            return null;
        });
        verify(provider, times(1)).recordConsumption(any(), any(), any());
    }
    @Test void consumptionTimeoutRecoversOriginalEventByOperationAfterRestartAndBlocksReplacement() {
        var provider = consumptionProvider();
        doAnswer(call -> { call.callRealMethod(); throw new IllegalStateException("response lost"); }).when(provider).recordConsumption(any(), any(), any());
        UUID key = UUID.randomUUID();
        UUID operation;
        try { service(provider).recordConsumption(plan.id(), key, new java.math.BigDecimal("80"), ActorRole.POLLEN_GUARDIAN, "original"); throw new AssertionError(); }
        catch (PollenCommandPendingException pending) { operation = pending.operationId(); }
        assertThatThrownBy(() -> service(provider).requestReservation(plan.id(), UUID.randomUUID(), 50, ActorRole.POLLEN_GUARDIAN, "original")).isInstanceOf(PollenCommandPendingException.class);
        assertThatThrownBy(() -> service(provider).recordConsumption(plan.id(), UUID.randomUUID(), java.math.BigDecimal.ONE, ActorRole.POLLEN_GUARDIAN, "original")).isInstanceOf(PollenCommandPendingException.class);
        assertThatThrownBy(() -> service(provider).recordConsumption(plan.id(), key, java.math.BigDecimal.ONE, ActorRole.POLLEN_GUARDIAN, "original")).isInstanceOf(com.seasonplan.shared.domain.DomainValidationException.class);
        assertThatThrownBy(() -> service(provider).recordConsumption(plan.id(), key, new java.math.BigDecimal("80"), ActorRole.POLLEN_GUARDIAN, "other")).isInstanceOf(com.seasonplan.shared.domain.DomainValidationException.class);
        assertThat(service(provider).reconcileConsumption(operation, ActorRole.POLLEN_GUARDIAN, "recoverer").actualGrams()).isEqualByComparingTo("80");
        assertThat(intents.findConsumption(operation).orElseThrow().actorId()).isEqualTo("original");
        verify(provider, times(1)).recordConsumption(any(), any(), any());
    }
    @Test void consumptionReceiptSurvivesActualAuditRollbackAndOriginalSnapshotSurvivesLaterEvents() {
        var provider = consumptionProvider();
        var actualAudit = new com.seasonplan.audit.application.AuditLogService(new com.seasonplan.audit.adapter.out.persistence.JdbcAuditLogRepository(jdbc), Clock.systemUTC());
        when(audit.appendOnce(any(), any(), any(), any(), any())).thenAnswer(call -> {
            actualAudit.appendOnce(call.getArgument(0), call.getArgument(1), call.getArgument(2), call.getArgument(3), call.getArgument(4));
            throw new IllegalStateException("audit failed");
        });
        UUID key = UUID.randomUUID();
        assertThatThrownBy(() -> service(provider).recordConsumption(plan.id(), key, new java.math.BigDecimal("80"), ActorRole.POLLEN_GUARDIAN, "original")).hasMessage("audit failed");
        UUID operation = intents.unresolvedConsumption(plan.id()).orElseThrow();
        assertThat(intents.findConsumption(operation).orElseThrow().state()).isEqualTo(ConsumptionIntent.State.RECEIVED);
        assertThat(actualAudit.entriesFor(plan.id())).isEmpty();
        audit = actualAudit;
        service(provider).reconcileConsumption(operation, ActorRole.POLLEN_GUARDIAN, "recoverer");
        service(provider).recordConsumption(plan.id(), UUID.randomUUID(), java.math.BigDecimal.ONE, ActorRole.POLLEN_GUARDIAN, "original");
        assertThat(service(provider).recordConsumption(plan.id(), key, new java.math.BigDecimal("80"), ActorRole.POLLEN_GUARDIAN, "original").actualGrams()).isEqualByComparingTo("80");
        verify(provider, times(2)).recordConsumption(any(), any(), any());
        assertThatThrownBy(() -> jdbc.update("update pollen_consumption_operations set receipt = '{}'::jsonb where id = ?", operation)).isInstanceOf(org.springframework.dao.DataAccessException.class);
        assertThatThrownBy(() -> jdbc.execute("truncate pollen_consumption_operations")).isInstanceOf(org.springframework.dao.DataAccessException.class);
        assertThatThrownBy(() -> jdbc.update("delete from pollen_consumption_operations where id = ?", operation)).isInstanceOf(org.springframework.dao.DataAccessException.class);
        assertThatThrownBy(() -> jdbc.execute("alter table pollen_consumption_operations disable trigger all")).isInstanceOf(org.springframework.dao.DataAccessException.class);
    }
    @Test void preJournalConsumptionReusesOldKeyAndAuditWithoutAnotherMutationOrInventedBinding() {
        var provider = consumptionProvider();
        UUID key = UUID.randomUUID();
        provider.recordConsumption(plan.id(), key, new java.math.BigDecimal("80"));
        var actualAudit = new com.seasonplan.audit.application.AuditLogService(new com.seasonplan.audit.adapter.out.persistence.JdbcAuditLogRepository(jdbc), Clock.systemUTC());
        UUID auditId = UUID.nameUUIDFromBytes(("pollen-consumption:" + plan.id() + ":" + key).getBytes(java.nio.charset.StandardCharsets.UTF_8));
        actualAudit.appendOnce(auditId, plan.id(), com.seasonplan.audit.api.AuditAction.POLLEN_CONSUMPTION_RECORDED, "original", key.toString());
        var old = actualAudit.entriesFor(plan.id()).get(0); // Compare the actual stored PostgreSQL timestamp.
        audit = actualAudit;
        assertThat(service(provider).recordConsumption(plan.id(), key, new java.math.BigDecimal("80"), ActorRole.POLLEN_GUARDIAN, "original").actualGrams()).isEqualByComparingTo("80");
        assertThat(actualAudit.entriesFor(plan.id())).containsExactly(old);
        assertThatThrownBy(() -> service(provider).recordConsumption(plan.id(), key, java.math.BigDecimal.ONE, ActorRole.POLLEN_GUARDIAN, "original")).isInstanceOf(com.seasonplan.shared.domain.DomainValidationException.class);
        assertThat(jdbc.queryForObject("select count(*) from pollen_reservation_bindings where season_plan_id = ?", Integer.class, plan.id())).isZero();
        verify(provider, times(1)).recordConsumption(any(), any(), any());
    }
}
