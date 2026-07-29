package io.github.surezzzzzz.sdk.kms.client.test.cases;

import io.github.surezzzzzz.sdk.kms.client.client.RestTemplateKmsClient;
import io.github.surezzzzzz.sdk.kms.client.exception.*;
import io.github.surezzzzzz.sdk.kms.client.model.KmsKeyPage;
import io.github.surezzzzzz.sdk.kms.client.model.KmsPolicy;
import io.github.surezzzzzz.sdk.kms.client.model.KmsPublicKey;
import io.github.surezzzzzz.sdk.kms.client.model.KmsSignature;
import io.github.surezzzzzz.sdk.kms.client.support.KmsHttpErrorMapper;
import io.github.surezzzzzz.sdk.kms.client.support.KmsHttpExecutor;
import io.github.surezzzzzz.sdk.kms.client.support.KmsJsonCodec;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.DefaultResponseErrorHandler;
import org.springframework.web.client.RestTemplate;

import java.net.URI;
import java.time.Instant;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

/**
 * RestTemplate KMS Client HTTP 契约测试。
 *
 * <p>使用 MockRestServiceServer 验证 Client 传输协议 格式、状态映射和拒绝路径，不启动或依赖 KMS Server。</p>
 *
 * @author surezzzzzz
 */
@Slf4j
class RestTemplateKmsClientTest {

    private static final String API_BASE = "https://kms.example.internal/api/v1/kms";
    private static final String FIXED_TIME = "2026-07-27T01:02:03.004Z";
    private static final String KEY_REF = "key-1";
    private static final String IDEMPOTENCY_KEY = "operation-1";

    /**
     * 以含有额外字段的错误体验证状态映射，确保异常不会拼接原始响应内容。
     */
    private static void assertStatus(HttpStatus status, Class<? extends RuntimeException> expectedType) {
        RestTemplate restTemplate = restTemplate();
        MockRestServiceServer server = MockRestServiceServer.bindTo(restTemplate).build();
        server.expect(once(), requestTo(API_BASE + "/keys/key-1"))
                .andRespond(withStatus(status).contentType(MediaType.APPLICATION_JSON)
                        .body("{\"message\":\"安全消息\",\"timestamp\":\"" + FIXED_TIME
                                + "\",\"requestId\":\"request-1\",\"secret\":\"forbidden\"}"));
        RuntimeException exception = assertThrows(expectedType, () -> client(restTemplate, 4096, 4096).getKey(KEY_REF),
                "HTTP 状态必须映射为预期 Client 异常");
        assertFalse(exception.getMessage().contains("forbidden"), "异常消息不得拼接原始错误响应");
        server.verify();
    }

    private static RestTemplateKmsClient client(RestTemplate restTemplate, int maxRequestBytes, int maxResponseBytes) {
        return new RestTemplateKmsClient(URI.create(API_BASE),
                new KmsHttpExecutor(restTemplate, new KmsJsonCodec(), new KmsHttpErrorMapper(), maxRequestBytes,
                        maxResponseBytes));
    }

    /**
     * 关闭 RestTemplate 默认状态抛错，由 KmsHttpExecutor 统一执行协议安全映射。
     */
    private static RestTemplate restTemplate() {
        RestTemplate restTemplate = new RestTemplate();
        restTemplate.setErrorHandler(new DefaultResponseErrorHandler() {
            @Override
            public boolean hasError(org.springframework.http.client.ClientHttpResponse response) {
                return false;
            }
        });
        return restTemplate;
    }

    private static String keyJson() {
        return "{\"keyRef\":\"key-1\",\"keyAlias\":\"alias\",\"purpose\":\"SIGN\",\"algorithm\":\"ES256\","
                + "\"state\":\"DISABLED\",\"activeVersion\":1,\"rowVersion\":5,\"createdAt\":\"" + FIXED_TIME
                + "\",\"updatedAt\":\"" + FIXED_TIME + "\"}";
    }

