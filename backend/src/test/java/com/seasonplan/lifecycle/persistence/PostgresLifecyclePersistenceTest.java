package com.seasonplan.lifecycle.persistence;

import com.seasonplan.lifecycle.domain.ActorRole;
import com.seasonplan.lifecycle.domain.AssignmentStatus;
import com.seasonplan.lifecycle.domain.MagicTask;
import com.seasonplan.lifecycle.domain.PollenReservation;
import com.seasonplan.lifecycle.domain.SeasonPlan;
import com.seasonplan.lifecycle.domain.SeasonPlanStatus;
import com.seasonplan.lifecycle.application.preparation.FairyAssignmentService;
import com.seasonplan.lifecycle.application.preparation.MagicTaskService;
import com.seasonplan.lifecycle.application.pollen.PollenService;
import com.seasonplan.lifecycle.application.preparation.ReadinessChecklistService;
import com.seasonplan.lifecycle.application.port.SeasonPlanRepository;
import com.seasonplan.lifecycle.service.SeasonPlanService;
import com.seasonplan.lifecycle.service.SeasonPlanTestFixture;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import com.seasonplan.lifecycle.application.port.PollenTreeClient;
import com.seasonplan.lifecycle.domain.AuditLogEntry;
import com.seasonplan.lifecycle.domain.PollenReservationStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;

@SpringBootTest
@org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
@ActiveProfiles("test")
@Testcontainers
class PostgresLifecyclePersistenceTest {
    @Container
    static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:14")
            .withDatabaseName("seasonplan_test")
            .withUsername("seasonplan")
            .withPassword("seasonplan_test");

    @DynamicPropertySource
    static void postgresProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @Autowired
    private org.springframework.test.web.servlet.MockMvc mvc;

