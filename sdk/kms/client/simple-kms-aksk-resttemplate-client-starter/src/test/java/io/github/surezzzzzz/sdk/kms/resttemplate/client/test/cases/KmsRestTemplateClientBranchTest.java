package io.github.surezzzzzz.sdk.kms.resttemplate.client.test.cases;

import io.github.surezzzzzz.sdk.kms.client.KmsClient;
import io.github.surezzzzzz.sdk.kms.client.exception.KmsBadRequestException;
import io.github.surezzzzzz.sdk.kms.client.exception.KmsProtocolException;
import io.github.surezzzzzz.sdk.kms.client.model.KmsKeyPage;
import io.github.surezzzzzz.sdk.kms.client.model.KmsPolicy;
import io.github.surezzzzzz.sdk.kms.resttemplate.client.KmsRestTemplateClient;
import io.github.surezzzzzz.sdk.kms.resttemplate.client.configuration.KmsClientRestTemplateProperties;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withNoContent;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/**
 * KmsRestTemplateClient 分支与防御面测试：可选字段省略/携带、空集合、204、入参校验与协议违例。
 *
 * @author surezzzzzz
 */
@Slf4j
class KmsRestTemplateClientBranchTest {

    private static final String API_BASE = "https://kms.example.internal/api/kms";
    private static final String KEY_REF = "key-1";

    private RestTemplate restTemplate;
    private MockRestServiceServer server;
    private KmsClient client;

    @BeforeEach
    void setUp() {
        restTemplate = new RestTemplate();
        server = MockRestServiceServer.bindTo(restTemplate).build();
        KmsClientRestTemplateProperties properties = new KmsClientRestTemplateProperties();
        properties.setBaseUrl("https://kms.example.internal");
        client = new KmsRestTemplateClient(restTemplate, properties);
    }

