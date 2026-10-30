package io.github.surezzzzzz.sdk.license.client.transport.test;

import io.github.surezzzzzz.sdk.license.client.transport.LicenseClientTransportAutoConfiguration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.github.surezzzzzz.sdk.license.client.model.ClientDecision;
import io.github.surezzzzzz.sdk.license.client.model.TrustMaterialSnapshot;
import io.github.surezzzzzz.sdk.license.client.spi.ClientResponseValidator;
import io.github.surezzzzzz.sdk.license.client.spi.LicenseServerClient;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;

/**
 * 传输实现断言：请求/响应映射、动作不匹配整份拒绝、缺字段拒绝、认证异常原样传导、信任资料分片映射。
 *
 * @author surezzzzzz
 */
class RestTemplateClientTest {

    @SuppressWarnings("unchecked")
    private RestTemplate restTemplateReturning(Map<String, Object> body) {
        RestTemplate restTemplate = mock(RestTemplate.class);
        when(restTemplate.exchange(anyString(), any(HttpMethod.class), any(HttpEntity.class),
                eq(Map.class))).thenReturn(ResponseEntity.ok(body));
        return restTemplate;
    }

    private LicenseServerClient client(RestTemplate restTemplate) {
        return new LicenseClientTransportAutoConfiguration.RestTemplateLicenseServerClient(
                restTemplate, "https://license.example.invalid");
    }

    @Test
    void authorizeMapsResponseAndValidatesAssociation() {
        Map<String, Object> body = new HashMap<String, Object>();
        body.put("allowed", true);
        body.put("action", "data.export");
        body.put("modelVersion", 1);
        body.put("commercialPhase", "ACTIVE");
        body.put("reasonCategory", "ENTITLED");
        body.put("evaluatedAt", 1780000000L);
        body.put("requestId", "request-test");
        body.put("recheckAt", null);
        body.put("reservationFenceReferences", java.util.Collections.emptyList());
        addPersonMetadata(body, "p", "g", null);
        LicenseServerClient client = client(restTemplateReturning(body));
        ClientDecision decision = client.authorize("p", "g", "PERSON", "data.export",
                null, null, null, null);
        assertThat(decision.isAllowed()).isTrue();
        assertThat(decision.getAction()).isEqualTo("data.export");
    }

    @Test
    void mismatchedActionRejectedWholeResponse() {
        Map<String, Object> body = new HashMap<String, Object>();
        body.put("allowed", true);
        // 响应回显的 action 与请求不一致：传输层读回显，validator 整份拒绝
        body.put("action", "other.action");
        body.put("modelVersion", 1);
        body.put("commercialPhase", "ACTIVE");
        body.put("reasonCategory", "ENTITLED");
        body.put("evaluatedAt", 1780000000L);
        body.put("requestId", "request-test");
        body.put("recheckAt", null);
        body.put("reservationFenceReferences", java.util.Collections.emptyList());
        addPersonMetadata(body, "p", "g", null);
        LicenseServerClient client = client(restTemplateReturning(body));
        assertThatThrownBy(() -> client.authorize("p", "g", "PERSON", "data.export",
                null, null, null, null))
                .isInstanceOf(ClientResponseValidator.ProtocolViolationException.class)
                .hasMessageContaining("响应动作与请求不一致");
    }

    @Test
    void missingActionEchoIsProtocolViolation() {
        Map<String, Object> body = new HashMap<String, Object>();
        body.put("allowed", true);
        // 响应不回显 action：与请求无法关联，整份拒绝
        body.put("modelVersion", 1);
        body.put("commercialPhase", "ACTIVE");
        LicenseServerClient client = client(restTemplateReturning(body));
        assertThatThrownBy(() -> client.authorize("p", "g", "PERSON", "act", null, null, null, null))
                .isInstanceOf(ClientResponseValidator.ProtocolViolationException.class);
    }

