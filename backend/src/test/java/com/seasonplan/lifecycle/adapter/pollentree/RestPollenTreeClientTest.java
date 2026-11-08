package com.seasonplan.lifecycle.adapter.pollentree;

import com.seasonplan.lifecycle.application.shared.NotFoundException;
import com.seasonplan.lifecycle.application.shared.PollenTreeUnavailableException;
import com.seasonplan.lifecycle.domain.DomainValidationException;
import com.seasonplan.lifecycle.domain.PollenReservationStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.net.SocketTimeoutException;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

class RestPollenTreeClientTest {
    private static final String BASE = "https://tree.example/api";
    private static final UUID PLAN = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID ID = UUID.fromString("00000000-0000-0000-0000-000000000002");
    private static final UUID REQUEST = UUID.fromString("00000000-0000-0000-0000-000000000003");
    private MockRestServiceServer server;
    private RestPollenTreeClient tree;

    @BeforeEach
    void setUp() {
        var builder = RestClient.builder().baseUrl(BASE).defaultHeader("X-API-Key", "secret-key");
        server = MockRestServiceServer.bindTo(builder).build();
        tree = new RestPollenTreeClient(builder.build());
    }

    @Test
    void capacityUsesSharedCredentialsAndExplicitDto() {
        server.expect(requestTo(BASE + "/capacity")).andExpect(method(HttpMethod.GET))
                .andExpect(header("X-API-Key", "secret-key"))
                .andRespond(withSuccess("{\"availableGrams\":1500.125,\"version\":\"v1\"}", MediaType.APPLICATION_JSON));
        assertThat(tree.capacity().availableGrams()).isEqualByComparingTo("1500.125");
        server.verify();
    }

    @Test
    void reservationRequestPreservesCorrelationAndLegacyIntegerAmounts() {
        server.expect(requestTo(BASE + "/reservations")).andExpect(method(HttpMethod.POST))
                .andExpect(header("X-API-Key", "secret-key"))
                .andExpect(content().json("""
                        {"seasonPlanId":"%s","requestId":"%s","requestedAmount":1500,"actorId":"guardian"}
                        """.formatted(PLAN, REQUEST)))
                .andRespond(withStatus(HttpStatus.CREATED).contentType(MediaType.APPLICATION_JSON).body(reservation("DRAFT", "null")));
        var result = tree.requestReservation(PLAN, REQUEST, 1500, "guardian");
        assertThat(result.id()).isEqualTo(ID);
        assertThat(result.requestedAmount()).isEqualTo(1500);
        assertThat(result.status()).isEqualTo(PollenReservationStatus.PENDING);
        server.verify();
    }

    @Test
    void lookupsMapFreshSnapshotsAndValidatePlan() {
        server.expect(requestTo(BASE + "/reservations/" + ID)).andRespond(withSuccess(reservation("CONFIRMED", "1200"), MediaType.APPLICATION_JSON));
        server.expect(requestTo(BASE + "/season-plans/" + PLAN + "/reservations/latest"))
                .andRespond(withSuccess(reservation("CONFIRMED", "1200"), MediaType.APPLICATION_JSON));
        var first = tree.findById(ID).orElseThrow();
        first.reject("local mutation", "local");
        var second = tree.findLatestBySeasonPlanId(PLAN).orElseThrow();
        assertThat(second.status()).isEqualTo(PollenReservationStatus.CONFIRMED);
        assertThat(second.confirmedAmount()).isEqualTo(1200);
        assertThat(second.confirmedBy()).isEqualTo("guardian");
        server.verify();
    }

    @Test
    void confirmationChecksPendingSnapshotAndReturnedDecision() {
        pending();
        server.expect(requestTo(BASE + "/reservations/" + ID + "/confirm")).andExpect(method(HttpMethod.POST))
                .andExpect(content().json("{\"confirmedAmount\":1200,\"comment\":\"ok\",\"actorId\":\"guardian\"}"))
                .andRespond(withSuccess(reservation("CONFIRMED", "1200"), MediaType.APPLICATION_JSON));
        assertThat(tree.confirmReservation(ID, 1200, "ok", "guardian").confirmedAmount()).isEqualTo(1200);
        server.verify();
    }

