package io.github.surezzzzzz.sdk.kms.client.test.cases;

import com.fasterxml.jackson.databind.JsonNode;
import io.github.surezzzzzz.sdk.kms.client.auth.KmsCredentialApplier;
import io.github.surezzzzzz.sdk.kms.client.exception.*;
import io.github.surezzzzzz.sdk.kms.client.model.KmsKey;
import io.github.surezzzzzz.sdk.kms.client.model.KmsKeyPage;
import io.github.surezzzzzz.sdk.kms.client.model.KmsSigningResult;
import io.github.surezzzzzz.sdk.kms.client.port.KeyEncryptionPort;
import io.github.surezzzzzz.sdk.kms.client.port.OwnerPublicKeyPort;
import io.github.surezzzzzz.sdk.kms.client.port.OwnerSignerPort;
import io.github.surezzzzzz.sdk.kms.client.support.KmsClientUriHelper;
import io.github.surezzzzzz.sdk.kms.client.support.KmsHttpErrorMapper;
import io.github.surezzzzzz.sdk.kms.client.support.KmsJsonCodec;
import io.github.surezzzzzz.sdk.kms.client.support.KmsValidationHelper;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.time.Instant;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * core 契约全量测试：错误映射/JSON 编解码/URI 校验/参数校验/模型不可变/端口委托/SPI 中立。
 *
 * @author surezzzzzz
 */
class KmsCoreContractTest {

    private final KmsHttpErrorMapper errorMapper = new KmsHttpErrorMapper();
    private final KmsJsonCodec codec = new KmsJsonCodec();

    // ==================== 错误映射 ====================

    @Test
    void shouldMapHttpStatusToExactExceptionFamily() {
        assertInstanceOf(KmsBadRequestException.class, errorMapper.map(400, "POST", "/keys", "x", null, null));
        assertInstanceOf(io.github.surezzzzzz.sdk.kms.client.exception.KmsUnauthenticatedException.class,
                errorMapper.map(401, "GET", "/me", "x", null, null));
        assertInstanceOf(KmsUnauthorizedException.class, errorMapper.map(403, "GET", "/keys", "x", null, null));
        assertInstanceOf(KmsNotFoundException.class, errorMapper.map(404, "GET", "/keys/k", "x", null, null));
        assertInstanceOf(KmsConflictException.class, errorMapper.map(409, "PATCH", "/keys/k/state", "x", null, null));
        assertInstanceOf(io.github.surezzzzzz.sdk.kms.client.exception.KmsPayloadTooLargeException.class,
                errorMapper.map(413, "POST", "/crypto/signatures", "x", null, null));
        assertInstanceOf(io.github.surezzzzzz.sdk.kms.client.exception.KmsUnprocessableException.class,
                errorMapper.map(422, "POST", "/keys", "x", null, null));
        assertInstanceOf(io.github.surezzzzzz.sdk.kms.client.exception.KmsServiceUnavailableException.class,
                errorMapper.map(503, "GET", "/keys", "x", null, null));
    }

    // ==================== JSON 编解码 ====================

    @Test
    void jsonCodecShouldSerializeAndDeserializeRoundTrip() {
        KmsKey key = KmsKey.builder().keyRef("k-1").keyAlias("test").purpose("SIGN")
                .algorithm("ES256").state("ACTIVE").activeVersion(1).rowVersion(0L).build();
        byte[] json = codec.write(key);
        JsonNode node = codec.read(json);
        assertEquals("k-1", node.path("keyRef").asText());
        assertEquals("SIGN", node.path("purpose").asText());
    }

    @Test
    void jsonCodecShouldTolerateUnknownFields() {
        // 前向兼容：server 新增字段，老 client 不炸
        byte[] json = "{\"keyRef\":\"k-1\",\"keyAlias\":\"a\",\"purpose\":\"SIGN\",\"algorithm\":\"ES256\",\"state\":\"ACTIVE\",\"activeVersion\":1,\"rowVersion\":0,\"newFieldFromFutureServer\":\"whatever\"}".getBytes();
        assertDoesNotThrow(() -> codec.read(json));
    }

