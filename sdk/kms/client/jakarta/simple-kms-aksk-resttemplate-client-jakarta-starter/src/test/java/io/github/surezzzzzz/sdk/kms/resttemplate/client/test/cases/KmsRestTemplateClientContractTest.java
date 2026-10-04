package io.github.surezzzzzz.sdk.kms.resttemplate.client.test.cases;

import io.github.surezzzzzz.sdk.kms.client.KmsClient;
import io.github.surezzzzzz.sdk.kms.client.model.*;
import io.github.surezzzzzz.sdk.kms.resttemplate.client.KmsRestTemplateClient;
import io.github.surezzzzzz.sdk.kms.resttemplate.client.configuration.KmsClientRestTemplateProperties;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.RestTemplate;

import java.nio.charset.StandardCharsets;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

/**
 * KmsRestTemplateClient 契约测试：桩出底座 RestTemplate，逐方法断言 URL、方法、头、体字段与响应解析。
 *
 * @author surezzzzzz
 */
@Slf4j
class KmsRestTemplateClientContractTest {

    private static final String API_BASE = "https://kms.example.internal/api/kms";
    private static final String IDEMPOTENCY_KEY = "idem-0001";
    private static final String KEY_REF = "key-1";

    private RestTemplate restTemplate;
    private MockRestServiceServer server;
    private KmsClient client;

    private static String keyJson() {
        return "{\"keyRef\":\"" + KEY_REF + "\",\"keyAlias\":\"alias-1\",\"purpose\":\"SIGN\","
                + "\"algorithm\":\"ES256\",\"state\":\"ACTIVE\",\"activeVersion\":1,\"rowVersion\":4,"
                + "\"createdAt\":\"2026-01-01T00:00:00.000Z\",\"updatedAt\":\"2026-01-01T00:00:00.000Z\"}";
    }

    private static String policyJson() {
        return "{\"policyId\":\"policy-1\",\"keyRef\":\"" + KEY_REF + "\",\"principalId\":\"iam:user-1\","
                + "\"keyVersion\":1,\"operation\":\"SIGN\",\"expiresAt\":\"2026-07-27T01:02:03.004Z\","
                + "\"rowVersion\":7}";
    }

    private static String publicKeyJson() {
        return "{\"keyRef\":\"" + KEY_REF + "\",\"version\":1,\"algorithm\":\"ES256\",\"state\":\"ACTIVE\","
                + "\"publicKey\":\"cHVia2V5\"}";
    }

    private static void assertKey(KmsKey key) {
        assertAll("密钥模型契约字段",
                () -> assertEquals(KEY_REF, key.getKeyRef(), "keyRef 必须解析"),
                () -> assertEquals("alias-1", key.getKeyAlias(), "keyAlias 必须解析"),
                () -> assertEquals("SIGN", key.getPurpose(), "purpose 必须解析"),
                () -> assertEquals("ES256", key.getAlgorithm(), "algorithm 必须解析"),
                () -> assertEquals("ACTIVE", key.getState(), "state 必须解析"),
                () -> assertEquals(Integer.valueOf(1), key.getActiveVersion(), "activeVersion 必须解析"),
                () -> assertEquals(Long.valueOf(4), key.getRowVersion(), "rowVersion 必须解析"),
                () -> assertEquals(Instant.parse("2026-01-01T00:00:00.000Z"), key.getCreatedAt(),
                        "createdAt 必须按 UTC 毫秒解析"),
                () -> assertEquals(Instant.parse("2026-01-01T00:00:00.000Z"), key.getUpdatedAt(),
                        "updatedAt 必须按 UTC 毫秒解析"));
    }

    private static void assertPolicy(KmsPolicy policy) {
        assertAll("策略模型契约字段",
                () -> assertEquals("policy-1", policy.getPolicyId(), "policyId 必须解析"),
                () -> assertEquals(KEY_REF, policy.getKeyRef(), "keyRef 必须解析"),
                () -> assertEquals("iam:user-1", policy.getPrincipalId(), "principalId 必须解析"),
                () -> assertEquals(Integer.valueOf(1), policy.getKeyVersion(), "keyVersion 必须解析"),
                () -> assertEquals("SIGN", policy.getOperation(), "operation 必须解析"),
                () -> assertEquals(Instant.parse("2026-07-27T01:02:03.004Z"), policy.getExpiresAt(),
                        "expiresAt 必须按 UTC 毫秒解析"),
                () -> assertEquals(Long.valueOf(7), policy.getRowVersion(), "rowVersion 必须解析"));
    }

    @BeforeEach
    void setUp() {
        restTemplate = new RestTemplate();
        server = MockRestServiceServer.bindTo(restTemplate).build();
        KmsClientRestTemplateProperties properties = new KmsClientRestTemplateProperties();
        properties.setBaseUrl("https://kms.example.internal");
        client = new KmsRestTemplateClient(restTemplate, properties);
    }