    @Test
    void rejectionChecksReturnedDecision() {
        pending();
        server.expect(requestTo(BASE + "/reservations/" + ID + "/reject")).andExpect(method(HttpMethod.POST))
                .andExpect(content().json("{\"comment\":\"ok\",\"actorId\":\"guardian\"}"))
                .andRespond(withSuccess(reservation("REJECTED", "null"), MediaType.APPLICATION_JSON));
        assertThat(tree.rejectReservation(ID, "ok", "guardian").status()).isEqualTo(PollenReservationStatus.REJECTED);
        server.verify();
    }

    @Test
    void usageAndIdempotentConsumptionPreservePlanEventAndOriginalRetrySnapshot() {
        server.expect(requestTo(BASE + "/season-plans/" + PLAN + "/usage")).andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(usage("0", null), MediaType.APPLICATION_JSON));
        expectConsumption(REQUEST, "2.125", "2.125");
        UUID later = UUID.fromString("00000000-0000-0000-0000-000000000004");
        expectConsumption(later, "1.000", "3.125");
        expectConsumption(REQUEST, "2.125", "2.125");
        assertThat(tree.summary(PLAN).orElseThrow().actualGrams()).isEqualByComparingTo("0");
        var original = tree.recordConsumption(PLAN, REQUEST, new BigDecimal("2.1254"));
        tree.recordConsumption(PLAN, later, BigDecimal.ONE);
        assertThat(tree.recordConsumption(PLAN, REQUEST, new BigDecimal("2.125"))).isEqualTo(original);
        server.verify();
    }

    @Test
    void onlyGenuineLookupAndSummary404AreAbsent() {
        server.expect(requestTo(BASE + "/reservations/" + ID)).andRespond(withResourceNotFound());
        server.expect(requestTo(BASE + "/season-plans/" + PLAN + "/reservations/latest")).andRespond(withResourceNotFound());
        server.expect(requestTo(BASE + "/season-plans/" + PLAN + "/usage")).andRespond(withResourceNotFound());
        assertThat(tree.findById(ID)).isEmpty();
        assertThat(tree.findLatestBySeasonPlanId(PLAN)).isEmpty();
        assertThat(tree.summary(PLAN)).isEmpty();
        server.verify();
    }

    @ParameterizedTest
    @ValueSource(ints = {301, 400, 401, 403, 409, 422, 429, 500, 503})
    void lookupErrorsAreNeverAbsence(int status) {
        server.expect(requestTo(BASE + "/reservations/" + ID))
                .andRespond(withStatus(HttpStatus.valueOf(status)).body("upstream-secret-key"));
        assertThatThrownBy(() -> tree.findById(ID)).isInstanceOf(PollenTreeUnavailableException.class)
                .hasMessage("Pollen Tree is unavailable");
        server.verify();
    }

    @ParameterizedTest
    @ValueSource(ints = {401, 403, 500, 503})
    void summaryErrorsAreNeverAbsence(int status) {
        server.expect(requestTo(BASE + "/season-plans/" + PLAN + "/usage"))
                .andRespond(withStatus(HttpStatus.valueOf(status)));
        assertThatThrownBy(() -> tree.summary(PLAN)).isInstanceOf(PollenTreeUnavailableException.class);
    }

    @Test
    void timeoutIsUnavailableWithoutRetryOrFallback() {
        server.expect(requestTo(BASE + "/capacity")).andRespond(withException(new SocketTimeoutException("secret")));
        assertThatThrownBy(tree::capacity).isInstanceOf(PollenTreeUnavailableException.class)
                .hasMessage("Pollen Tree is unavailable");
        server.verify();
    }

    @ParameterizedTest
    @ValueSource(ints = {400, 409, 422})
    void businessDenialIsSafeFieldValidation(int status) {
        server.expect(requestTo(BASE + "/reservations"))
                .andRespond(withStatus(HttpStatus.valueOf(status)).body("secret internal capacity details"));
        assertThatThrownBy(() -> tree.requestReservation(PLAN, REQUEST, 1500, "guardian"))
                .isInstanceOfSatisfying(DomainValidationException.class, e ->
                        assertThat(e.fieldErrors()).containsEntry("requestedAmount", java.util.List.of("Pollen Tree rejected the operation")));
        server.verify();
    }

    @Test
    void nonexistentMutationIsNotFound() {
        pending();
        server.expect(requestTo(BASE + "/reservations/" + ID + "/confirm")).andRespond(withResourceNotFound());
        assertThatThrownBy(() -> tree.confirmReservation(ID, 1200, "ok", "guardian")).isInstanceOf(NotFoundException.class);
        server.verify();
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "null", "{}", "not-json", "{\"availableGrams\":-1,\"version\":\"v\"}", "{\"availableGrams\":0,\"version\":\"\"}"})
    void malformedCapacityIsUnavailable(String body) {
        server.expect(requestTo(BASE + "/capacity")).andRespond(withSuccess(body, MediaType.APPLICATION_JSON));
        assertThatThrownBy(tree::capacity).isInstanceOf(PollenTreeUnavailableException.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {"id", "seasonPlanId", "requestedAmount", "confirmedAmount", "status", "confirmedBy"})
    void malformedOrContradictoryReservationIsUnavailable(String field) {
        String json = reservation("CONFIRMED", "1200");
        json = json.replaceAll("\"" + field + "\"\\s*:\\s*(\"[^\"]*\"|[0-9]+)", "\"" + field + "\":null");
        server.expect(requestTo(BASE + "/reservations/" + ID)).andRespond(withSuccess(json, MediaType.APPLICATION_JSON));
        assertThatThrownBy(() -> tree.findById(ID)).isInstanceOf(PollenTreeUnavailableException.class);
    }

    @Test
    void wrongLookupIdAndWrongLatestPlanAreUnavailable() {
        server.expect(requestTo(BASE + "/reservations/" + ID))
                .andRespond(withSuccess(reservation("PENDING", "null").replace(ID.toString(), REQUEST.toString()), MediaType.APPLICATION_JSON));
        server.expect(requestTo(BASE + "/season-plans/" + PLAN + "/reservations/latest"))
                .andRespond(withSuccess(reservation("PENDING", "null").replace(PLAN.toString(), REQUEST.toString()), MediaType.APPLICATION_JSON));
        assertThatThrownBy(() -> tree.findById(ID)).isInstanceOf(PollenTreeUnavailableException.class);
        assertThatThrownBy(() -> tree.findLatestBySeasonPlanId(PLAN)).isInstanceOf(PollenTreeUnavailableException.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {"plan", "request", "amount"})
    void requestSuccessMustMatchRequest(String mismatch) {
        String body = reservation("PENDING", "null");
        body = switch (mismatch) {
            case "plan" -> body.replace(PLAN.toString(), ID.toString());
            case "request" -> body.replace(REQUEST.toString(), ID.toString());
            default -> body.replace("1500", "1501");
        };
        server.expect(requestTo(BASE + "/reservations")).andRespond(withSuccess(body, MediaType.APPLICATION_JSON));
        assertThatThrownBy(() -> tree.requestReservation(PLAN, REQUEST, 1500, "guardian")).isInstanceOf(PollenTreeUnavailableException.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {"id", "plan", "state", "amount", "actor", "comment", "requested"})
    void inconsistentConfirmationIsUnavailable(String mismatch) {
        pending();
        String body = reservation("CONFIRMED", "1200");
        body = switch (mismatch) {
            case "id" -> body.replace(ID.toString(), REQUEST.toString());
            case "plan" -> body.replace(PLAN.toString(), REQUEST.toString());
            case "state" -> reservation("PENDING", "null");
            case "amount" -> body.replace("1200", "1199");
            case "actor" -> body.replace("guardian", "other");
            case "comment" -> body.replace("ok", "other");
            default -> body.replace("1500", "1501");
        };
        server.expect(requestTo(BASE + "/reservations/" + ID + "/confirm")).andRespond(withSuccess(body, MediaType.APPLICATION_JSON));
        assertThatThrownBy(() -> tree.confirmReservation(ID, 1200, "ok", "guardian")).isInstanceOf(PollenTreeUnavailableException.class);
    }

    @Test
    void identicalConfirmationReplaySendsCompleteStableDecisionPayload() {
        server.expect(requestTo(BASE + "/reservations/" + ID))
                .andRespond(withSuccess(reservation("CONFIRMED", "1200"), MediaType.APPLICATION_JSON));
        server.expect(requestTo(BASE + "/reservations/" + ID + "/confirm")).andExpect(method(HttpMethod.POST))
                .andExpect(content().json("{\"confirmedAmount\":1200,\"comment\":\"ok\",\"actorId\":\"guardian\"}", true))
                .andRespond(withSuccess(reservation("CONFIRMED", "1200"), MediaType.APPLICATION_JSON));
        assertThat(tree.confirmReservation(ID, 1200, "ok", "guardian").confirmedAmount()).isEqualTo(1200);
        server.verify();
    }

    @Test
    void identicalRejectionReplaySendsCompleteStableDecisionPayload() {
        server.expect(requestTo(BASE + "/reservations/" + ID))
                .andRespond(withSuccess(reservation("REJECTED", "null"), MediaType.APPLICATION_JSON));
        server.expect(requestTo(BASE + "/reservations/" + ID + "/reject")).andExpect(method(HttpMethod.POST))
                .andExpect(content().json("{\"comment\":\"ok\",\"actorId\":\"guardian\"}", true))
                .andRespond(withSuccess(reservation("REJECTED", "null"), MediaType.APPLICATION_JSON));
        assertThat(tree.rejectReservation(ID, "ok", "guardian").status()).isEqualTo(PollenReservationStatus.REJECTED);
        server.verify();
    }

    @Test
    void confirmationReplayWithNullCommentPreservesPayloadAndDoesNotAutomaticallyRetryTimeout() {
        server.expect(requestTo(BASE + "/reservations/" + ID))
                .andRespond(withSuccess(reservation("CONFIRMED", "1200").replace("\"ok\"", "null"), MediaType.APPLICATION_JSON));
        server.expect(requestTo(BASE + "/reservations/" + ID + "/confirm")).andExpect(method(HttpMethod.POST))
                .andExpect(content().json("{\"confirmedAmount\":1200,\"comment\":null,\"actorId\":\"guardian\"}", true))
                .andRespond(withException(new SocketTimeoutException("secret")));
        assertThatThrownBy(() -> tree.confirmReservation(ID, 1200, null, "guardian")).isInstanceOf(PollenTreeUnavailableException.class);
        server.verify();
    }

    @ParameterizedTest
    @ValueSource(strings = {"amount", "comment", "actor"})
    void changedConfirmationReplayIsValidationWithoutMutation(String changed) {
        server.expect(requestTo(BASE + "/reservations/" + ID))
                .andRespond(withSuccess(reservation("CONFIRMED", "1200"), MediaType.APPLICATION_JSON));
        assertThatThrownBy(() -> tree.confirmReservation(ID, changed.equals("amount") ? 1199 : 1200,
                changed.equals("comment") ? "other" : "ok", changed.equals("actor") ? "other" : "guardian"))
                .isInstanceOf(DomainValidationException.class);
        server.verify();
    }

    @ParameterizedTest
    @ValueSource(strings = {"comment", "actor"})
    void changedRejectionReplayIsValidationWithoutMutation(String changed) {
        server.expect(requestTo(BASE + "/reservations/" + ID))
                .andRespond(withSuccess(reservation("REJECTED", "null"), MediaType.APPLICATION_JSON));
        assertThatThrownBy(() -> tree.rejectReservation(ID, changed.equals("comment") ? "other" : "ok",
                changed.equals("actor") ? "other" : "guardian")).isInstanceOf(DomainValidationException.class);
        server.verify();
    }

    @Test
    void terminalReservationIsNotMutated() {
        server.expect(requestTo(BASE + "/reservations/" + ID)).andRespond(withSuccess(reservation("CONFIRMED", "1200"), MediaType.APPLICATION_JSON));
        assertThatThrownBy(() -> tree.rejectReservation(ID, "ok", "guardian")).isInstanceOf(DomainValidationException.class);
        server.verify();
    }

    @ParameterizedTest
    @ValueSource(strings = {"null", "{}", "{\"actualGrams\":0}"})
    void malformedUsageIsUnavailable(String body) {
        server.expect(requestTo(BASE + "/season-plans/" + PLAN + "/usage")).andRespond(withSuccess(body, MediaType.APPLICATION_JSON));
        assertThatThrownBy(() -> tree.summary(PLAN)).isInstanceOf(PollenTreeUnavailableException.class);
    }

    @Test
    void wrongUsagePlanOrEventIsUnavailable() {
        server.expect(requestTo(BASE + "/season-plans/" + PLAN + "/usage"))
                .andRespond(withSuccess(usage("1", null).replace(PLAN.toString(), ID.toString()), MediaType.APPLICATION_JSON));
        server.expect(requestTo(BASE + "/season-plans/" + PLAN + "/consumptions"))
                .andRespond(withSuccess(usage("1", ID), MediaType.APPLICATION_JSON));
        assertThatThrownBy(() -> tree.summary(PLAN)).isInstanceOf(PollenTreeUnavailableException.class);
        assertThatThrownBy(() -> tree.recordConsumption(PLAN, REQUEST, BigDecimal.ONE)).isInstanceOf(PollenTreeUnavailableException.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {"comment", "confirmedBy"})
    void pendingSuccessCannotContainTerminalMetadata(String field) {
        String body = reservation("PENDING", "null").replace("\"" + field + "\":null", "\"" + field + "\":\"unexpected\"");
        server.expect(requestTo(BASE + "/reservations/" + ID)).andRespond(withSuccess(body, MediaType.APPLICATION_JSON));
        assertThatThrownBy(() -> tree.findById(ID)).isInstanceOf(PollenTreeUnavailableException.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {"null", "{}", "", "not-json"})
    void successfulLookupWithoutValidSnapshotIsUnavailableNotAbsent(String body) {
        server.expect(requestTo(BASE + "/reservations/" + ID)).andRespond(withSuccess(body, MediaType.APPLICATION_JSON));
        assertThatThrownBy(() -> tree.findById(ID)).isInstanceOf(PollenTreeUnavailableException.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "-1", "1.25", "2147483648"})
    void invalidLegacyIntegerAmountIsUnavailable(String amount) {
        server.expect(requestTo(BASE + "/reservations/" + ID))
                .andRespond(withSuccess(reservation("PENDING", "null").replace("1500", amount), MediaType.APPLICATION_JSON));
        assertThatThrownBy(() -> tree.findById(ID)).isInstanceOf(PollenTreeUnavailableException.class);
    }

    @ParameterizedTest
    @ValueSource(ints = {202, 204, 301, 401, 403, 500, 503})
    void consumptionFailureIsUnavailableWithoutRetries(int status) {
        server.expect(requestTo(BASE + "/season-plans/" + PLAN + "/consumptions"))
                .andRespond(withStatus(HttpStatus.valueOf(status)).body("secret provider response"));
        assertThatThrownBy(() -> tree.recordConsumption(PLAN, REQUEST, BigDecimal.ONE))
                .isInstanceOf(PollenTreeUnavailableException.class).hasMessage("Pollen Tree is unavailable");
        server.verify();
    }

    @Test
    void confirmationCapacityDenialIsSafeFieldValidation() {
        pending();
        server.expect(requestTo(BASE + "/reservations/" + ID + "/confirm"))
                .andRespond(withStatus(HttpStatus.CONFLICT).body("secret capacity information"));
        assertThatThrownBy(() -> tree.confirmReservation(ID, 1200, "ok", "guardian"))
                .isInstanceOfSatisfying(DomainValidationException.class, e ->
                        assertThat(e.fieldErrors()).containsEntry("confirmedAmount", java.util.List.of("Pollen Tree rejected the operation")));
        server.verify();
    }

    @Test
    void missingPreflightAndMissingConsumptionAreNotFound() {
        server.expect(requestTo(BASE + "/reservations/" + ID)).andRespond(withResourceNotFound());
        server.expect(requestTo(BASE + "/season-plans/" + PLAN + "/consumptions")).andRespond(withResourceNotFound());
        assertThatThrownBy(() -> tree.rejectReservation(ID, "ok", "guardian")).isInstanceOf(NotFoundException.class);
        assertThatThrownBy(() -> tree.recordConsumption(PLAN, REQUEST, BigDecimal.ONE)).isInstanceOf(NotFoundException.class);
        server.verify();
    }

    @Test
    void rejectedDecisionCannotReturnConfirmedState() {
        pending();
        server.expect(requestTo(BASE + "/reservations/" + ID + "/reject"))
                .andRespond(withSuccess(reservation("CONFIRMED", "1200"), MediaType.APPLICATION_JSON));
        assertThatThrownBy(() -> tree.rejectReservation(ID, "ok", "guardian")).isInstanceOf(PollenTreeUnavailableException.class);
    }

    @Test
    void repeatedReservationRequestCanReturnCurrentTerminalSnapshot() {
        server.expect(requestTo(BASE + "/reservations"))
                .andRespond(withSuccess(reservation("CONFIRMED", "1200"), MediaType.APPLICATION_JSON));
        assertThat(tree.requestReservation(PLAN, REQUEST, 1500, "guardian").status()).isEqualTo(PollenReservationStatus.CONFIRMED);
    }

    private void pending() {
        server.expect(requestTo(BASE + "/reservations/" + ID)).andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(reservation("PENDING", "null"), MediaType.APPLICATION_JSON));
    }

    private void expectConsumption(UUID event, String grams, String total) {
        server.expect(requestTo(BASE + "/season-plans/" + PLAN + "/consumptions")).andExpect(method(HttpMethod.POST))
                .andExpect(header("X-API-Key", "secret-key"))
                .andExpect(content().json("{\"clientEventId\":\"" + event + "\",\"grams\":" + grams + "}"))
                .andRespond(withSuccess(usage(total, event), MediaType.APPLICATION_JSON));
    }

    private String reservation(String state, String confirmed) {
        return """
                {"id":"%s","seasonPlanId":"%s","requestId":"%s","requestedAmount":1500,
                 "confirmedAmount":%s,"status":"%s","comment":"ok","confirmedBy":"guardian"}
                """.formatted(ID, PLAN, REQUEST, confirmed, state)
                .replace("\"ok\"", state.equals("PENDING") || state.equals("DRAFT") ? "null" : "\"ok\"")
                .replace("\"guardian\"", state.equals("PENDING") || state.equals("DRAFT") ? "null" : "\"guardian\"");
    }

    private String usage(String actual, UUID event) {
        return """
                {"seasonPlanId":"%s","reservationId":"%s","confirmedGrams":1200,"actualGrams":%s,
                 "version":"v%s","clientEventId":%s}
                """.formatted(PLAN, ID, actual, actual, event == null ? "null" : "\"" + event + "\"");
    }
}