    private static String policyJson() {
        return "{\"policyId\":\"policy-1\",\"keyRef\":\"key-1\",\"principalId\":\"service-a\",\"keyVersion\":2,"
                + "\"operation\":\"SIGN\",\"expiresAt\":\"" + FIXED_TIME + "\",\"rowVersion\":7}";
    }

    private static String publicKeyJson(String encodedPublicKey) {
        return "{\"keyRef\":\"key-1\",\"version\":2,\"algorithm\":\"ES256\",\"state\":\"RETIRED\","
                + "\"publicKey\":\"" + encodedPublicKey + "\"}";
    }

    private static String base64(byte[] value) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(value);
    }

    @Test
    void shouldCallAllKeyManagementEndpointsWithContractFields() {
        RestTemplate restTemplate = restTemplate();
        MockRestServiceServer server = MockRestServiceServer.bindTo(restTemplate).build();
        RestTemplateKmsClient client = client(restTemplate, 4096, 4096);

        server.expect(once(), requestTo(API_BASE + "/keys"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("Idempotency-Key", IDEMPOTENCY_KEY))
                .andExpect(content().json("{\"keyAlias\":\"alias\",\"purpose\":\"SIGN\",\"algorithm\":\"ES256\"}"))
                .andRespond(withStatus(HttpStatus.CREATED)
                        .location(URI.create("/api/v1/kms/keys/key-1"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(keyJson()));
        assertEquals(KEY_REF, client.createKey(IDEMPOTENCY_KEY, "alias", "SIGN", "ES256").getKeyRef(),
                "创建逻辑密钥必须返回服务端资源");
        server.verify();
        server.reset();

        server.expect(once(), requestTo(API_BASE + "/keys/key-1"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(keyJson(), MediaType.APPLICATION_JSON));
        assertEquals(KEY_REF, client.getKey(KEY_REF).getKeyRef(), "查询逻辑密钥必须返回目标资源");
        server.verify();
        server.reset();

        server.expect(once(), requestTo(API_BASE + "/keys?page=2&size=20&alias=alias&purpose=SIGN&algorithm=ES256&state=ACTIVE"))
                .andExpect(method(HttpMethod.GET))
                .andExpect(queryParam("page", "2"))
                .andExpect(queryParam("size", "20"))
                .andRespond(withSuccess("{\"items\":[" + keyJson() + "],\"page\":2,\"size\":20,\"total\":1}",
                        MediaType.APPLICATION_JSON));
        KmsKeyPage page = client.listKeys(Integer.valueOf(2), Integer.valueOf(20), "alias", "SIGN", "ES256", "ACTIVE");
        log.info("逻辑密钥分页返回数量: {}, 当前页: {}", page.getItems().size(), page.getPage());
        assertEquals(Integer.valueOf(1), Integer.valueOf(page.getItems().size()), "分页结果必须解析 items");
        assertEquals(Long.valueOf(1L), page.getTotal(), "分页结果必须解析总数量");
        server.verify();
        server.reset();

        server.expect(once(), requestTo(API_BASE + "/keys/key-1/state"))
                .andExpect(method(HttpMethod.PATCH))
                .andExpect(header("Idempotency-Key", IDEMPOTENCY_KEY))
                .andExpect(content().json("{\"state\":\"DISABLED\",\"expectedRowVersion\":4}"))
                .andRespond(withSuccess(keyJson(), MediaType.APPLICATION_JSON));
        assertEquals(KEY_REF, client.changeKeyState(IDEMPOTENCY_KEY, KEY_REF, "DISABLED", 4L).getKeyRef(),
                "状态修改必须返回当前资源");
        server.verify();
        server.reset();

        server.expect(once(), requestTo(API_BASE + "/keys/key-1/versions"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("Idempotency-Key", IDEMPOTENCY_KEY))
                .andExpect(content().json("{\"expectedRowVersion\":4}"))
                .andRespond(withSuccess(keyJson(), MediaType.APPLICATION_JSON));
        assertEquals(KEY_REF, client.rotateKey(IDEMPOTENCY_KEY, KEY_REF, 4L).getKeyRef(), "轮换必须返回当前资源");
        server.verify();
        server.reset();

        server.expect(once(), requestTo(API_BASE + "/keys/key-1/destruction"))
                .andExpect(method(HttpMethod.PUT))
                .andExpect(header("Idempotency-Key", IDEMPOTENCY_KEY))
                .andExpect(content().json("{\"destroyAfter\":\"2026-07-27T01:02:03.004Z\",\"expectedRowVersion\":4}"))
                .andRespond(withSuccess(keyJson(), MediaType.APPLICATION_JSON));
        assertEquals(KEY_REF, client.scheduleDestruction(IDEMPOTENCY_KEY, KEY_REF,
                Instant.parse(FIXED_TIME), 4L).getKeyRef(), "销毁安排必须返回当前资源");
        server.verify();
        server.reset();

        server.expect(once(), requestTo(API_BASE + "/keys/key-1/destruction"))
                .andExpect(method(HttpMethod.DELETE))
                .andExpect(header("Idempotency-Key", IDEMPOTENCY_KEY))
                .andExpect(content().json("{\"expectedRowVersion\":4}"))
                .andRespond(withNoContent());
        client.cancelDestruction(IDEMPOTENCY_KEY, KEY_REF, 4L);

        server.verify();
    }

    @Test
    void shouldCallPolicyEndpointsWithCorrectConcurrencyContract() {
        RestTemplate restTemplate = restTemplate();
        MockRestServiceServer server = MockRestServiceServer.bindTo(restTemplate).build();
        RestTemplateKmsClient client = client(restTemplate, 4096, 4096);
        String policyJson = policyJson();

        server.expect(once(), requestTo(API_BASE + "/keys/key-1/policies"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("Idempotency-Key", IDEMPOTENCY_KEY))
                .andExpect(content().json("{\"principalId\":\"service-a\",\"operation\":\"SIGN\",\"keyVersion\":2,\"expiresAt\":\"2026-07-27T01:02:03.004Z\"}"))
                .andRespond(withStatus(HttpStatus.CREATED)
                        .location(URI.create("/api/v1/kms/keys/key-1/policies/policy-1"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(policyJson));
        KmsPolicy created = client.createPolicy(IDEMPOTENCY_KEY, KEY_REF, "service-a", Integer.valueOf(2), "SIGN",
                Instant.parse(FIXED_TIME));
        assertEquals("policy-1", created.getPolicyId(), "创建策略必须返回服务端资源");
        server.verify();
        server.reset();

        server.expect(once(), requestTo(API_BASE + "/keys/key-1/policies"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("{\"items\":[" + policyJson + "]}", MediaType.APPLICATION_JSON));
        assertEquals(Integer.valueOf(1), Integer.valueOf(client.listPolicies(KEY_REF).size()), "策略列表必须解析 items");
        server.verify();
        server.reset();

        server.expect(once(), requestTo(API_BASE + "/keys/key-1/policies/policy-1"))
                .andExpect(method(HttpMethod.DELETE))
                .andExpect(header("Idempotency-Key", IDEMPOTENCY_KEY))
                .andExpect(content().json("{\"expectedRowVersion\":7}"))
                .andRespond(withNoContent());
        client.revokePolicy(IDEMPOTENCY_KEY, KEY_REF, "policy-1", 7L);

        log.info("策略管理契约已覆盖创建、查询和撤销，未记录策略主体外的敏感负载");
        server.verify();
    }

    @Test
    void shouldParseMissingAndNullOptionalPolicyFields() {
        RestTemplate restTemplate = restTemplate();
        MockRestServiceServer server = MockRestServiceServer.bindTo(restTemplate).build();
        RestTemplateKmsClient client = client(restTemplate, 4096, 4096);
        String missingOptionalFields = "{\"policyId\":\"policy-missing\",\"keyRef\":\"key-1\",\"principalId\":\"service-a\","
                + "\"operation\":\"SIGN\",\"rowVersion\":7}";
        String nullOptionalFields = "{\"policyId\":\"policy-null\",\"keyRef\":\"key-1\",\"principalId\":\"service-a\","
                + "\"keyVersion\":null,\"operation\":\"SIGN\",\"expiresAt\":null,\"rowVersion\":8}";

        server.expect(once(), requestTo(API_BASE + "/keys/key-1/policies"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("{\"items\":[" + missingOptionalFields + "," + nullOptionalFields + "]}",
                        MediaType.APPLICATION_JSON));
        java.util.List<KmsPolicy> policies = client.listPolicies(KEY_REF);

        log.info("可选策略字段缺失和显式 null 的返回数量: {}", policies.size());
        assertNull(policies.get(0).getKeyVersion(), "缺失的策略版本必须解析为 null");
        assertNull(policies.get(0).getExpiresAt(), "缺失的策略到期时间必须解析为 null");
        assertNull(policies.get(1).getKeyVersion(), "显式 null 的策略版本必须解析为 null");
        assertNull(policies.get(1).getExpiresAt(), "显式 null 的策略到期时间必须解析为 null");
        server.verify();
    }

    @Test
    void shouldCallCryptoAndPublicKeyEndpointsWithoutRetry() {
        RestTemplate restTemplate = restTemplate();
        MockRestServiceServer server = MockRestServiceServer.bindTo(restTemplate).build();
        RestTemplateKmsClient client = client(restTemplate, 4096, 4096);
        byte[] signingInput = new byte[]{1, 2, 3};
        byte[] signature = new byte[64];
        byte[] envelope = new byte[]{4, 5, 6};
        byte[] publicKey = new byte[]{7, 8, 9};
        String encodedSignature = base64(signature);
        String encodedEnvelope = base64(envelope);
        String encodedPublicKey = base64(publicKey);

        server.expect(once(), requestTo(API_BASE + "/crypto/signatures"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(content().json("{\"keyRef\":\"key-1\",\"input\":\"AQID\",\"version\":2}"))
                .andRespond(withSuccess("{\"keyRef\":\"key-1\",\"version\":2,\"signature\":\"" + encodedSignature + "\"}",
                        MediaType.APPLICATION_JSON));
        KmsSignature signed = client.sign(KEY_REF, Integer.valueOf(2), signingInput);
        assertArrayEquals(signature, signed.getSignature(), "签名必须按无 padding Base64url 解析");
        server.verify();
        server.reset();

        server.expect(once(), requestTo(API_BASE + "/crypto/verifications"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().json("{\"keyRef\":\"key-1\",\"input\":\"AQID\",\"signature\":\"" + encodedSignature + "\"}"))
                .andRespond(withSuccess("{\"valid\":false}", MediaType.APPLICATION_JSON));
        assertFalse(client.verify(KEY_REF, null, signingInput, signature), "验签失败是正常业务结果而非异常");
        server.verify();
        server.reset();

        server.expect(once(), requestTo(API_BASE + "/crypto/envelopes"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().json("{\"keyRef\":\"key-1\",\"plaintext\":\"AQID\"}"))
                .andRespond(withSuccess("{\"envelope\":\"" + encodedEnvelope + "\"}", MediaType.APPLICATION_JSON));
        assertArrayEquals(envelope, client.encrypt(KEY_REF, signingInput, null), "加密必须返回版本化信封字节");
        server.verify();
        server.reset();

        server.expect(once(), requestTo(API_BASE + "/crypto/decryptions"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().json("{\"envelope\":\"" + encodedEnvelope + "\"}"))
                .andRespond(withSuccess("{\"plaintext\":\"AQID\"}", MediaType.APPLICATION_JSON));
        assertArrayEquals(signingInput, client.decrypt(envelope, null), "解密必须返回原始明文字节");
        server.verify();
        server.reset();

        server.expect(once(), requestTo(API_BASE + "/keys/key-1/public-key?version=2"))
                .andExpect(method(HttpMethod.GET))
                .andExpect(queryParam("version", "2"))
                .andRespond(withSuccess(publicKeyJson(encodedPublicKey), MediaType.APPLICATION_JSON));
        KmsPublicKey single = client.readPublicKey(KEY_REF, Integer.valueOf(2));
        assertArrayEquals(publicKey, single.getPublicKey(), "单个公钥必须解析 Base64url 字节");
        server.verify();
        server.reset();

        server.expect(once(), requestTo(API_BASE + "/keys/key-1/public-keys"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("[" + publicKeyJson(encodedPublicKey) + "]", MediaType.APPLICATION_JSON));
        assertEquals(Integer.valueOf(1), Integer.valueOf(client.listPublicKeys(KEY_REF).size()), "公钥集合必须接受直接数组响应");

        log.info("密码学与公钥接口各执行一次，签名、明文、信封和公钥内容均未输出日志");
        server.verify();
    }

    @Test
    void shouldEncodeReservedPathSegmentsAndFormatMillis() {
        RestTemplate restTemplate = restTemplate();
        MockRestServiceServer server = MockRestServiceServer.bindTo(restTemplate).build();
        RestTemplateKmsClient client = client(restTemplate, 4096, 4096);
        String keyRef = "key/a b";
        String policyId = "policy/a b";

        server.expect(once(), requestTo(API_BASE + "/keys/key%2Fa%20b"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(keyJson(), MediaType.APPLICATION_JSON));
        client.getKey(keyRef);
        server.verify();
        server.reset();

        server.expect(once(), requestTo(API_BASE + "/keys/key%2Fa%20b/policies/policy%2Fa%20b"))
                .andExpect(method(HttpMethod.DELETE))
                .andExpect(content().json("{\"expectedRowVersion\":4}"))
                .andRespond(withNoContent());
        client.revokePolicy(IDEMPOTENCY_KEY, keyRef, policyId, 4L);

        log.info("路径参数已使用 segment 编码，时间文本契约已由销毁与策略用例覆盖");
        server.verify();
    }

    @Test
    void shouldMapAllHttpStatusFamiliesAndHideRawErrorBody() {
        assertStatus(HttpStatus.BAD_REQUEST, KmsBadRequestException.class);
        assertStatus(HttpStatus.METHOD_NOT_ALLOWED, KmsBadRequestException.class);
        assertStatus(HttpStatus.UNAUTHORIZED, KmsUnauthenticatedException.class);
        assertStatus(HttpStatus.FORBIDDEN, KmsUnauthorizedException.class);
        assertStatus(HttpStatus.NOT_FOUND, KmsNotFoundException.class);
        assertStatus(HttpStatus.CONFLICT, KmsConflictException.class);
        assertStatus(HttpStatus.PAYLOAD_TOO_LARGE, KmsPayloadTooLargeException.class);
        assertStatus(HttpStatus.UNSUPPORTED_MEDIA_TYPE, KmsBadRequestException.class);
        assertStatus(HttpStatus.UNPROCESSABLE_ENTITY, KmsUnprocessableException.class);
        assertStatus(HttpStatus.SERVICE_UNAVAILABLE, KmsServiceUnavailableException.class);
        assertStatus(HttpStatus.BAD_GATEWAY, KmsServiceUnavailableException.class);
        log.info("HTTP 状态映射已覆盖 4xx、503 与其他 5xx，不包含原始错误响应");
    }

    @Test
    void shouldRejectMalformedProtocolAndOversizedPayloads() {
        RestTemplate malformedTemplate = restTemplate();
        MockRestServiceServer malformedServer = MockRestServiceServer.bindTo(malformedTemplate).build();
        malformedServer.expect(once(), requestTo(API_BASE + "/keys/key-1"))
                .andRespond(withSuccess("not-json", MediaType.APPLICATION_JSON));
        assertThrows(KmsProtocolException.class, () -> client(malformedTemplate, 4096, 4096).getKey(KEY_REF),
                "非 JSON 成功响应必须视为协议错误");
        malformedServer.verify();

        RestTemplate nullTemplate = restTemplate();
        MockRestServiceServer nullServer = MockRestServiceServer.bindTo(nullTemplate).build();
        nullServer.expect(once(), requestTo(API_BASE + "/keys/key-1"))
                .andRespond(withSuccess("null", MediaType.APPLICATION_JSON));
        assertThrows(KmsProtocolException.class, () -> client(nullTemplate, 4096, 4096).getKey(KEY_REF),
                "JSON null 成功响应必须视为协议错误");
        nullServer.verify();

        RestTemplate contentTypeTemplate = restTemplate();
        MockRestServiceServer contentTypeServer = MockRestServiceServer.bindTo(contentTypeTemplate).build();
        contentTypeServer.expect(once(), requestTo(API_BASE + "/keys/key-1"))
                .andRespond(withSuccess(keyJson(), MediaType.TEXT_PLAIN));
        assertThrows(KmsProtocolException.class, () -> client(contentTypeTemplate, 4096, 4096).getKey(KEY_REF),
                "非 JSON Content-Type 成功响应必须视为协议错误");
        contentTypeServer.verify();

        RestTemplate invalidFieldTemplate = restTemplate();
        MockRestServiceServer invalidFieldServer = MockRestServiceServer.bindTo(invalidFieldTemplate).build();
        invalidFieldServer.expect(once(), requestTo(API_BASE + "/keys/key-1"))
                .andRespond(withSuccess(keyJson().replace("\"purpose\":\"SIGN\"", "\"purpose\":\"UNKNOWN\""),
                        MediaType.APPLICATION_JSON));
        assertThrows(KmsProtocolException.class, () -> client(invalidFieldTemplate, 4096, 4096).getKey(KEY_REF),
                "服务端未知枚举必须视为协议错误");
        invalidFieldServer.verify();

        RestTemplate invalidTimeTemplate = restTemplate();
        MockRestServiceServer invalidTimeServer = MockRestServiceServer.bindTo(invalidTimeTemplate).build();
        invalidTimeServer.expect(once(), requestTo(API_BASE + "/keys/key-1"))
                .andRespond(withSuccess(keyJson().replace(FIXED_TIME, "2026-07-27T01:02:03Z"),
                        MediaType.APPLICATION_JSON));
        assertThrows(KmsProtocolException.class, () -> client(invalidTimeTemplate, 4096, 4096).getKey(KEY_REF),
                "服务端非固定毫秒 UTC 时间必须视为协议错误");
        invalidTimeServer.verify();

        RestTemplate paddingTemplate = restTemplate();
        MockRestServiceServer paddingServer = MockRestServiceServer.bindTo(paddingTemplate).build();
        paddingServer.expect(once(), requestTo(API_BASE + "/crypto/signatures"))
                .andRespond(withSuccess("{\"keyRef\":\"key-1\",\"version\":2,\"signature\":\"AQI=\"}",
                        MediaType.APPLICATION_JSON));
        assertThrows(KmsProtocolException.class, () -> client(paddingTemplate, 4096, 4096)
                .sign(KEY_REF, Integer.valueOf(2), new byte[]{1}), "带 padding 的 Base64url 响应必须拒绝");
        paddingServer.verify();

        RestTemplate oversizedTemplate = restTemplate();
        MockRestServiceServer oversizedServer = MockRestServiceServer.bindTo(oversizedTemplate).build();
        oversizedServer.expect(once(), requestTo(API_BASE + "/keys/key-1"))
                .andRespond(withSuccess("{\"x\":\"too-large\"}", MediaType.APPLICATION_JSON));
        assertThrows(KmsResponseTooLargeException.class, () -> client(oversizedTemplate, 4096, 4).getKey(KEY_REF),
                "响应超过配置上限时必须拒绝");
        oversizedServer.verify();

        RestTemplate requestTemplate = restTemplate();
        MockRestServiceServer requestServer = MockRestServiceServer.bindTo(requestTemplate).build();
        assertThrows(KmsPayloadTooLargeException.class, () -> client(requestTemplate, 4, 4096)
                .encrypt(KEY_REF, new byte[]{1, 2, 3}, null), "编码后的请求超过上限时必须拒绝");
        requestServer.verify();
        log.info("协议、Base64url、请求上限和响应上限拒绝路径均已覆盖");
    }
}
