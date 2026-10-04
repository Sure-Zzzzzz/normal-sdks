package io.github.surezzzzzz.sdk.kms.feign.client.test.cases;

import io.github.surezzzzzz.sdk.auth.aksk.client.core.manager.TokenManager;
import io.github.surezzzzzz.sdk.kms.client.constant.SimpleKmsClientConstant;
import io.github.surezzzzzz.sdk.kms.feign.client.KmsFeignClient;
import io.github.surezzzzzz.sdk.kms.feign.client.model.KmsKeyPageResponse;
import io.github.surezzzzzz.sdk.kms.feign.client.model.KmsKeyResponse;
import io.github.surezzzzzz.sdk.kms.feign.client.model.KmsPolicyResponse;
import io.github.surezzzzzz.sdk.kms.feign.client.model.KmsSignResponse;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.context.annotation.Bean;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * KmsFeignClient 契约测试：JDK HttpServer 桩出服务端，经 @EnableFeignClients 真实 Feign 链路
 * 逐方法断言路径、方法、幂等头、请求体与响应解码（形态对齐 aksk feign 底座自身测试）。
 *
 * @author surezzzzzz
 */
@Slf4j
@SpringBootTest(classes = KmsFeignClientContractTest.ContractTestApplication.class,
        properties = "io.github.surezzzzzz.sdk.auth.aksk.client.enable=true",
        webEnvironment = SpringBootTest.WebEnvironment.NONE)
class KmsFeignClientContractTest {

    private static HttpStubServer stub;
    @Autowired
    private KmsFeignClient client;

    @DynamicPropertySource
    static void baseUrl(DynamicPropertyRegistry registry) {
        registry.add(SimpleKmsClientConstant.CONFIG_PREFIX + ".base-url",
                () -> "http://127.0.0.1:" + stub.port());
    }

    @BeforeAll
    static void startStub() {
        stub = new HttpStubServer();
        stub.start();
    }

    @AfterAll
    static void stopStub() {
        stub.stop();
    }

    private static Map<String, Object> body(Object... pairs) {
        Map<String, Object> map = new LinkedHashMap<String, Object>();
        for (int i = 0; i < pairs.length; i += 2) {
            map.put((String) pairs[i], pairs[i + 1]);
        }
        return map;
    }

    private static String keyJson() {
        return "{\"keyRef\":\"key-1\",\"keyAlias\":\"alias-1\",\"purpose\":\"SIGN\","
                + "\"algorithm\":\"ES256\",\"state\":\"ACTIVE\",\"activeVersion\":1,\"rowVersion\":4,"
                + "\"createdAt\":\"2026-01-01T00:00:00.000Z\",\"updatedAt\":\"2026-01-01T00:00:00.000Z\"}";
    }

    private static String policyJson() {
        return "{\"policyId\":\"policy-1\",\"keyRef\":\"key-1\",\"principalId\":\"iam:user-1\","
                + "\"keyVersion\":1,\"operation\":\"SIGN\",\"expiresAt\":\"2026-07-27T01:02:03.004Z\","
                + "\"rowVersion\":7}";
    }

    private static String publicKeyJson() {
        return "{\"keyRef\":\"key-1\",\"version\":1,\"algorithm\":\"ES256\",\"state\":\"ACTIVE\","
                + "\"publicKey\":\"cHVia2V5\"}";
    }

    /**
     * 每个测试前清空桩注册（防止 fail 状态跨测试残留）。
     */
    @org.junit.jupiter.api.BeforeEach
    void resetStub() {
        stub.reset();
    }