    @Test
    void shouldCarryOptionalCryptoFieldsWhenProvided() {
        byte[] payload = "payload-1".getBytes(StandardCharsets.UTF_8);
        String encodedInput = Base64.getUrlEncoder().withoutPadding().encodeToString(payload);
        String encodedAad = Base64.getUrlEncoder().withoutPadding()
                .encodeToString("aad-1".getBytes(StandardCharsets.UTF_8));

        server.expect(requestTo(API_BASE + "/crypto/signatures"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(jsonPath("$.version").value(2))
                .andExpect(jsonPath("$.input").value(encodedInput))
                .andRespond(withSuccess(
                        "{\"keyRef\":\"" + KEY_REF + "\",\"version\":2,\"signature\":\"c2lnbmF0dXJl\"}",
                        MediaType.APPLICATION_JSON));
        assertEquals(Integer.valueOf(2), client.sign(KEY_REF, 2, payload).getVersion(),
                "sign 携带 version 时必须出现在请求体并回读版本");
        server.verify();
        server.reset();

        server.expect(requestTo(API_BASE + "/crypto/envelopes"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(jsonPath("$.aad").value(encodedAad))
                .andRespond(withSuccess("{\"envelope\":\"ZW52ZWxvcGU\"}", MediaType.APPLICATION_JSON));
        client.encrypt(KEY_REF, payload, "aad-1".getBytes(StandardCharsets.UTF_8));
        server.verify();
        server.reset();

        server.expect(requestTo(API_BASE + "/crypto/decryptions"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(jsonPath("$.aad").value(encodedAad))
                .andRespond(withSuccess("{\"plaintext\":\"cGxhaW50ZXh0\"}", MediaType.APPLICATION_JSON));
        client.decrypt("envelope".getBytes(StandardCharsets.UTF_8), "aad-1".getBytes(StandardCharsets.UTF_8));
        server.verify();
    }

    @Test
    void shouldOmitOptionalQueryAndBodyFieldsWhenAbsent() {
        server.expect(requestTo(API_BASE + "/keys"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("{\"items\":[],\"page\":1,\"size\":10,\"total\":0}",
                        MediaType.APPLICATION_JSON));
        KmsKeyPage page = client.listKeys(null, null, null, null, null, null);
        assertEquals(0, page.getItems().size(), "全可选参缺省时不得产生查询串");
        assertEquals(Long.valueOf(0), page.getTotal(), "空列表 total 必须为 0");
        server.verify();
        server.reset();

        server.expect(requestTo(API_BASE + "/keys/" + KEY_REF + "/public-key"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(
                        "{\"keyRef\":\"" + KEY_REF + "\",\"version\":1,\"algorithm\":\"ES256\","
                                + "\"state\":\"ACTIVE\",\"publicKey\":\"cHVia2V5\"}",
                        MediaType.APPLICATION_JSON));
        client.readPublicKey(KEY_REF, null);
        server.verify();
        server.reset();

        // createPolicy 可选字段全空时，请求体必须只含 principalId 与 operation（STRICT 省略断言）
        server.expect(requestTo(API_BASE + "/keys/" + KEY_REF + "/policies"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().json("{\"principalId\":\"iam:user-1\",\"operation\":\"SIGN\"}", true))
                .andRespond(withSuccess(
                        "{\"policyId\":\"policy-1\",\"keyRef\":\"" + KEY_REF + "\",\"principalId\":\"iam:user-1\","
                                + "\"operation\":\"SIGN\",\"rowVersion\":7}", MediaType.APPLICATION_JSON));
        KmsPolicy policy = client.createPolicy("idem-1", KEY_REF, "iam:user-1", null, "SIGN", null);
        assertNull(policy.getKeyVersion(), "缺省 keyVersion 响应字段必须解析为 null");
        assertNull(policy.getExpiresAt(), "缺省 expiresAt 响应字段必须解析为 null");
        server.verify();
    }

    @Test
    void shouldReturnEmptyCollectionsForListEndpoints() {
        server.expect(requestTo(API_BASE + "/keys?page=1&size=10"))
                .andRespond(withSuccess("{\"items\":[],\"page\":1,\"size\":10,\"total\":0}",
                        MediaType.APPLICATION_JSON));
        assertTrue(client.listKeys(1, 10, null, null, null, null).getItems().isEmpty(),
                "空密钥列表必须返回空集");
        server.verify();
        server.reset();

        server.expect(requestTo(API_BASE + "/keys/" + KEY_REF + "/policies"))
                .andRespond(withSuccess("{\"items\":[]}", MediaType.APPLICATION_JSON));
        assertTrue(client.listPolicies(KEY_REF).isEmpty(), "空策略列表必须返回空集");
        server.verify();
        server.reset();

        server.expect(requestTo(API_BASE + "/keys/" + KEY_REF + "/public-keys"))
                .andRespond(withSuccess("[]", MediaType.APPLICATION_JSON));
        assertTrue(client.listPublicKeys(KEY_REF).isEmpty(), "空公钥列表必须返回空集");
        server.verify();
    }

    @Test
    void shouldTreatNoContentAsSuccessForVoidCalls() {
        server.expect(requestTo(API_BASE + "/keys/" + KEY_REF + "/destruction"))
                .andExpect(method(HttpMethod.DELETE))
                .andRespond(withNoContent());
        client.cancelDestruction("idem-1", KEY_REF, 5L);
        server.verify();
        server.reset();

        server.expect(requestTo(API_BASE + "/keys/" + KEY_REF + "/policies/policy-1"))
                .andExpect(method(HttpMethod.DELETE))
                .andRespond(withNoContent());
        client.revokePolicy("idem-1", KEY_REF, "policy-1", 7L);
        server.verify();
    }

    @Test
    void shouldRejectNullAndBlankInputsBeforeAnyRequest() {
        assertThrows(KmsBadRequestException.class, () -> client.sign(KEY_REF, null, null),
                "二进制字段为 null 必须在发出请求前失败");
        // 空二进制数组按 core 契约放行（requireValue 仅拒 null），内容由 server 校验
        assertThrows(KmsBadRequestException.class, () -> client.createKey(null, "alias-1", "SIGN", "ES256"),
                "幂等键为 null 必须在发出请求前失败");
        assertThrows(KmsBadRequestException.class, () -> client.getKey(" "),
                "资源标识为空白必须在发出请求前失败");
        // 零期望下任何真实 HTTP 请求都会抛 AssertionError 使上述断言变红，等价于"未发出请求"证明
    }

    @Test
    void shouldFailOnProtocolViolationsInSuccessResponses() {
        server.expect(requestTo(API_BASE + "/keys/" + KEY_REF))
                .andRespond(withSuccess(
                        "{\"keyRef\":\"" + KEY_REF + "\",\"keyAlias\":\"alias-1\",\"purpose\":\"SIGN\","
                                + "\"algorithm\":\"ES256\",\"state\":\"ACTIVE\",\"activeVersion\":1,"
                                + "\"rowVersion\":4,\"createdAt\":\"2026-01-01 00:00:00Z\","
                                + "\"updatedAt\":\"2026-01-01T00:00:00.000Z\"}", MediaType.APPLICATION_JSON));
        assertThrows(KmsProtocolException.class, () -> client.getKey(KEY_REF),
                "时间格式偏离 UTC 毫秒契约必须协议失败");
        server.verify();
        server.reset();

        server.expect(requestTo(API_BASE + "/crypto/signatures"))
                .andRespond(withSuccess(
                        "{\"keyRef\":\"" + KEY_REF + "\",\"version\":1,\"signature\":\"c2lnbmF0dXJl=\"}",
                        MediaType.APPLICATION_JSON));
        assertThrows(KmsProtocolException.class, () -> client.sign(KEY_REF, null,
                        "payload-1".getBytes(StandardCharsets.UTF_8)),
                "带 padding 的 Base64url 必须按协议失败拒绝");
        server.verify();
        server.reset();

        server.expect(requestTo(API_BASE + "/keys/" + KEY_REF))
                .andRespond(withSuccess(
                        "{\"keyRef\":\"" + KEY_REF + "\",\"keyAlias\":\"alias-1\",\"purpose\":\"SIGN\","
                                + "\"algorithm\":\"ES256\",\"state\":\"FROZEN\",\"activeVersion\":1,"
                                + "\"rowVersion\":4,\"createdAt\":\"2026-01-01T00:00:00.000Z\","
                                + "\"updatedAt\":\"2026-01-01T00:00:00.000Z\"}", MediaType.APPLICATION_JSON));
        assertThrows(KmsProtocolException.class, () -> client.getKey(KEY_REF),
                "未知枚举值必须按协议失败拒绝");
        server.verify();
        server.reset();

        server.expect(requestTo(API_BASE + "/keys/" + KEY_REF))
                .andRespond(withSuccess(
                        "{\"keyAlias\":\"alias-1\",\"purpose\":\"SIGN\",\"algorithm\":\"ES256\","
                                + "\"state\":\"ACTIVE\",\"activeVersion\":1,\"rowVersion\":4,"
                                + "\"createdAt\":\"2026-01-01T00:00:00.000Z\","
                                + "\"updatedAt\":\"2026-01-01T00:00:00.000Z\"}", MediaType.APPLICATION_JSON));
        assertThrows(KmsProtocolException.class, () -> client.getKey(KEY_REF),
                "缺失必填字段必须按协议失败拒绝");
        server.verify();
    }

    @Test
    void shouldEncodeReservedCharactersInPathSegments() {
        server.expect(requestTo(API_BASE + "/keys/team%2Fa%20b"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(
                        "{\"keyRef\":\"team/a b\",\"keyAlias\":\"alias-1\",\"purpose\":\"SIGN\","
                                + "\"algorithm\":\"ES256\",\"state\":\"ACTIVE\",\"activeVersion\":1,"
                                + "\"rowVersion\":4,\"createdAt\":\"2026-01-01T00:00:00.000Z\","
                                + "\"updatedAt\":\"2026-01-01T00:00:00.000Z\"}", MediaType.APPLICATION_JSON));
        assertEquals("team/a b", client.getKey("team/a b").getKeyRef(),
                "资源标识含保留字符时必须按独立路径段编码（/ 与空格转义）");
        server.verify();
    }

    @Test
    void shouldParseTrailingZeroMillisAndTruncateOutboundTime() {
        server.expect(requestTo(API_BASE + "/keys/" + KEY_REF + "/destruction"))
                .andExpect(method(HttpMethod.PUT))
                .andExpect(content().json("{\"dueAt\":\"2026-07-27T01:02:03.000Z\",\"expectedRowVersion\":4}",
                        true))
                .andRespond(withSuccess(
                        "{\"keyRef\":\"" + KEY_REF + "\",\"keyAlias\":\"alias-1\",\"purpose\":\"SIGN\","
                                + "\"algorithm\":\"ES256\",\"state\":\"PENDING_DESTRUCTION\",\"activeVersion\":1,"
                                + "\"rowVersion\":5,\"createdAt\":\"2026-07-26T00:00:00.000Z\","
                                + "\"updatedAt\":\"2026-07-27T01:02:03.000Z\"}", MediaType.APPLICATION_JSON));
        client.scheduleDestruction("idem-1", KEY_REF, Instant.parse("2026-07-27T01:02:03Z"), 4L);
        server.verify();
    }
}