    @Test
    void missingFieldIsProtocolViolation() {
        Map<String, Object> body = new HashMap<String, Object>();
        body.put("allowed", true);
        body.put("action", "act");
        // 缺 commercialPhase
        body.put("modelVersion", 1);
        LicenseServerClient client = client(restTemplateReturning(body));
        assertThatThrownBy(() -> client.authorize("p", "g", "PERSON", "act", null, null, null, null))
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
        ClientDecision denied = client(restTemplateReturning(wire))
                .authorize("p", "g", "PERSON", "act", null, null, null, null);
        assertThat(denied.isAllowed()).isFalse();
        assertThat(denied.getCommercialPhase()).isNull();
        assertThat(denied.getReasonCategory()).isEqualTo("SUBJECT_INELIGIBLE");
        wire.put("allowed", true);
        wire.put("reasonCategory", "ENTITLED");
        assertThatThrownBy(() -> client(restTemplateReturning(wire))
                .authorize("p", "g", "PERSON", "act", null, null, null, null))
                .isInstanceOf(ClientResponseValidator.ProtocolViolationException.class);
    }

    @Test
    @SuppressWarnings("unchecked")
    void trustMaterialMapsSequenceAndParts() {
        RestTemplate restTemplate = mock(RestTemplate.class);
        Map<String, Object> body = new HashMap<String, Object>();
        body.put("issuerId", "i");
        body.put("productId", "p");
        body.put("sequence", 7);
        body.put("issuedAt", 1780000000L);
        body.put("snapshotId", "snap-7");
        body.put("manifestJws", "manifest-jws");
        body.put("revocationPartJws", Arrays.asList("part-one-jws", "part-two-jws"));
        body.put("trustedPublicKeysJson", Arrays.asList("{\"kid\":\"k1\"}"));
        when(restTemplate.getForObject(anyString(), eq(Map.class), any(), any()))
                .thenReturn(body);
        LicenseServerClient client = client(restTemplate);
        TrustMaterialSnapshot snapshot = client.fetchTrustMaterial("i", "p", 6L);
        assertThat(snapshot.getSequence()).isEqualTo(7L);
        assertThat(snapshot.getRevocationPartJws())
                .containsExactly("part-one-jws", "part-two-jws");
        assertThat(snapshot.getTrustedPublicKeysJson()).containsExactly("{\"kid\":\"k1\"}");
    }

    @Test
    void missingTrustPartsIsProtocolViolation() {
        RestTemplate restTemplate = mock(RestTemplate.class);
        Map<String, Object> body = new HashMap<String, Object>();
        body.put("issuerId", "i");
        body.put("productId", "p");
        body.put("sequence", 7);
        body.put("issuedAt", 1780000000L);
        body.put("snapshotId", "snap-7");
        // 缺 revocationPartJws/trustedPublicKeysJson：信任资料不得静默降级为空
        when(restTemplate.getForObject(anyString(), eq(Map.class), any(), any()))
                .thenReturn(body);
        LicenseServerClient client = client(restTemplate);
        assertThatThrownBy(() -> client.fetchTrustMaterial("i", "p", 6L))
                .isInstanceOf(ClientResponseValidator.ProtocolViolationException.class);
    }

    @Test
    void authFailurePropagatesAsExceptionNotSilentDeny() {
        RestTemplate restTemplate = mock(RestTemplate.class);
        when(restTemplate.exchange(anyString(), any(HttpMethod.class), any(HttpEntity.class),
                eq(Map.class))).thenThrow(new org.springframework.web.client.ResourceAccessException(
                "connection refused"));
        LicenseServerClient client = client(restTemplate);
        // 认证/网络失败必须异常且业务不执行，不冒充 DENY
        assertThatThrownBy(() -> client.authorize("p", "g", "PERSON", "act", null, null, null, null))
                .isInstanceOf(org.springframework.web.client.ResourceAccessException.class);
    }

    private static void addPersonMetadata(Map<String, Object> body, String productId, String grantId,
                                           String assignmentId) {
        body.put("productId", productId);
        body.put("grantId", grantId);
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
        body.put("subjectBinding", binding);
        body.put("grantRevision", 1L);
        body.put("grantStateVersion", 1L);
    }
}
