package io.github.surezzzzzz.sdk.license.client.transport.test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.github.surezzzzzz.sdk.license.client.model.ClientDecision;
import io.github.surezzzzzz.sdk.license.client.spi.ClientResponseValidator;
import io.github.surezzzzzz.sdk.license.client.spi.LicenseServerClient;
import io.github.surezzzzzz.sdk.license.client.transport.LicenseClientJakartaTransportAutoConfiguration;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;

/**
 * jakarta 传输件断言：与 javax 件同构语义（映射/缺字段拒绝/异常传导）。
 *
 * @author surezzzzzz
 */
class JakartaRestTemplateClientTest {

    @SuppressWarnings("unchecked")
    private RestTemplate restTemplateReturning(Map<String, Object> body) {
        RestTemplate restTemplate = mock(RestTemplate.class);
        when(restTemplate.exchange(anyString(), any(HttpMethod.class), any(HttpEntity.class),
                eq(Map.class))).thenReturn(ResponseEntity.ok(body));
        return restTemplate;
    }

    private LicenseServerClient client(RestTemplate restTemplate) {
        return new LicenseClientJakartaTransportAutoConfiguration.JakartaRestTemplateLicenseServerClient(
                restTemplate, "https://license.example.invalid");
    }

    @Test
    void authorizeMapsAllowWithAssociation() {
        Map<String, Object> body = new HashMap<String, Object>();
        body.put("allowed", true);
        body.put("action", "data.export");
        body.put("modelVersion", 3);
        body.put("commercialPhase", "ACTIVE");
        body.put("reasonCategory", "ENTITLED");
        body.put("evaluatedAt", 1780000000L);
        body.put("requestId", "request-test");
        body.put("recheckAt", null);
        body.put("reservationFenceReferences", java.util.Collections.emptyList());
        addPersonMetadata(body, "p", "g", null);
        ClientDecision decision = client(restTemplateReturning(body))
                .authorize("p", "g", "PERSON", "data.export", null, null, null, null);
        assertThat(decision.isAllowed()).isTrue();
        assertThat(decision.getModelVersion()).isEqualTo(3L);
    }

    @Test
    void denyMapsReasonCategory() {
        Map<String, Object> body = new HashMap<String, Object>();
        body.put("allowed", false);
        body.put("action", "act");
        body.put("modelVersion", 3);
        body.put("commercialPhase", "ACTIVE");
        body.put("reasonCategory", "RULE_DENIED");
        body.put("evaluatedAt", 1780000000L);
        body.put("requestId", "request-test");
        body.put("recheckAt", null);
        body.put("reservationFenceReferences", java.util.Collections.emptyList());
        addPersonMetadata(body, "p", "g", null);
        ClientDecision decision = client(restTemplateReturning(body))
                .authorize("p", "g", "PERSON", "act", null, null, null, null);
        assertThat(decision.isAllowed()).isFalse();
        assertThat(decision.getReasonCategory()).isEqualTo("RULE_DENIED");
    }

    @Test
    void mismatchedActionEchoRejectedWholeResponse() {
        Map<String, Object> body = new HashMap<String, Object>();
        body.put("allowed", true);
        // 回显的 action 与请求不一致：validator 整份拒绝（对象不关联）
        body.put("action", "other.action");
        body.put("modelVersion", 3);
        body.put("commercialPhase", "ACTIVE");
        body.put("reasonCategory", "ENTITLED");
        body.put("evaluatedAt", 1780000000L);
        body.put("requestId", "request-test");
        body.put("recheckAt", null);
        body.put("reservationFenceReferences", java.util.Collections.emptyList());
        addPersonMetadata(body, "p", "g", null);
        assertThatThrownBy(() -> client(restTemplateReturning(body))
                .authorize("p", "g", "PERSON", "act", null, null, null, null))
                .isInstanceOf(ClientResponseValidator.ProtocolViolationException.class)
                .hasMessageContaining("响应动作与请求不一致");
    }

    @Test
    void missingFieldRejectedWholeResponse() {
        Map<String, Object> body = new HashMap<String, Object>();
        body.put("allowed", true);
        assertThatThrownBy(() -> client(restTemplateReturning(body))
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
    void networkFailurePropagatesNotSilentDeny() {
        RestTemplate restTemplate = mock(RestTemplate.class);
        when(restTemplate.exchange(anyString(), any(HttpMethod.class), any(HttpEntity.class),
                eq(Map.class))).thenThrow(new org.springframework.web.client.ResourceAccessException(
                "refused"));
        assertThatThrownBy(() -> client(restTemplate)
                .authorize("p", "g", "PERSON", "act", null, null, null, null))
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