    @Test
    void shouldCallKeyManagementEndpointsWithContractFields() {
        stub.reply("POST", "/api/kms/keys", keyJson());
        KmsKeyResponse created = client.createKey("idem-0001", body(
                SimpleKmsClientConstant.FIELD_KEY_ALIAS, "alias-1",
                SimpleKmsClientConstant.FIELD_PURPOSE, "SIGN",
                SimpleKmsClientConstant.FIELD_ALGORITHM, "ES256"));
        assertEquals("key-1", created.keyRef, "响应 keyRef 必须解码");
        assertEquals("ACTIVE", created.state, "响应 state 必须解码");
        assertEquals("idem-0001", stub.lastIdempotencyKey(), "幂等键必须走 Idempotency-Key 头");
        assertTrue(stub.lastBody().contains("ES256"), "请求体必须携带算法");
        stub.reset();

        stub.reply("GET", "/api/kms/keys/key-1", keyJson());
        assertEquals("alias-1", client.getKey("key-1").keyAlias, "单键查询必须命中 /keys/{keyRef}");
        stub.reset();

        stub.reply("GET", "/api/kms/keys?page=1&size=10&alias=alias-1&purpose=SIGN&algorithm=ES256&state=ACTIVE",
                "{\"items\":[" + keyJson() + "],\"page\":1,\"size\":10,\"total\":1}");
        KmsKeyPageResponse page = client.listKeys(1, 10, "alias-1", "SIGN", "ES256", "ACTIVE");
        assertEquals(1, page.items.size(), "分页条目必须解码");
        assertEquals(Long.valueOf(1), page.total, "分页 total 必须解码");
        stub.reset();

        stub.reply("PATCH", "/api/kms/keys/key-1/state", keyJson());
        client.changeKeyState("idem-0001", "key-1", body(
                SimpleKmsClientConstant.FIELD_STATE, "DISABLED",
                SimpleKmsClientConstant.FIELD_EXPECTED_ROW_VERSION, 4));
        assertEquals("PATCH", stub.lastMethod(), "状态变更必须走 PATCH");
        stub.reset();

        stub.reply("POST", "/api/kms/keys/key-1/versions", keyJson());
        client.rotateKey("idem-0001", "key-1", body(
                SimpleKmsClientConstant.FIELD_EXPECTED_ROW_VERSION, 4));
        assertEquals("POST /api/kms/keys/key-1/versions", stub.lastMethod() + " " + stub.lastPath(),
                "轮换必须命中 /keys/{keyRef}/versions");
        stub.reset();

        stub.reply("PUT", "/api/kms/keys/key-1/destruction", keyJson());
        client.scheduleDestruction("idem-0001", "key-1", body(
                SimpleKmsClientConstant.FIELD_DUE_AT, "2026-07-27T01:02:03.004Z",
                SimpleKmsClientConstant.FIELD_EXPECTED_ROW_VERSION, 4));
        assertEquals("PUT", stub.lastMethod(), "安排销毁必须走 PUT");
        assertTrue(stub.lastBody().contains("dueAt"), "销毁窗口字段必须是契约的 dueAt");
        stub.reset();

        stub.reply("DELETE", "/api/kms/keys/key-1/destruction", "{}");
        client.cancelDestruction("idem-0001", "key-1", body(
                SimpleKmsClientConstant.FIELD_EXPECTED_ROW_VERSION, 5));
        assertEquals("DELETE", stub.lastMethod(), "取消销毁必须走 DELETE 且携带并发体");
        assertTrue(stub.lastBody().contains("expectedRowVersion"), "取消销毁体必须携带乐观并发版本");
    }