    @Test
    void jsonCodecShouldSerializeInstantAsIso8601() {
        // 时间必须 ISO-8601 UTC 文本，不是 epoch 时间戳
        Map<String, Instant> input = new LinkedHashMap<>();
        input.put("ts", Instant.parse("2026-10-03T12:00:00Z"));
        byte[] json = codec.write(input);
        JsonNode node = codec.read(json);
        assertTrue(node.path("ts").asText().startsWith("2026-10-03T"), "时间应输出 ISO-8601 文本");
    }

    @Test
    void jsonCodecShouldRejectInvalidJson() {
        assertThrows(KmsProtocolException.class, () -> codec.read("not-json{".getBytes()));
    }

    @Test
    void jsonCodecShouldRejectUnserializableObject() {
        assertThrows(KmsProtocolException.class, () -> codec.write(new Object() {
            // 匿名类无属性，jackson 序列化失败
        }));
    }

    // ==================== URI 校验 ====================

    @Test
    void uriHelperShouldAcceptHttpAndHttpsOrigins() {
        assertEquals(URI.create("http://localhost:8080/api/kms"), KmsClientUriHelper.apiBaseUri("http://localhost:8080"));
        assertEquals(URI.create("https://kms.example.com/api/kms"), KmsClientUriHelper.apiBaseUri("https://kms.example.com"));
    }

    @Test
    void uriHelperShouldRejectPathInBaseUrl() {
        assertThrows(KmsClientConfigurationException.class, () -> KmsClientUriHelper.apiBaseUri("http://host/prefix"));
    }

    @Test
    void uriHelperShouldRejectQueryAndFragment() {
        assertThrows(KmsClientConfigurationException.class, () -> KmsClientUriHelper.apiBaseUri("http://host?q=1"));
        assertThrows(KmsClientConfigurationException.class, () -> KmsClientUriHelper.apiBaseUri("http://host#f"));
    }

    @Test
    void uriHelperShouldRejectUserInfo() {
        assertThrows(KmsClientConfigurationException.class, () -> KmsClientUriHelper.apiBaseUri("http://user:pass@host"));
    }

    @Test
    void uriHelperShouldRejectNullAndEmpty() {
        assertThrows(KmsClientConfigurationException.class, () -> KmsClientUriHelper.apiBaseUri(null));
        assertThrows(KmsClientConfigurationException.class, () -> KmsClientUriHelper.apiBaseUri("  "));
    }

    @Test
    void uriHelperShouldRejectNonHttpScheme() {
        assertThrows(KmsClientConfigurationException.class, () -> KmsClientUriHelper.apiBaseUri("ftp://host"));
    }

    @Test
    void fixedApiBaseUriShouldValidatePath() {
        assertDoesNotThrow(() -> KmsClientUriHelper.fixedApiBaseUri(URI.create("http://h/api/kms")));
        assertThrows(KmsClientConfigurationException.class, () -> KmsClientUriHelper.fixedApiBaseUri(URI.create("http://h/wrong")));
    }

    // ==================== 参数校验 ====================

    @Test
    void validationHelperShouldRejectNullAndBlank() {
        assertThrows(KmsBadRequestException.class, () -> KmsValidationHelper.requireText(null));
        assertThrows(KmsBadRequestException.class, () -> KmsValidationHelper.requireText(""));
        assertThrows(KmsBadRequestException.class, () -> KmsValidationHelper.requireText("  "));
        assertEquals("ok", KmsValidationHelper.requireText("ok"));
    }

    @Test
    void validationHelperShouldRejectNullObject() {
        assertThrows(KmsBadRequestException.class, () -> KmsValidationHelper.requireValue(null));
        assertEquals("x", KmsValidationHelper.requireValue("x"));
    }

    // ==================== 模型不可变性与 Builder ====================