    @Test
    void reviewEndpointsReturn404ForMissingPlan() throws Exception {
        UUID missing = UUID.randomUUID();
        var chief = new com.seasonplan.security.LocalUserDetails(UUID.randomUUID(), "chief", "Chief", "{noop}unused", true, java.util.Set.of(ActorRole.CHIEF_GUARDIAN));
        for (String action : java.util.List.of("verify", "approve", "return")) {
            var request = org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/season-plans/{id}/" + action, missing)
                    .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user(chief))
                    .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf())
                    .contentType(org.springframework.http.MediaType.APPLICATION_JSON).content("{\"comment\":\"reason\"}");
            mvc.perform(request).andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isNotFound());
        }
    }

    @Autowired
    private SeasonPlanService seasonPlanService;

    @Autowired
    private PollenService pollenService;

    @Autowired
    private MagicTaskService magicTaskService;

    @Autowired
    private FairyAssignmentService fairyAssignmentService;

    @Autowired
    private ReadinessChecklistService readinessChecklistService;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private SeasonPlanRepository seasonPlanRepository;

    @Autowired
    private PollenTreeClient pollenTree;

    @SpyBean
    private JdbcAuditLogRepository auditRepository;

    @SpyBean
    private JdbcNotificationOutbox reviewOutbox;

    @Test
    void reviewOutboxFailureRollsBackPlanAndAuditAndRetryCommitsAll() {
        SeasonPlan plan = seasonPlanService.createDraft(uniquePlanDraft(),
                ActorRole.FAIRY_PLANNER, "planner-1");
        seasonPlanService.submitForApproval(plan.id(), ActorRole.FAIRY_PLANNER, "planner-1");
        var failure = new org.springframework.dao.DataAccessResourceFailureException("outbox failed");
        JdbcNotificationOutbox outboxTarget = org.springframework.test.util.AopTestUtils.getTargetObject(reviewOutbox);
        org.mockito.Mockito.doAnswer(invocation -> { invocation.callRealMethod(); throw failure; })
                .doCallRealMethod().when(outboxTarget).enqueue(any());
        assertThatThrownBy(() -> seasonPlanService.verify(plan.id(), ActorRole.CHIEF_GUARDIAN, "guardian-1"))
                .isSameAs(failure);
        assertThat(jdbc.queryForObject("select status from season_plans where id = ?", String.class, plan.id())).isEqualTo("SUBMITTED");
        assertThat(jdbc.queryForObject("select count(*) from audit_log_entries where season_plan_id = ? and action = 'SEASON_PLAN_VERIFIED'", Integer.class, plan.id())).isZero();
        assertThat(jdbc.queryForObject("select count(*) from notification_outbox where season_plan_id = ?", Integer.class, plan.id())).isZero();
        seasonPlanService.verify(plan.id(), ActorRole.CHIEF_GUARDIAN, "guardian-1");
        assertThat(jdbc.queryForObject("select status from season_plans where id = ?", String.class, plan.id())).isEqualTo("VERIFIED");
        assertThat(jdbc.queryForObject("select count(*) from audit_log_entries where season_plan_id = ? and action = 'SEASON_PLAN_VERIFIED'", Integer.class, plan.id())).isEqualTo(1);
        assertThat(jdbc.queryForObject("select count(*) from notification_outbox where season_plan_id = ?", Integer.class, plan.id())).isEqualTo(1);
        assertThat(jdbc.queryForObject("select event_type from notification_outbox where season_plan_id = ?", String.class, plan.id())).isEqualTo("SEASON_PLAN_VERIFIED");
        assertThat(jdbc.queryForObject("select recipients::text from notification_outbox where season_plan_id = ?", String.class, plan.id())).contains("planner-1");
        assertThat(jdbc.queryForObject("select channels::text from notification_outbox where season_plan_id = ?", String.class, plan.id())).contains("IN_APP", "EMAIL");
    }

    @Autowired
    private com.seasonplan.lifecycle.application.port.NotificationInboxRepository notificationInbox;

    @Test
    void inboxQueryIsIsolatedBoundedAndStableNewestFirst() {
        SeasonPlan plan = seasonPlanService.createDraft(uniquePlanDraft(), ActorRole.FAIRY_PLANNER, "planner-1");
        UUID owner = UUID.randomUUID();
        UUID other = UUID.randomUUID();
        java.time.Instant time = java.time.Instant.parse("2026-01-01T00:00:00Z");
        String prefix = UUID.randomUUID().toString();
        for (int i = 0; i < 52; i++) {
            String event = prefix + String.format("-%03d", i);
            var occurred = time.plusSeconds(i / 2);
            jdbc.update("insert into notification_outbox (event_id, season_plan_id, event_type, recipients, channels, message, occurred_at) values (?, ?, 'TEST', '[]'::jsonb, '[]'::jsonb, ?, ?)",
                    event, plan.id(), "message-" + i, java.sql.Timestamp.from(occurred));
            jdbc.update("insert into notification_inbox (event_id, recipient, season_plan_id, event_type, message, occurred_at) values (?, ?, ?, 'TEST', ?, ?)",
                    event, owner.toString(), plan.id(), "message-" + i, java.sql.Timestamp.from(occurred));
            if (i == 0) jdbc.update("insert into notification_inbox (event_id, recipient, season_plan_id, event_type, message, occurred_at) values (?, ?, ?, 'TEST', 'other', ?)",
                    event, other.toString(), plan.id(), java.sql.Timestamp.from(occurred));
        }
        var own = notificationInbox.findLatestByRecipient(owner);
        assertThat(own).hasSize(50);
        assertThat(own.get(0).eventId()).isEqualTo(prefix + "-051");
        assertThat(own.get(1).eventId()).isEqualTo(prefix + "-050");
        assertThat(own.get(0).occurredAt()).isAfter(own.get(2).occurredAt());
        assertThat(own.get(49).eventId()).isEqualTo(prefix + "-002");
        assertThat(own).extracting("message").doesNotContain("other");
        assertThat(notificationInbox.findLatestByRecipient(other)).extracting("message").containsExactly("other");
        assertThat(notificationInbox.findLatestByRecipient(UUID.randomUUID())).isEmpty();
    }

    @Autowired
    private JdbcNotificationDeliveryWorker deliveryWorker;

    @Test
    void postCommitDeliveryFailureDoesNotUndoDecisionAndRetriesOnlyFailedChannel() {
        SeasonPlan plan = seasonPlanService.createDraft(uniquePlanDraft(), ActorRole.FAIRY_PLANNER, "planner-1");
        seasonPlanService.submitForApproval(plan.id(), ActorRole.FAIRY_PLANNER, "planner-1");
        seasonPlanService.verify(plan.id(), ActorRole.CHIEF_GUARDIAN, "guardian-1");
        deliveryWorker.deliverPending();
        assertThat(jdbc.queryForObject("select status from season_plans where id = ?", String.class, plan.id())).isEqualTo("VERIFIED");
        assertThat(jdbc.queryForObject("select count(*) from notification_inbox i join notification_outbox o on o.event_id = i.event_id where o.season_plan_id = ?", Integer.class, plan.id())).isEqualTo(1);
        assertThat(jdbc.queryForObject("select attempts from notification_outbox_deliveries d join notification_outbox o on o.event_id = d.event_id where o.season_plan_id = ? and d.channel = 'EMAIL'", Integer.class, plan.id())).isEqualTo(1);
        assertThat(jdbc.queryForObject("select status from notification_outbox_deliveries d join notification_outbox o on o.event_id = d.event_id where o.season_plan_id = ? and d.channel = 'IN_APP'", String.class, plan.id())).isEqualTo("DELIVERED");
        deliveryWorker.deliverPending();
        assertThat(jdbc.queryForObject("select count(*) from notification_inbox i join notification_outbox o on o.event_id = i.event_id where o.season_plan_id = ?", Integer.class, plan.id())).isEqualTo(1);
        String eventId = jdbc.queryForObject("select event_id from notification_outbox where season_plan_id = ?", String.class, plan.id());
        var duplicate = new com.seasonplan.lifecycle.application.port.NotificationOutbox.Event(eventId, plan.id(), "SEASON_PLAN_VERIFIED", java.util.Set.of("planner-1"),
                java.util.Set.of(com.seasonplan.lifecycle.application.port.NotificationOutbox.Channel.IN_APP), "", java.time.Instant.now());
        new JdbcNotificationDeliveryGateway(jdbc, null).deliver(duplicate, "planner-1", com.seasonplan.lifecycle.application.port.NotificationOutbox.Channel.IN_APP);
        assertThat(jdbc.queryForObject("select count(*) from notification_inbox where event_id = ?", Integer.class, eventId)).isEqualTo(1);
    }

    @Test
    void invalidStaleAndUnauthorizedReviewCommandsLeaveNoEffects() {
        SeasonPlan plan = seasonPlanService.createDraft(uniquePlanDraft(), ActorRole.FAIRY_PLANNER, "planner-1");
        seasonPlanService.submitForApproval(plan.id(), ActorRole.FAIRY_PLANNER, "planner-1");
        assertThatThrownBy(() -> seasonPlanService.verify(plan.id(), ActorRole.FAIRY_PLANNER, "planner-1"))
                .isInstanceOf(com.seasonplan.lifecycle.application.shared.AccessDeniedForRoleException.class);
        assertThatThrownBy(() -> seasonPlanService.returnForRevision(plan.id(), " ", ActorRole.CHIEF_GUARDIAN, "guardian-1"))
                .isInstanceOf(com.seasonplan.lifecycle.domain.DomainValidationException.class);
        assertThatThrownBy(() -> seasonPlanService.approve(plan.id(), "stale", ActorRole.CHIEF_GUARDIAN, "guardian-1"))
                .isInstanceOf(com.seasonplan.lifecycle.domain.DomainValidationException.class);
        assertThat(jdbc.queryForObject("select status from season_plans where id = ?", String.class, plan.id())).isEqualTo("SUBMITTED");
        assertThat(jdbc.queryForObject("select count(*) from audit_log_entries where season_plan_id = ?", Integer.class, plan.id())).isEqualTo(2);
        assertThat(jdbc.queryForObject("select count(*) from notification_outbox where season_plan_id = ?", Integer.class, plan.id())).isZero();
    }

    @Test
    void auditFailureAfterInsertRollsBackReviewPlanAndAudit() {
        SeasonPlan plan = seasonPlanService.createDraft(uniquePlanDraft(), ActorRole.FAIRY_PLANNER, "planner-1");
        seasonPlanService.submitForApproval(plan.id(), ActorRole.FAIRY_PLANNER, "planner-1");
        var failure = new org.springframework.dao.DataAccessResourceFailureException("audit failed");
        org.mockito.Mockito.doAnswer(invocation -> { invocation.callRealMethod(); throw failure; })
                .doCallRealMethod().when(auditRepository).append(any());
        assertThatThrownBy(() -> seasonPlanService.verify(plan.id(), ActorRole.CHIEF_GUARDIAN, "guardian-1")).isSameAs(failure);
        assertThat(jdbc.queryForObject("select status from season_plans where id = ?", String.class, plan.id())).isEqualTo("SUBMITTED");
        assertThat(jdbc.queryForObject("select count(*) from audit_log_entries where season_plan_id = ? and action = 'SEASON_PLAN_VERIFIED'", Integer.class, plan.id())).isZero();
        assertThat(jdbc.queryForObject("select count(*) from notification_outbox where season_plan_id = ?", Integer.class, plan.id())).isZero();
    }

    @SpyBean
    private JdbcSeasonPlanRepository planStore;

    @Test
    void planSaveFailureRollsBackReviewAndProducesNoAuditOrOutbox() {
        SeasonPlan plan = seasonPlanService.createDraft(uniquePlanDraft(), ActorRole.FAIRY_PLANNER, "planner-1");
        seasonPlanService.submitForApproval(plan.id(), ActorRole.FAIRY_PLANNER, "planner-1");
        JdbcSeasonPlanRepository target = org.springframework.test.util.AopTestUtils.getTargetObject(planStore);
        var failure = new org.springframework.dao.DataAccessResourceFailureException("plan failed");
        org.mockito.Mockito.doAnswer(invocation -> { invocation.callRealMethod(); throw failure; })
                .doCallRealMethod().when(target).save(any());
        assertThatThrownBy(() -> seasonPlanService.verify(plan.id(), ActorRole.CHIEF_GUARDIAN, "guardian-1")).isSameAs(failure);
        assertThat(jdbc.queryForObject("select status from season_plans where id = ?", String.class, plan.id())).isEqualTo("SUBMITTED");
        assertThat(jdbc.queryForObject("select count(*) from audit_log_entries where season_plan_id = ? and action = 'SEASON_PLAN_VERIFIED'", Integer.class, plan.id())).isZero();
        assertThat(jdbc.queryForObject("select count(*) from notification_outbox where season_plan_id = ?", Integer.class, plan.id())).isZero();
    }

    @Autowired private org.springframework.transaction.PlatformTransactionManager transactionManager;
    @Autowired private com.fasterxml.jackson.databind.ObjectMapper objectMapper;

    @Test
    void deliveryRetriesExponentiallyAndExpiresAtTwentyFourHours() {
        SeasonPlan plan = seasonPlanService.createDraft(uniquePlanDraft(), ActorRole.FAIRY_PLANNER, "planner-1");
        seasonPlanService.submitForApproval(plan.id(), ActorRole.FAIRY_PLANNER, "planner-1");
        seasonPlanService.verify(plan.id(), ActorRole.CHIEF_GUARDIAN, "guardian-1");
        java.time.Instant occurred = jdbc.queryForObject("select occurred_at from notification_outbox where season_plan_id = ?", java.sql.Timestamp.class, plan.id()).toInstant();
        com.seasonplan.lifecycle.application.port.NotificationDeliveryGateway failing = (event, recipient, channel) -> { throw new IllegalStateException("offline"); };
        new JdbcNotificationDeliveryWorker(jdbc, objectMapper, failing, transactionManager, java.time.Clock.fixed(occurred, java.time.ZoneOffset.UTC)).deliverPending();
        Map<String, Object> first = jdbc.queryForMap("select d.attempts, d.next_attempt_at from notification_outbox_deliveries d join notification_outbox o on o.event_id = d.event_id where o.season_plan_id = ? and d.channel = 'EMAIL'", plan.id());
        assertThat(first.get("attempts")).isEqualTo(1);
        assertThat(((java.sql.Timestamp) first.get("next_attempt_at")).toInstant()).isEqualTo(occurred.plusSeconds(1));
        new JdbcNotificationDeliveryWorker(jdbc, objectMapper, failing, transactionManager, java.time.Clock.fixed(occurred.plusSeconds(1), java.time.ZoneOffset.UTC)).deliverPending();
        Map<String, Object> second = jdbc.queryForMap("select d.attempts, d.next_attempt_at from notification_outbox_deliveries d join notification_outbox o on o.event_id = d.event_id where o.season_plan_id = ? and d.channel = 'EMAIL'", plan.id());
        assertThat(second.get("attempts")).isEqualTo(2);
        assertThat(((java.sql.Timestamp) second.get("next_attempt_at")).toInstant()).isEqualTo(occurred.plusSeconds(3));
        new JdbcNotificationDeliveryWorker(jdbc, objectMapper, failing, transactionManager, java.time.Clock.fixed(occurred.plusSeconds(86400), java.time.ZoneOffset.UTC)).deliverPending();
        assertThat(jdbc.queryForObject("select d.status from notification_outbox_deliveries d join notification_outbox o on o.event_id = d.event_id where o.season_plan_id = ? and d.channel = 'EMAIL'", String.class, plan.id())).isEqualTo("EXPIRED");
    }

    @Test
    void duplicateEventIdCannotAddRecipientsOrChannels() {
        SeasonPlan plan = seasonPlanService.createDraft(uniquePlanDraft(), ActorRole.FAIRY_PLANNER, "planner-1");
        seasonPlanService.submitForApproval(plan.id(), ActorRole.FAIRY_PLANNER, "planner-1");
        seasonPlanService.verify(plan.id(), ActorRole.CHIEF_GUARDIAN, "guardian-1");
        String eventId = jdbc.queryForObject("select event_id from notification_outbox where season_plan_id = ?", String.class, plan.id());
        var event = new com.seasonplan.lifecycle.application.port.NotificationOutbox.Event(eventId, plan.id(), "forged", java.util.Set.of("other"),
                java.util.Set.of(com.seasonplan.lifecycle.application.port.NotificationOutbox.Channel.EMAIL), "other", java.time.Instant.now());
        new org.springframework.transaction.support.TransactionTemplate(transactionManager).executeWithoutResult(status -> reviewOutbox.enqueue(event));
        assertThat(jdbc.queryForObject("select count(*) from notification_outbox_deliveries where event_id = ?", Integer.class, eventId)).isEqualTo(2);
        assertThat(jdbc.queryForObject("select count(*) from notification_outbox_deliveries where event_id = ? and recipient = 'other'", Integer.class, eventId)).isZero();
    }

    @Test
    void committedReviewCreatesIndependentChannelDeliveryRecords() {
        SeasonPlan plan = seasonPlanService.createDraft(uniquePlanDraft(), ActorRole.FAIRY_PLANNER, "planner-1");
        seasonPlanService.submitForApproval(plan.id(), ActorRole.FAIRY_PLANNER, "planner-1");
        seasonPlanService.verify(plan.id(), ActorRole.CHIEF_GUARDIAN, "guardian-1");
        assertThat(jdbc.queryForObject("select count(*) from notification_outbox_deliveries d join notification_outbox o on o.event_id = d.event_id where o.season_plan_id = ?", Integer.class, plan.id())).isEqualTo(2);
    }

    @Test
    void checklistSaveSerializesWithConditionalApprovalAndCannotEraseIt() throws Exception {
        SeasonPlan plan = seasonPlanService.createDraft(uniquePlanDraft(), ActorRole.FAIRY_PLANNER, "planner-1");
        seasonPlanService.submitForApproval(plan.id(), ActorRole.FAIRY_PLANNER, "planner-1");
        var reservation = pollenService.requestReservation(plan.id(), 100, ActorRole.POLLEN_GUARDIAN, "pollen-1");
        pollenService.confirmReservation(reservation.id(), 100, "ok", ActorRole.POLLEN_GUARDIAN, "pollen-1");
        seasonPlanService.verify(plan.id(), ActorRole.CHIEF_GUARDIAN, "guardian-1");
        var condition = new com.seasonplan.lifecycle.domain.ApprovalCondition("weather-evidence", "Weather", "Calibration", ActorRole.TALENT_COORDINATOR);
        var read = new java.util.concurrent.CountDownLatch(1);
        var release = new java.util.concurrent.CountDownLatch(1);
        var approvalStarted = new java.util.concurrent.CountDownLatch(1);
        var target = org.springframework.test.util.AopTestUtils.<JdbcSeasonPlanRepository>getTargetObject(planStore);
        org.mockito.Mockito.doAnswer(invocation -> {
            Object result = invocation.callRealMethod();
            if (Thread.currentThread().getName().equals("checklist-reader")) {
                read.countDown();
                if (!release.await(10, java.util.concurrent.TimeUnit.SECONDS)) throw new IllegalStateException("test gate timed out");
            }
            return result;
        }).when(target).findById(org.mockito.ArgumentMatchers.eq(plan.id()));
        var executor = java.util.concurrent.Executors.newFixedThreadPool(2);
        try {
            var checklist = executor.submit(() -> {
                Thread.currentThread().setName("checklist-reader");
                readinessChecklistService.completeItem(plan.id(), "calendar-reviewed", ActorRole.TALENT_COORDINATOR, "coordinator");
            });
            assertThat(read.await(5, java.util.concurrent.TimeUnit.SECONDS)).isTrue();
            var approval = executor.submit(() -> {
                approvalStarted.countDown();
                return seasonPlanService.approve(plan.id(), "conditional", java.util.List.of(condition), ActorRole.CHIEF_GUARDIAN, "chief");
            });
            assertThat(approvalStarted.await(5, java.util.concurrent.TimeUnit.SECONDS)).isTrue();
            assertThatThrownBy(() -> approval.get(1, java.util.concurrent.TimeUnit.SECONDS))
                    .isInstanceOf(java.util.concurrent.TimeoutException.class);
            release.countDown();
            checklist.get(5, java.util.concurrent.TimeUnit.SECONDS);
            assertThat(approval.get(5, java.util.concurrent.TimeUnit.SECONDS).status()).isEqualTo(SeasonPlanStatus.APPROVED);
            SeasonPlan saved = seasonPlanRepository.findById(plan.id()).orElseThrow();
            assertThat(saved.status()).isEqualTo(SeasonPlanStatus.APPROVED);
            assertThat(saved.approvalConditions()).containsExactly(condition);
            assertThat(saved.readinessChecklist()).anyMatch(item -> item.key().equals("calendar-reviewed") && item.complete());
            assertThat(jdbc.queryForObject("select count(*) from audit_log_entries where season_plan_id = ? and action = 'SEASON_PLAN_APPROVED'", Integer.class, plan.id())).isEqualTo(1);
            assertThat(jdbc.queryForObject("select count(*) from notification_outbox where season_plan_id = ? and event_type = 'SEASON_PLAN_APPROVED'", Integer.class, plan.id())).isEqualTo(1);
        } finally {
            release.countDown();
            executor.shutdownNow();
        }
    }

    @Test
    void approvalConditionsPersistAndRollbackWithDecision() {
        SeasonPlan plan = seasonPlanService.createDraft(uniquePlanDraft(), ActorRole.FAIRY_PLANNER, "planner-1");
        seasonPlanService.submitForApproval(plan.id(), ActorRole.FAIRY_PLANNER, "planner-1");
        var reservation = pollenService.requestReservation(plan.id(), 100, ActorRole.POLLEN_GUARDIAN, "pollen-1");
        pollenService.confirmReservation(reservation.id(), 100, "ok", ActorRole.POLLEN_GUARDIAN, "pollen-1");
        seasonPlanService.verify(plan.id(), ActorRole.CHIEF_GUARDIAN, "guardian-1");
        var condition = new com.seasonplan.lifecycle.domain.ApprovalCondition("weather-evidence", "Weather", "Calibration", ActorRole.TALENT_COORDINATOR);
        var failure = new org.springframework.dao.DataAccessResourceFailureException("outbox failed");
        JdbcNotificationOutbox outboxTarget = org.springframework.test.util.AopTestUtils.getTargetObject(reviewOutbox);
        org.mockito.Mockito.doAnswer(invocation -> { invocation.callRealMethod(); throw failure; })
                .doCallRealMethod().when(outboxTarget).enqueue(any());
        assertThatThrownBy(() -> seasonPlanService.approve(plan.id(), "conditional", java.util.List.of(condition), ActorRole.CHIEF_GUARDIAN, "guardian-1"))
                .isSameAs(failure);
        assertThat(seasonPlanRepository.findById(plan.id()).orElseThrow().status()).isEqualTo(SeasonPlanStatus.VERIFIED);
        assertThat(seasonPlanRepository.findById(plan.id()).orElseThrow().approvalConditions()).isEmpty();
        assertThat(jdbc.queryForObject("select count(*) from audit_log_entries where season_plan_id = ? and action = 'SEASON_PLAN_APPROVED'", Integer.class, plan.id())).isZero();
        seasonPlanService.approve(plan.id(), "conditional", java.util.List.of(condition), ActorRole.CHIEF_GUARDIAN, "guardian-1");
        assertThat(seasonPlanRepository.findById(plan.id()).orElseThrow().approvalConditions()).containsExactly(condition);
        readinessChecklistService.completeItem(plan.id(), "weather-evidence", ActorRole.TALENT_COORDINATOR, "coordinator");
        assertThat(readinessChecklistService.calculateChecklist(plan.id())).containsKey("weather-evidence");
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void externalDecisionsNeverPersistReservationsAndAuditFailureCanBeRetried(boolean confirm) {
        SeasonPlan plan = seasonPlanService.createDraft(uniquePlanDraft(),
                ActorRole.FAIRY_PLANNER, "planner-1");
        PollenReservation reservation = pollenService.requestReservation(plan.id(), 100, ActorRole.POLLEN_GUARDIAN, "pollen-1");
        // Model a persistence failure directly: Spring translates generic IllegalStateException at @Repository boundaries.
        var failure = new org.springframework.dao.DataAccessResourceFailureException("simulated audit failure");
        doThrow(failure).doCallRealMethod().when(auditRepository).append(any(AuditLogEntry.class));
        assertThatThrownBy(() -> decide(reservation.id(), confirm)).isSameAs(failure);
        assertThat(pollenTree.findById(reservation.id()).orElseThrow().status())
                .isEqualTo(confirm ? PollenReservationStatus.CONFIRMED : PollenReservationStatus.REJECTED);
        decide(reservation.id(), confirm);
        decide(reservation.id(), confirm);
        assertThat(jdbc.queryForObject("select count(*) from pollen_reservations where season_plan_id = ?", Integer.class, plan.id())).isZero();
        assertThat(jdbc.queryForObject("select count(*) from audit_log_entries where season_plan_id = ? and action like 'POLLEN_RESERVATION_%'",
                Integer.class, plan.id())).isEqualTo(1);
    }

    private void decide(UUID id, boolean confirm) {
        if (confirm) pollenService.confirmReservation(id, 100, "decision", ActorRole.POLLEN_GUARDIAN, "pollen-1");
        else pollenService.rejectReservation(id, "decision", ActorRole.POLLEN_GUARDIAN, "pollen-1");
    }

    @Test
    void lifecycleServicesPersistSeasonPlanAndAuditLogToPostgres() {
        SeasonPlan draft = seasonPlanService.createDraft(
                uniquePlanDraft(),
                ActorRole.FAIRY_PLANNER,
                "planner-1"
        );

        assertThat(jdbc.queryForObject("select count(*) from season_plans where id = ?", Integer.class, draft.id()))
                .isEqualTo(1);
        assertThat(jdbc.queryForObject("select count(*) from season_plan_time_windows where season_plan_id = ?", Integer.class, draft.id()))
                .isEqualTo(1);
        assertThat(jdbc.queryForObject("select count(*) from season_plan_target_territories where season_plan_id = ?", Integer.class, draft.id()))
                .isEqualTo(1);
        assertThat(jdbc.queryForObject("select count(*) from readiness_checklist_items where season_plan_id = ?", Integer.class, draft.id()))
                .isEqualTo(2);
        assertThat(jdbc.queryForObject("select count(*) from audit_log_entries where season_plan_id = ?", Integer.class, draft.id()))
                .isEqualTo(1);

        SeasonPlan submitted = seasonPlanService.submitForApproval(draft.id(), ActorRole.FAIRY_PLANNER, "planner-1");

        assertThat(submitted.status()).isEqualTo(SeasonPlanStatus.SUBMITTED);
        assertThat(jdbc.queryForObject("select status from season_plans where id = ?", String.class, draft.id()))
                .isEqualTo("SUBMITTED");
        assertThat(jdbc.queryForObject("select count(*) from audit_log_entries where season_plan_id = ?", Integer.class, draft.id()))
                .isEqualTo(2);
    }

    @Test
    void preparationDataIsPersistedAndLoadedFromPostgres() {
        SeasonPlan draft = seasonPlanService.createDraft(
                uniquePlanDraft(),
                ActorRole.FAIRY_PLANNER,
                "planner-1"
        );
        seasonPlanService.submitForApproval(draft.id(), ActorRole.FAIRY_PLANNER, "planner-1");
        PollenReservation reservation = pollenService.requestReservation(draft.id(), 100, ActorRole.POLLEN_GUARDIAN, "pollen-1");
        pollenService.confirmReservation(reservation.id(), 100, "within limit", ActorRole.POLLEN_GUARDIAN, "pollen-1");
        seasonPlanService.verify(draft.id(), ActorRole.CHIEF_GUARDIAN, "guardian-1");
        SeasonPlan approved = seasonPlanService.approve(draft.id(), "approved", ActorRole.CHIEF_GUARDIAN, "guardian-1");
        MagicTask task = magicTaskService.createTask(approved.id(), SeasonPlanTestFixture.validTaskDraft(), ActorRole.TALENT_COORDINATOR, "coordinator-1");
        fairyAssignmentService.assignFairy(task.id(), "fairy-1", "LEAD", "SPRING_WEATHER", AssignmentStatus.CONFIRMED, ActorRole.TALENT_COORDINATOR, "coordinator-1");
        readinessChecklistService.completeItem(approved.id(), "calendar-reviewed", ActorRole.TALENT_COORDINATOR, "coordinator-1");
        readinessChecklistService.completeItem(approved.id(), "roles-reviewed", ActorRole.TALENT_COORDINATOR, "coordinator-1");

        SeasonPlan ready = seasonPlanService.markRitualReady(approved.id(), ActorRole.TALENT_COORDINATOR, "coordinator-1");

        assertThat(ready.status()).isEqualTo(SeasonPlanStatus.RITUAL_READY);
        assertThat(pollenService.get(reservation.id()).status()).isEqualTo(PollenReservationStatus.CONFIRMED);
        assertThat(jdbc.queryForObject("select count(*) from pollen_reservations where season_plan_id = ?", Integer.class, approved.id())).isZero();
        assertThat(jdbc.queryForObject("select count(*) from magic_tasks where season_plan_id = ?", Integer.class, approved.id()))
                .isEqualTo(1);
        assertThat(jdbc.queryForObject("select count(*) from fairy_assignments where magic_task_id = ?", Integer.class, task.id()))
                .isEqualTo(1);
        assertThat(jdbc.queryForObject("select count(*) from readiness_checklist_items where season_plan_id = ? and complete = true", Integer.class, approved.id()))
                .isEqualTo(2);
        assertThat(jdbc.queryForObject("select status from season_plans where id = ?", String.class, approved.id()))
                .isEqualTo("RITUAL_READY");
    }

    @Test
    void completingChecklistItemsPreservesExistingCompletionMetadata() {
        SeasonPlan draft = seasonPlanService.createDraft(
                uniquePlanDraft(),
                ActorRole.FAIRY_PLANNER,
                "planner-1"
        );

        readinessChecklistService.completeItem(draft.id(), "calendar-reviewed", ActorRole.TALENT_COORDINATOR, "coordinator-1");
        Map<String, Object> firstCompletion = checklistRow(draft.id(), "calendar-reviewed");

        readinessChecklistService.completeItem(draft.id(), "roles-reviewed", ActorRole.TALENT_COORDINATOR, "coordinator-2");
        Map<String, Object> afterUnrelatedCompletion = checklistRow(draft.id(), "calendar-reviewed");

        assertThat(afterUnrelatedCompletion.get("id")).isEqualTo(firstCompletion.get("id"));
        assertThat(afterUnrelatedCompletion.get("completed_by")).isEqualTo("coordinator-1");
        assertThat(afterUnrelatedCompletion.get("completed_at")).isEqualTo(firstCompletion.get("completed_at"));
    }

    @Test
    void repositoryListsPersistedSeasonPlansForSelection() {
        SeasonPlan spring = seasonPlanService.createDraft(
                uniquePlanDraft(),
                ActorRole.FAIRY_PLANNER,
                "planner-1"
        );
        SeasonPlan summerDraft = uniquePlanDraft();
        summerDraft.setSeasonType("SUMMER");
        SeasonPlan summer = seasonPlanService.createDraft(summerDraft, ActorRole.FAIRY_PLANNER, "planner-1");

        assertThat(seasonPlanRepository.findAll())
                .extracting(SeasonPlan::id)
                .contains(spring.id(), summer.id());
    }

    private SeasonPlan uniquePlanDraft() {
        SeasonPlan draft = SeasonPlanTestFixture.validPlanDraft("planner-1");
        draft.setTargetTerritories(java.util.List.of("territory-" + UUID.randomUUID()));
        return draft;
    }

    private Map<String, Object> checklistRow(UUID seasonPlanId, String itemKey) {
        return jdbc.queryForMap("""
                select id, completed_by, completed_at
                  from readiness_checklist_items
                 where season_plan_id = ? and item_key = ?
                """, seasonPlanId, itemKey);
    }
}