    @Test
    void shouldCallPolicyAndCryptoEndpointsWithContractFields() {
        stub.reply("POST", "/api/kms/keys/key-1/policies", policyJson());
        KmsPolicyResponse policy = client.createPolicy("idem-0001", "key-1", body(
                SimpleKmsClientConstant.FIELD_PRINCIPAL_ID, "iam:user-1",
                SimpleKmsClientConstant.FIELD_OPERATION, "SIGN"));
        assertEquals("policy-1", policy.policyId, "策略响应必须解码");
        stub.reset();

        stub.reply("GET", "/api/kms/keys/key-1/policies", "{\"items\":[" + policyJson() + "]}");
        assertEquals(1, client.listPolicies("key-1").items.size(), "策略列表必须解码");
        stub.reset();

        stub.reply("DELETE", "/api/kms/keys/key-1/policies/policy-1", "{}");
        client.revokePolicy("idem-0001", "key-1", "policy-1", body(
                SimpleKmsClientConstant.FIELD_EXPECTED_ROW_VERSION, 7));
        assertEquals("DELETE /api/kms/keys/key-1/policies/policy-1", stub.lastMethod() + " " + stub.lastPath(),
                "撤销策略必须命中 /policies/{policyId}");
        stub.reset();

        stub.reply("POST", "/api/kms/crypto/signatures",
                "{\"keyRef\":\"key-1\",\"version\":1,\"signature\":\"c2lnbmF0dXJl\"}");
        KmsSignResponse sign = client.sign(body(
                SimpleKmsClientConstant.FIELD_KEY_REF, "key-1",
                SimpleKmsClientConstant.FIELD_INPUT, "cGF5bG9hZC0x"));
        assertEquals("c2lnbmF0dXJl", sign.signature, "签名字段必须按 Base64url 原文解码");
        stub.reset();

        stub.reply("POST", "/api/kms/crypto/verifications", "{\"valid\":true}");
        assertEquals(Boolean.TRUE, client.verify(body(
                        SimpleKmsClientConstant.FIELD_KEY_REF, "key-1",
                        SimpleKmsClientConstant.FIELD_INPUT, "cGF5bG9hZC0x",
                        SimpleKmsClientConstant.FIELD_SIGNATURE, "c2lnbmF0dXJl")).valid,
                "验签结果必须解码");
        stub.reset();

        stub.reply("POST", "/api/kms/crypto/envelopes", "{\"envelope\":\"ZW52ZWxvcGU\"}");
        assertEquals("ZW52ZWxvcGU", client.encrypt(body(
                        SimpleKmsClientConstant.FIELD_KEY_REF, "key-1",
                        SimpleKmsClientConstant.FIELD_PLAINTEXT, "cGxhaW50ZXh0")).envelope,
                "信封字段必须解码");
        stub.reset();

        stub.reply("POST", "/api/kms/crypto/decryptions", "{\"plaintext\":\"cGxhaW50ZXh0\"}");
        assertEquals("cGxhaW50ZXh0", client.decrypt(body(
                        SimpleKmsClientConstant.FIELD_ENVELOPE, "ZW52ZWxvcGU")).plaintext,
                "明文字段必须解码");
        stub.reset();

        stub.reply("GET", "/api/kms/keys/key-1/public-key?version=1", publicKeyJson());
        assertEquals("ES256", client.readPublicKey("key-1", 1).algorithm, "公钥必须按版本查询");
        stub.reset();

        stub.reply("GET", "/api/kms/keys/key-1/public-keys", "[" + publicKeyJson() + "]");
        assertEquals(1, client.listPublicKeys("key-1").size(), "公钥列表直接返回数组");
    }

    @Test
    void shouldDecodeRawContractFieldsWithoutTransformation() {
        stub.reply("GET", "/api/kms/keys/key-1", keyJson());
        KmsKeyResponse key = client.getKey("key-1");
        assertEquals("2026-01-01T00:00:00.000Z", key.createdAt,
                "时间字段必须保持契约原文（UTC 毫秒字符串），不做类型转换");
    }

    @Test
    void shouldOmitNullQueryParameters() {
        stub.reply("GET", "/api/kms/keys?page=1&size=10",
                "{\"items\":[],\"page\":1,\"size\":10,\"total\":0}");
        KmsKeyPageResponse page = client.listKeys(1, 10, null, null, null, null);
        assertTrue(page.items.isEmpty(), "空列表必须解码");
        assertEquals("GET /api/kms/keys?page=1&size=10", stub.lastMethod() + " " + stub.lastPath(),
                "null 可选参数必须从查询串省略");
    }