    @Test
    void modelsShouldBeImmutableValueObjects() {
        // @Value 生成全字段 getter + equals/hashCode + toString；不可变性=无 setter
        KmsKey key = KmsKey.builder().keyRef("k").keyAlias("a").purpose("SIGN")
                .algorithm("ES256").state("ACTIVE").activeVersion(1).rowVersion(0L).build();
        assertNotNull(key.getKeyRef());
        assertEquals("SIGN", key.getPurpose());
        // 反射确认无 setter（@Value 不生成）
        assertTrue(Arrays.stream(KmsKey.class.getDeclaredMethods())
                .noneMatch(m -> m.getName().startsWith("set")), "@Value 模型不应有 setter");
    }

    @Test
    void modelsShouldImplementEqualsAndHashCode() {
        KmsKey a = KmsKey.builder().keyRef("k").keyAlias("x").purpose("SIGN")
                .algorithm("ES256").state("ACTIVE").activeVersion(1).rowVersion(0L).build();
        KmsKey b = KmsKey.builder().keyRef("k").keyAlias("x").purpose("SIGN")
                .algorithm("ES256").state("ACTIVE").activeVersion(1).rowVersion(0L).build();
        assertEquals(a, b, "同值 @Value 对象应 equals");
        assertEquals(a.hashCode(), b.hashCode());
    }

    @Test
    void keyPageShouldHoldItemsAndPagination() {
        List<KmsKey> items = Arrays.asList(KmsKey.builder().keyRef("k").keyAlias("a")
                .purpose("SIGN").algorithm("ES256").state("ACTIVE").activeVersion(1).rowVersion(0L).build());
        KmsKeyPage page = new KmsKeyPage(items, 1, 20, 1L);
        assertEquals(1, page.getItems().size());
        assertEquals(1L, page.getTotal());
    }

    @Test
    void signingResultShouldExposeVersionAndAlgorithmAndDefensiveCopy() {
        byte[] sig = new byte[]{1, 2, 3};
        KmsSigningResult result = KmsSigningResult.builder()
                .version(1).algorithm("ES256").signature(sig).build();
        assertEquals(1, result.getVersion());
        assertEquals("ES256", result.getAlgorithm());
        // 防御复制：改外部数组不影响模型内部
        sig[0] = 99;
        assertEquals(1, result.getSignature()[0], "构造时应复制入参");
        // getter 也返回副本
        byte[] got = result.getSignature();
        got[1] = 88;
        assertEquals(2, result.getSignature()[1], "getter 应返回副本");
    }

    // ==================== 端口存在性 ====================

    @Test
    void portInterfacesShouldDeclareCorrectMethodSignatures() {
        // 端口方法签名验证：方法名+参数个数+返回类型（防意外变更）
        try {
            java.lang.reflect.Method sign = OwnerSignerPort.class.getMethod("sign", String.class, Integer.class, byte[].class);
            assertEquals(io.github.surezzzzzz.sdk.kms.client.model.KmsSigningResult.class, sign.getReturnType());
            java.lang.reflect.Method pub = OwnerPublicKeyPort.class.getMethod("read", String.class, Integer.class);
            assertEquals(io.github.surezzzzzz.sdk.kms.client.model.KmsPublicKey.class, pub.getReturnType());
            java.lang.reflect.Method enc = KeyEncryptionPort.class.getMethod("encrypt", String.class, byte[].class, byte[].class);
            assertEquals(byte[].class, enc.getReturnType());
        } catch (NoSuchMethodException e) {
            throw new RuntimeException("端口方法签名不匹配", e);
        }
    }

    // ==================== SPI 中立性 ====================

    @Test
    void credentialApplierShouldBeTransportNeutral() {
        KmsCredentialApplier applier = headers -> headers.put("Authorization", "Bearer t");
        Map<String, String> headers = new LinkedHashMap<>();
        applier.apply(headers);
        assertEquals("Bearer t", headers.get("Authorization"));
    }

    @Test
    void credentialApplierShouldAllowMultipleHeaders() {
        KmsCredentialApplier applier = headers -> {
            headers.put("Authorization", "Bearer t");
            headers.put("X-Custom-Trace", "trace-1");
        };
        Map<String, String> headers = new LinkedHashMap<>();
        applier.apply(headers);
        assertEquals(2, headers.size());
    }

    // ==================== R18 覆盖缺口修复 ====================