    @Test
    void shouldCallAllKeyManagementEndpointsWithContractFields() {
        server.expect(requestTo(API_BASE + "/keys"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("Idempotency-Key", IDEMPOTENCY_KEY))
                .andExpect(content().json("{\"keyAlias\":\"alias-1\",\"purpose\":\"SIGN\",\"algorithm\":\"ES256\"}"))
                .andRespond(withSuccess(keyJson(), MediaType.APPLICATION_JSON));
        KmsKey created = client.createKey(IDEMPOTENCY_KEY, "alias-1", "SIGN", "ES256");
        assertKey(created);
        server.verify();
        server.reset();

        server.expect(requestTo(API_BASE + "/keys/" + KEY_REF))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(keyJson(), MediaType.APPLICATION_JSON));
        assertKey(client.getKey(KEY_REF));
        server.verify();
        server.reset();

        server.expect(requestTo(API_BASE + "/keys?page=1&size=10&alias=alias-1&purpose=SIGN&algorithm=ES256&state=ACTIVE"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("{\"items\":[" + keyJson() + "],\"page\":1,\"size\":10,\"total\":1}",
                        MediaType.APPLICATION_JSON));
        KmsKeyPage page = client.listKeys(1, 10, "alias-1", "SIGN", "ES256", "ACTIVE");
        assertEquals(1, page.getItems().size(), "列表必须解析条目");
        assertKey(page.getItems().get(0));
        assertEquals(Long.valueOf(1), page.getTotal(), "分页 total 必须解析");
        server.verify();
        server.reset();

        server.expect(requestTo(API_BASE + "/keys/" + KEY_REF + "/state"))
                .andExpect(method(HttpMethod.PATCH))
                .andExpect(header("Idempotency-Key", IDEMPOTENCY_KEY))
                .andExpect(content().json("{\"state\":\"DISABLED\",\"expectedRowVersion\":4}"))
                .andRespond(withSuccess(keyJson(), MediaType.APPLICATION_JSON));
        assertKey(client.changeKeyState(IDEMPOTENCY_KEY, KEY_REF, "DISABLED", 4L));
        server.verify();
        server.reset();

        server.expect(requestTo(API_BASE + "/keys/" + KEY_REF + "/versions"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("Idempotency-Key", IDEMPOTENCY_KEY))
                .andExpect(content().json("{\"expectedRowVersion\":4}"))
                .andRespond(withSuccess(keyJson(), MediaType.APPLICATION_JSON));
        assertKey(client.rotateKey(IDEMPOTENCY_KEY, KEY_REF, 4L));
        server.verify();
        server.reset();

        server.expect(requestTo(API_BASE + "/keys/" + KEY_REF + "/destruction"))
                .andExpect(method(HttpMethod.PUT))
                .andExpect(header("Idempotency-Key", IDEMPOTENCY_KEY))
                .andExpect(content().json("{\"dueAt\":\"2026-07-27T01:02:03.004Z\",\"expectedRowVersion\":4}", true))
                .andRespond(withSuccess(keyJson(), MediaType.APPLICATION_JSON));
        assertKey(client.scheduleDestruction(IDEMPOTENCY_KEY, KEY_REF,
                Instant.parse("2026-07-27T01:02:03.004Z"), 4L));
        server.verify();
        server.reset();

        server.expect(requestTo(API_BASE + "/keys/" + KEY_REF + "/destruction"))
                .andExpect(method(HttpMethod.DELETE))
                .andExpect(header("Idempotency-Key", IDEMPOTENCY_KEY))
                .andExpect(content().json("{\"expectedRowVersion\":5}"))
                .andRespond(withSuccess());
        client.cancelDestruction(IDEMPOTENCY_KEY, KEY_REF, 5L);
        server.verify();
    }

    @Test
    void shouldCallPolicyEndpointsWithCorrectConcurrencyContract() {
        server.expect(requestTo(API_BASE + "/keys/" + KEY_REF + "/policies"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("Idempotency-Key", IDEMPOTENCY_KEY))
                .andExpect(content().json(
                        "{\"principalId\":\"iam:user-1\",\"operation\":\"SIGN\",\"keyVersion\":1,"
                                + "\"expiresAt\":\"2026-07-27T01:02:03.004Z\"}"))
                .andRespond(withSuccess(policyJson(), MediaType.APPLICATION_JSON));
        KmsPolicy created = client.createPolicy(IDEMPOTENCY_KEY, KEY_REF, "iam:user-1", 1, "SIGN",
                Instant.parse("2026-07-27T01:02:03.004Z"));
        assertPolicy(created);
        server.verify();
        server.reset();

        server.expect(requestTo(API_BASE + "/keys/" + KEY_REF + "/policies"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("{\"items\":[" + policyJson() + "]}", MediaType.APPLICATION_JSON));
        assertEquals(1, client.listPolicies(KEY_REF).size(), "策略列表必须解析条目");
        server.verify();
        server.reset();

        server.expect(requestTo(API_BASE + "/keys/" + KEY_REF + "/policies/policy-1"))
                .andExpect(method(HttpMethod.DELETE))
                .andExpect(header("Idempotency-Key", IDEMPOTENCY_KEY))
                .andExpect(content().json("{\"expectedRowVersion\":7}"))
                .andRespond(withSuccess());
        client.revokePolicy(IDEMPOTENCY_KEY, KEY_REF, "policy-1", 7L);
        server.verify();
    }

    @Test
    void shouldCallCryptoAndPublicKeyEndpointsWithoutRetry() {
        byte[] payload = "payload-1".getBytes(StandardCharsets.UTF_8);

        server.expect(requestTo(API_BASE + "/crypto/signatures"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().json("{\"keyRef\":\"" + KEY_REF + "\",\"input\":\"cGF5bG9hZC0x\"}"))
                .andRespond(withSuccess(
                        "{\"keyRef\":\"" + KEY_REF + "\",\"version\":1,\"signature\":\"c2lnbmF0dXJl\"}",
                        MediaType.APPLICATION_JSON));
        KmsSignature signature = client.sign(KEY_REF, null, payload);
        assertEquals(KEY_REF, signature.getKeyRef(), "签名响应必须解析 keyRef");
        assertArrayEquals("signature".getBytes(StandardCharsets.UTF_8), signature.getSignature(),
                "签名必须按无填充 Base64url 解码");
        server.verify();
        server.reset();

        server.expect(requestTo(API_BASE + "/crypto/verifications"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().json(
                        "{\"keyRef\":\"" + KEY_REF + "\",\"input\":\"cGF5bG9hZC0x\",\"signature\":\"c2lnbmF0dXJl\"}"))
                .andRespond(withSuccess("{\"valid\":true}", MediaType.APPLICATION_JSON));
        assertTrue(client.verify(KEY_REF, null, payload,
                "signature".getBytes(StandardCharsets.UTF_8)), "验签必须解析 valid 布尔");
        server.verify();
        server.reset();

        server.expect(requestTo(API_BASE + "/crypto/envelopes"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess("{\"envelope\":\"ZW52ZWxvcGU\"}", MediaType.APPLICATION_JSON));
        assertArrayEquals("envelope".getBytes(StandardCharsets.UTF_8), client.encrypt(KEY_REF, payload, null),
                "信封必须按无填充 Base64url 解码");
        server.verify();
        server.reset();

        server.expect(requestTo(API_BASE + "/crypto/decryptions"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess("{\"plaintext\":\"cGxhaW50ZXh0\"}", MediaType.APPLICATION_JSON));
        assertArrayEquals("plaintext".getBytes(StandardCharsets.UTF_8),
                client.decrypt("envelope".getBytes(StandardCharsets.UTF_8), null),
                "明文必须按无填充 Base64url 解码");
        server.verify();
        server.reset();

        server.expect(requestTo(API_BASE + "/keys/" + KEY_REF + "/public-key?version=1"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(publicKeyJson(), MediaType.APPLICATION_JSON));
        KmsPublicKey publicKey = client.readPublicKey(KEY_REF, 1);
        assertEquals("ES256", publicKey.getAlgorithm(), "公钥算法必须解析");
        server.verify();
        server.reset();

        server.expect(requestTo(API_BASE + "/keys/" + KEY_REF + "/public-keys"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("[" + publicKeyJson() + "]", MediaType.APPLICATION_JSON));
        assertEquals(1, client.listPublicKeys(KEY_REF).size(), "公钥列表直接返回数组");
        server.verify();
    }

    @Test
    void shouldPropagateHttpErrorsWithoutWrapping() {
        server.expect(requestTo(API_BASE + "/keys/" + KEY_REF))
                .andRespond(withBadRequest().body("{\"message\":\"bad\"}"));
        HttpClientErrorException clientError = assertThrows(HttpClientErrorException.class,
                () -> client.getKey(KEY_REF), "4xx 必须以 Spring 标准异常透传");
        assertEquals(400, clientError.getStatusCode().value(), "透传异常必须保留状态码");
        server.verify();
        server.reset();

        server.expect(requestTo(API_BASE + "/keys/" + KEY_REF))
                .andRespond(withServerError());
        HttpServerErrorException serverError = assertThrows(HttpServerErrorException.class,
                () -> client.getKey(KEY_REF), "5xx 必须以 Spring 标准异常透传");
        assertEquals(500, serverError.getStatusCode().value(), "透传异常必须保留状态码");
        server.verify();
    }

    @Test
    void shouldRejectMalformedBaseUrl() {
        KmsClientRestTemplateProperties properties = new KmsClientRestTemplateProperties();
        properties.setBaseUrl("https://kms.example.internal/with-path");
        assertThrows(RuntimeException.class, () -> new KmsRestTemplateClient(restTemplate, properties),
                "base-url 带路径必须构造失败");
    }
}