    @Test
    void shouldPropagateHttpErrorAsFeignException() {
        stub.fail("GET", "/api/kms/keys/key-1", 400);
        feign.FeignException exception = assertThrows(feign.FeignException.class,
                () -> client.getKey("key-1"), "非 2xx 必须以 FeignException 透传（含状态码）");
        assertEquals(400, exception.status(), "透传异常必须保留状态码");
    }

    /**
     * 契约测试启动类：启用 Feign 扫描契约接口，桩出底座令牌链满足拦截器装配条件。
     */
    @SpringBootApplication
    @EnableFeignClients(basePackageClasses = KmsFeignClient.class)
    static class ContractTestApplication {

        /**
         * 测试桩令牌链：固定令牌，不连接任何真实凭据源。
         */
        @Bean
        @org.springframework.context.annotation.Primary
        public TokenManager testTokenManager() {
            return new TokenManager() {
                @Override
                public String getToken() {
                    return "test-token";
                }

                @Override
                public void clearToken() {
                    // 桩实现无缓存可清
                }
            };
        }
    }

    /**
     * JDK HttpServer 最小桩：按方法+路径回放 JSON，记录方法/路径/幂等头/请求体。
     */
    static final class HttpStubServer {
        private final Map<String, String> canned = new HashMap<String, String>();
        private final Map<String, Integer> statuses = new HashMap<String, Integer>();
        private final List<String[]> exchanges = new ArrayList<String[]>();
        private com.sun.net.httpserver.HttpServer server;

        void start() {
            try {
                server = com.sun.net.httpserver.HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
                server.createContext("/", this::handle);
                server.start();
            } catch (Exception exception) {
                throw new IllegalStateException("stub server start failed", exception);
            }
        }

        void stop() {
            if (server != null) {
                server.stop(0);
            }
        }

        int port() {
            return server.getAddress().getPort();
        }

        void reply(String method, String path, String body) {
            canned.put(method + " " + path, body);
        }

        void fail(String method, String path, int status) {
            statuses.put(method + " " + path, Integer.valueOf(status));
        }

        String lastMethod() {
            return exchanges.get(exchanges.size() - 1)[0];
        }

        String lastPath() {
            return exchanges.get(exchanges.size() - 1)[1];
        }

        String lastIdempotencyKey() {
            return exchanges.get(exchanges.size() - 1)[2];
        }

        String lastBody() {
            return exchanges.get(exchanges.size() - 1)[3];
        }

        void reset() {
            exchanges.clear();
            canned.clear();
            statuses.clear();
        }

        private void handle(com.sun.net.httpserver.HttpExchange exchange) throws java.io.IOException {
            try {
                handle0(exchange);
            } catch (java.io.IOException exception) {
                throw exception;
            } catch (Exception exception) {
                throw new java.io.IOException(exception);
            }
        }

        private void handle0(com.sun.net.httpserver.HttpExchange exchange) throws Exception {
            ByteArrayOutputStream buffer = new ByteArrayOutputStream();
            try (InputStream input = exchange.getRequestBody()) {
                byte[] chunk = new byte[4096];
                int count;
                while ((count = input.read(chunk)) >= 0) {
                    buffer.write(chunk, 0, count);
                }
            }
            String query = exchange.getRequestURI().getRawQuery();
            String path = exchange.getRequestURI().getRawPath() + (query == null ? "" : "?" + query);
            exchanges.add(new String[]{exchange.getRequestMethod(), path,
                    exchange.getRequestHeaders().getFirst(SimpleKmsClientConstant.HEADER_IDEMPOTENCY_KEY),
                    new String(buffer.toByteArray(), StandardCharsets.UTF_8)});
            String body = canned.containsKey(exchange.getRequestMethod() + " " + path)
                    ? canned.get(exchange.getRequestMethod() + " " + path) : "{}";
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            Integer cannedStatus = statuses.get(exchange.getRequestMethod() + " " + path);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(cannedStatus == null ? 200 : cannedStatus.intValue(), bytes.length);
            try (OutputStream output = exchange.getResponseBody()) {
                output.write(bytes);
            }
        }
    }
}