    @Test
    void policyModelShouldBeImmutableWithBuilder() {
        io.github.surezzzzzz.sdk.kms.client.model.KmsPolicy policy =
                io.github.surezzzzzz.sdk.kms.client.model.KmsPolicy.builder()
                        .policyId("p-1").keyRef("k-1").principalId("iam:u1")
                        .operation("SIGN").rowVersion(0L).build();
        assertEquals("p-1", policy.getPolicyId());
        assertEquals("SIGN", policy.getOperation());
    }

    @Test
    void publicKeyModelShouldHoldVersionAndState() {
        io.github.surezzzzzz.sdk.kms.client.model.KmsPublicKey pub =
                io.github.surezzzzzz.sdk.kms.client.model.KmsPublicKey.builder()
                        .keyRef("k-1").version(2).algorithm("ES256").state("ACTIVE")
                        .publicKey(new byte[]{1, 2, 3}).build();
        assertEquals(2, pub.getVersion());
        assertEquals("ACTIVE", pub.getState());
        assertNotNull(pub.getPublicKey());
    }

    @Test
    void signatureModelShouldDefensiveCopy() {
        byte[] sig = {9, 8, 7};
        io.github.surezzzzzz.sdk.kms.client.model.KmsSignature signature =
                new io.github.surezzzzzz.sdk.kms.client.model.KmsSignature("k-1", 1, sig);
        sig[0] = 0;
        assertEquals(9, signature.getSignature()[0], "构造时应复制入参");
    }

    @Test
    void rootExceptionShouldCarrySafeFields() {
        io.github.surezzzzzz.sdk.kms.client.exception.SimpleKmsClientException ex =
                new io.github.surezzzzzz.sdk.kms.client.exception.SimpleKmsClientException(
                        "msg", 400, "POST", "/keys", "req-1", null);
        assertEquals(400, ex.getStatus());
        assertEquals("POST", ex.getMethod());
        assertEquals("/keys", ex.getEndpoint());
        assertEquals("req-1", ex.getRequestId());
    }

    @Test
    void responseTooLargeAndTransportShouldMapFrom413And5xx() {
        assertInstanceOf(io.github.surezzzzzz.sdk.kms.client.exception.KmsPayloadTooLargeException.class,
                errorMapper.map(413, "POST", "/crypto/envelopes", "x", null, null));
        assertInstanceOf(io.github.surezzzzzz.sdk.kms.client.exception.KmsServiceUnavailableException.class,
                errorMapper.map(500, "GET", "/keys", "x", null, null));
        assertInstanceOf(io.github.surezzzzzz.sdk.kms.client.exception.KmsServiceUnavailableException.class,
                errorMapper.map(502, "GET", "/keys", "x", null, null));
        assertInstanceOf(io.github.surezzzzzz.sdk.kms.client.exception.KmsServiceUnavailableException.class,
                errorMapper.map(503, "GET", "/keys", "x", null, null));
    }

    @Test
    void defaultPortsShouldRejectNullClientAtConstruction() {
        // 默认端口实现构造时应校验非空（fail-fast，不等到调用时才 NPE）
        assertThrows(IllegalArgumentException.class,
                () -> new io.github.surezzzzzz.sdk.kms.client.port.DefaultOwnerSignerPort(null));
        assertThrows(IllegalArgumentException.class,
                () -> new io.github.surezzzzzz.sdk.kms.client.port.DefaultOwnerPublicKeyPort(null));
        assertThrows(IllegalArgumentException.class,
                () -> new io.github.surezzzzzz.sdk.kms.client.port.DefaultKeyEncryptionPort(null));
    }

    @Test
    void constantsShouldBeConsistent() {
        assertEquals("/api/kms", io.github.surezzzzzz.sdk.kms.client.constant.SimpleKmsClientConstant.API_BASE_PATH);
        assertTrue(io.github.surezzzzzz.sdk.kms.client.constant.SimpleKmsClientConstant.DEFAULT_MAX_TOTAL > 0);
        assertTrue(io.github.surezzzzzz.sdk.kms.client.constant.SimpleKmsClientConstant.DEFAULT_CONNECT_TIMEOUT_MILLIS > 0);
    }
}
