package io.github.surezzzzzz.sdk.license.client.feign.test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.surezzzzzz.sdk.license.client.feign.LicenseFeignAutoConfiguration;
import io.github.surezzzzzz.sdk.license.client.model.ClientDecision;
import io.github.surezzzzzz.sdk.license.client.model.ClientReservation;
import io.github.surezzzzzz.sdk.license.client.model.QuotaRequest;
import io.github.surezzzzzz.sdk.license.client.spi.ClientResponseValidator;
import io.github.surezzzzzz.sdk.license.client.spi.LicenseServerClient;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * Feign 传输件断言：与 RestTemplate 件同构语义（映射/缺字段拒绝/异常传导）。
 *
 * @author surezzzzzz
 */
class FeignClientTest {

    private LicenseServerClient clientReturning(final Map<String, Object> decision) {
        return new LicenseFeignAutoConfiguration.FeignLicenseServerClient(
                new LicenseFeignAutoConfiguration.LicenseContractFeign() {
                    @Override
                    public Map<String, Object> decisions(Map<String, Object> body) {
                        return decision;
                    }

                    @Override
                    public Map<String, Object> grantSelections(Map<String, Object> body) {
                        throw new UnsupportedOperationException();
                    }

                    @Override
                    public Map<String, Object> quotaReservations(Map<String, Object> body) {
                        Map<String, Object> reservation = new HashMap<String, Object>();
                        reservation.put("reservationId", "r-1");
                        reservation.put("grantId", body.get("grantId"));
                        reservation.put("metric", body.get("metric"));
                        reservation.put("quantity", body.get("quantity"));
                        reservation.put("kind", body.get("kind"));
                        reservation.put("action", body.get("action"));
                        reservation.put("grantRevision", body.get("expectedGrantRevision"));
                        reservation.put("businessOperationId", body.get("businessOperationId"));
                        reservation.put("fence", 1L);
                        reservation.put("validUntil", 1780000900L);
                        reservation.put("state", "ACTIVE");
                        return reservation;
                    }

                    @Override
                    public void release(String reservationId) {
                    }

                    @Override
                    public Map<String, Object> renewal(String reservationId, Map<String, Object> body) {
                        throw new UnsupportedOperationException();
                    }

                    @Override
                    public Map<String, Object> trustMaterial(String productId, long knownSequence) {
                        throw new UnsupportedOperationException();
                    }
                });
    }

    @Test
    void authorizeMapsAllow() {
        Map<String, Object> decision = new HashMap<String, Object>();
        decision.put("allowed", true);
        decision.put("action", "data.export");
        decision.put("modelVersion", 2);
        decision.put("commercialPhase", "ACTIVE");
        decision.put("reasonCategory", "ENTITLED");
        decision.put("evaluatedAt", 1780000000L);
        decision.put("requestId", "request-test");
        decision.put("recheckAt", null);
        decision.put("reservationFenceReferences", java.util.Collections.emptyList());
        addPersonMetadata(decision, "p", "g", null);
        ClientDecision result = clientReturning(decision)
                .authorize("p", "g", "PERSON", "data.export", null, null, null, null);
        assertThat(result.isAllowed()).isTrue();
        assertThat(result.getModelVersion()).isEqualTo(2L);
    }

    @Test
    void mismatchedActionEchoRejected() {
        Map<String, Object> decision = new HashMap<String, Object>();
        decision.put("allowed", true);
        // 回显 action 与请求不一致：整份拒绝
        decision.put("action", "other.action");
        decision.put("modelVersion", 2);
        decision.put("commercialPhase", "ACTIVE");
        decision.put("reasonCategory", "ENTITLED");
        decision.put("evaluatedAt", 1780000000L);
        decision.put("requestId", "request-test");
        decision.put("recheckAt", null);
        decision.put("reservationFenceReferences", java.util.Collections.emptyList());
        addPersonMetadata(decision, "p", "g", null);
        assertThatThrownBy(() -> clientReturning(decision)
                .authorize("p", "g", "PERSON", "data.export", null, null, null, null))
                .isInstanceOf(ClientResponseValidator.ProtocolViolationException.class)
                .hasMessageContaining("响应动作与请求不一致");
    }

    @Test
    void missingFieldRejectedWholeResponse() {
        Map<String, Object> decision = new HashMap<String, Object>();
        decision.put("allowed", false);
        // 缺 reasonCategory/modelVersion
        assertThatThrownBy(() -> clientReturning(decision)
                .authorize("p", "g", "PERSON", "act", null, null, null, null))
                .isInstanceOf(ClientResponseValidator.ProtocolViolationException.class);
    }

    @Test
    void nullableCommercialPhaseIsAcceptedForDenyButRejectedForAllow() {
        Map<String, Object> wire = new HashMap<String, Object>();
        wire.put("allowed", false);
        wire.put("action", "act");
        wire.put("modelVersion", 0L);
        wire.put("commercialPhase", null);
        wire.put("reasonCategory", "SUBJECT_INELIGIBLE");
        wire.put("evaluatedAt", 1780000000L);
        wire.put("requestId", "request-test");
        wire.put("recheckAt", null);
        wire.put("reservationFenceReferences", java.util.Collections.emptyList());
        ClientDecision denied = clientReturning(wire).authorize("p", "g", "PERSON", "act", null, null, null, null);
        assertThat(denied.isAllowed()).isFalse();
        assertThat(denied.getCommercialPhase()).isNull();
        assertThat(denied.getReasonCategory()).isEqualTo("SUBJECT_INELIGIBLE");
        wire.put("allowed", true);
        wire.put("reasonCategory", "ENTITLED");
        assertThatThrownBy(() -> clientReturning(wire).authorize("p", "g", "PERSON", "act", null, null, null, null))
                .isInstanceOf(ClientResponseValidator.ProtocolViolationException.class);
    }

    @Test
    void nullResponseRejected() {
        assertThatThrownBy(() -> clientReturning(null)
                .authorize("p", "g", "PERSON", "act", null, null, null, null))
                .isInstanceOf(ClientResponseValidator.ProtocolViolationException.class);
    }

    @Test
    void reserveAndConsumeMapReservations() {
        LicenseServerClient client = clientReturning(null);
        ClientReservation reserve = client.reserve(new QuotaRequest("p", "g", "SERVICE", "run",
                "sessions", 2L, "op-1", 1L, "assignment", null));
        assertThat(reserve.getReservationId()).isEqualTo("r-1");
        assertThat(reserve.getQuantity()).isEqualTo(2L);
        ClientReservation consume = client.consume(new QuotaRequest("p", "g", "SERVICE", "run",
                "api.calls", 5L, "op-2", 1L, "assignment", null));
        assertThat(consume.getQuantity()).isEqualTo(5L);
    }

    private static void addPersonMetadata(Map<String, Object> decision, String productId, String grantId,
                                           String assignmentId) {
        decision.put("productId", productId);
        decision.put("grantId", grantId);
        Map<String, Object> binding = new HashMap<String, Object>();
        binding.put("kind", "PERSON");
        Map<String, Object> subjectRef = new HashMap<String, Object>();
        subjectRef.put("kind", "PERSON");
        subjectRef.put("sourceId", "iam");
        subjectRef.put("subjectId", "user-1");
        binding.put("subjectRef", subjectRef);
        if (assignmentId != null) {
            binding.put("assignmentId", assignmentId);
            binding.put("assignmentGeneration", 1L);
        }
        decision.put("subjectBinding", binding);
        decision.put("grantRevision", 1L);
        decision.put("grantStateVersion", 1L);
    }
}
