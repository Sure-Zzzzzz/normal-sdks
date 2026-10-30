package io.github.surezzzzzz.sdk.license.client.feign.test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.surezzzzzz.sdk.license.client.feign.LicenseFeignJakartaAutoConfiguration;
import io.github.surezzzzzz.sdk.license.client.model.ClientDecision;
import io.github.surezzzzzz.sdk.license.client.model.TrustMaterialSnapshot;
import io.github.surezzzzzz.sdk.license.client.spi.ClientResponseValidator;
import io.github.surezzzzzz.sdk.license.client.spi.LicenseServerClient;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * jakarta feign 传输件断言：同构语义（映射/缺字段拒绝/空响应拒绝/信任资料代次）。
 *
 * @author surezzzzzz
 */
class JakartaFeignClientTest {

    private LicenseServerClient client(final Map<String, Object> decision,
                                       final Map<String, Object> trustMaterial) {
        return new LicenseFeignJakartaAutoConfiguration.JakartaFeignLicenseServerClient(
                new LicenseFeignJakartaAutoConfiguration.LicenseContractFeign() {
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
                        throw new UnsupportedOperationException();
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
                        return trustMaterial;
                    }
                });
    }

    @Test
    void authorizeMapsDecision() {
        Map<String, Object> decision = new HashMap<String, Object>();
        decision.put("action", "act");
        decision.put("allowed", true);
        decision.put("modelVersion", 5);
        decision.put("commercialPhase", "ACTIVE");
        decision.put("reasonCategory", "ENTITLED");
        decision.put("evaluatedAt", 1780000000L);
        decision.put("requestId", "request-test");
        decision.put("recheckAt", null);
        decision.put("reservationFenceReferences", java.util.Collections.emptyList());
        addPersonMetadata(decision, "p", "g", null);
        ClientDecision result = client(decision, null)
                .authorize("p", "g", "PERSON", "act", null, null, null, null);
        assertThat(result.isAllowed()).isTrue();
        assertThat(result.getModelVersion()).isEqualTo(5L);
    }

    @Test
    void nullDecisionRejected() {
        assertThatThrownBy(() -> client(null, null)
                .authorize("p", "g", "SERVICE", "act", null, null, null, null))
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
        ClientDecision denied = client(wire, null).authorize("p", "g", "SERVICE", "act", null, null, null, null);
        assertThat(denied.isAllowed()).isFalse();
        assertThat(denied.getCommercialPhase()).isNull();
        assertThat(denied.getReasonCategory()).isEqualTo("SUBJECT_INELIGIBLE");
        wire.put("allowed", true);
        wire.put("reasonCategory", "ENTITLED");
        assertThatThrownBy(() -> client(wire, null).authorize("p", "g", "SERVICE", "act", null, null, null, null))
                .isInstanceOf(ClientResponseValidator.ProtocolViolationException.class);
    }

    @Test
    void trustMaterialMapsSequence() {
        Map<String, Object> material = new HashMap<String, Object>();
        material.put("issuerId", "i");
        material.put("productId", "p");
        material.put("sequence", 12);
        material.put("issuedAt", 1780000000L);
        material.put("snapshotId", "snap-12");
        material.put("manifestJws", "manifest-jws");
        material.put("revocationPartJws", java.util.Arrays.asList("part-a"));
        material.put("trustedPublicKeysJson", java.util.Arrays.asList("{}", "{}"));
        TrustMaterialSnapshot snapshot = client(null, material)
                .fetchTrustMaterial("i", "p", 11L);
        assertThat(snapshot.getSequence()).isEqualTo(12L);
        // 分片与公钥档案必须映射（Feign 线曾静默丢弃为 null）
        assertThat(snapshot.getRevocationPartJws()).containsExactly("part-a");
        assertThat(snapshot.getTrustedPublicKeysJson()).hasSize(2);
    }

    @Test
    void missingTrustMaterialFieldRejected() {
        Map<String, Object> material = new HashMap<String, Object>();
        material.put("sequence", 12);
        assertThatThrownBy(() -> client(null, material).fetchTrustMaterial("i", "p", 11L))
                .isInstanceOf(ClientResponseValidator.ProtocolViolationException.class);
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
