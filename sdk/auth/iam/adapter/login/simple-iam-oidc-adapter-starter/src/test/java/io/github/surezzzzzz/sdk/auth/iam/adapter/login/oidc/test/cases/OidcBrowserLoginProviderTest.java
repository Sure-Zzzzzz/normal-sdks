package io.github.surezzzzzz.sdk.auth.iam.adapter.login.oidc.test.cases;

import com.nimbusds.jose.jwk.RSAKey;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import io.github.surezzzzzz.sdk.auth.iam.adapter.login.oidc.configuration.SimpleIamOidcAdapterProperties;
import io.github.surezzzzzz.sdk.auth.iam.adapter.login.oidc.service.MemoryPendingStateStore;
import io.github.surezzzzzz.sdk.auth.iam.adapter.login.oidc.service.OidcBrowserLoginProvider;
import io.github.surezzzzzz.sdk.auth.iam.adapter.login.oidc.test.support.OidcIdTokenMintSupport;
import io.github.surezzzzzz.sdk.auth.iam.core.exception.IamProtocolException;
import io.github.surezzzzzz.sdk.auth.iam.core.spi.ExternalIdentity;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.util.UriComponentsBuilder;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * OIDC 登录提供方单测（本地 HTTP mock IdP + RSA 真签名 ID token）
 *
 * <p>提供方组装（RestTemplate / NimbusJwtDecoder）已在构造内自治完成，
 * 测试以 JDK HttpServer 在网络边界 mock IdP 的 token / JWKS 端点。</p>
 *
 * @author surezzzzzz
 */
@Slf4j
class OidcBrowserLoginProviderTest {

    private static final String CLIENT_ID = "iam-test-client";
    private static final String CLIENT_SECRET = "test-client-secret";
    private static final String CALLBACK_URL = "http://localhost:18080/iam/web/auth/callback/oidc";
    private static final String STATE = "unit-state-1";
    private final AtomicInteger tokenRequestCount = new AtomicInteger();
    private RSAKey signingKey;
    private HttpServer idpServer;
    private String issuer;
    private String tokenUri;
    private String jwksUri;
    private volatile int tokenResponseStatus = 200;

    private volatile String tokenResponseBody = "{}";

    private volatile String jwksBody = "{}";

    private OidcBrowserLoginProvider provider;
    private String lastAuthorizeUrl;

    private static void drainRequest(HttpExchange exchange) throws IOException {
        exchange.getRequestBody().close();
    }

    private static void respond(HttpExchange exchange, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, bytes.length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(bytes);
        }
    }

    @BeforeEach
    void setUp() throws IOException {
        signingKey = OidcIdTokenMintSupport.generateSigningKey("unit-kid");
        idpServer = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        int port = idpServer.getAddress().getPort();
        issuer = "http://127.0.0.1:" + port;
        tokenUri = issuer + "/token";
        jwksUri = issuer + "/certs";
        jwksBody = OidcIdTokenMintSupport.toPublicJwks(signingKey);
        idpServer.createContext("/token", exchange -> {
            tokenRequestCount.incrementAndGet();
            drainRequest(exchange);
            respond(exchange, tokenResponseStatus, tokenResponseBody);
        });
        idpServer.createContext("/certs", exchange -> {
            drainRequest(exchange);
            respond(exchange, 200, jwksBody);
        });
        idpServer.start();
        provider = newProvider(issuer);
    }

    @AfterEach
    void tearDown() {
        if (idpServer != null) {
            idpServer.stop(0);
        }
    }

    @Test
    @DisplayName("授权地址应携带授权码模式标准参数")
    void testAuthorizeUrl() {
        String authorizeUrl = provider.buildAuthorizeUrl(CALLBACK_URL, STATE);
        log.info("授权地址：{}", authorizeUrl);
        assertTrue(authorizeUrl.startsWith(issuer + "/authorize"));
        Map<String, String> query = queryOf(authorizeUrl);
        assertEquals("code", query.get("response_type"));
        assertEquals(CLIENT_ID, query.get("client_id"));
        assertEquals(CALLBACK_URL, query.get("redirect_uri"));
        assertEquals(STATE, query.get("state"));
        assertNotNull(query.get("nonce"));
        assertTrue(query.get("scope").contains("openid"));
    }

    @Test
    @DisplayName("回调换取身份：code 换 token + JWKS 验签成功应返回 sub 为 externalId 的身份")
    void testConsumeCallbackHappyPath() {
        String nonce = beginAuthorization();
        String idToken = OidcIdTokenMintSupport.mintIdToken(
                signingKey, issuer, CLIENT_ID, "subject-001", "e2e-user", nonce);
        tokenResponseBody = withTokenBody(idToken);

        ExternalIdentity identity = provider.consumeCallback(callbackParams("auth-code-1", STATE));
        log.info("回调换取身份：providerCode={}, externalId={}, username={}",
                identity.getProviderCode(), identity.getExternalId(),
                identity.getUsernameSuggestion());

        assertEquals("oidc", identity.getProviderCode());
        assertEquals("subject-001", identity.getExternalId());
        assertEquals("e2e-user", identity.getUsernameSuggestion());
        assertEquals("e2e-user@sure-iam.test", identity.getEmail());
        assertTrue(tokenRequestCount.get() >= 1, "令牌端点应被真实调用");
    }

    @Test
    @DisplayName("同一 state 二次消费应被拒绝")
    void testStateReplayRejected() {
        String nonce = beginAuthorization();
        String idToken = OidcIdTokenMintSupport.mintIdToken(
                signingKey, issuer, CLIENT_ID, "subject-001", "e2e-user", nonce);
        tokenResponseBody = withTokenBody(idToken);
        provider.consumeCallback(callbackParams("auth-code-1", STATE));

        IamProtocolException exception = assertThrows(IamProtocolException.class,
                () -> provider.consumeCallback(callbackParams("auth-code-1", STATE)));
        log.info("state 重放拒绝：errorCode={}", exception.getErrorCode());
        assertEquals("BIZ_004", exception.getErrorCode());
    }

    @Test
    @DisplayName("未知 state 或缺失 code 应按回调无效拒绝")
    void testInvalidCallbackRejected() {
        log.info("回调参数异常场景：未知 state / 缺失 code / error 回包");
        assertEquals("BIZ_004", assertThrows(IamProtocolException.class,
                () -> provider.consumeCallback(callbackParams("auth-code-1", "unknown-state")))
                .getErrorCode());
        assertEquals("BIZ_004", assertThrows(IamProtocolException.class,
                () -> provider.consumeCallback(callbackParams(null, STATE)))
                .getErrorCode());
        assertEquals("BIZ_004", assertThrows(IamProtocolException.class,
                () -> provider.consumeCallback(null)).getErrorCode());
        Map<String, String> errorParams = new HashMap<>();
        errorParams.put("error", "access_denied");
        assertEquals("BIZ_004", assertThrows(IamProtocolException.class,
                () -> provider.consumeCallback(errorParams)).getErrorCode());
    }

    @Test
    @DisplayName("令牌端点 4xx 应按回调无效处理")
    void testTokenEndpoint4xx() {
        beginAuthorization();
        tokenResponseStatus = 400;
        tokenResponseBody = "{\"error\":\"invalid_grant\"}";

        IamProtocolException exception = assertThrows(IamProtocolException.class,
                () -> provider.consumeCallback(callbackParams("auth-code-1", STATE)));
        log.info("令牌端点 4xx：errorCode={}", exception.getErrorCode());
        assertEquals("BIZ_004", exception.getErrorCode());
    }

    @Test
    @DisplayName("令牌端点 5xx 应按外部 IdP 异常处理")
    void testTokenEndpoint5xx() {
        beginAuthorization();
        tokenResponseStatus = 500;
        tokenResponseBody = "{\"error\":\"server_error\"}";

        IamProtocolException exception = assertThrows(IamProtocolException.class,
                () -> provider.consumeCallback(callbackParams("auth-code-1", STATE)));
        log.info("令牌端点 5xx：errorCode={}", exception.getErrorCode());
        assertEquals("BIZ_003", exception.getErrorCode());
    }

    @Test
    @DisplayName("外部 IdP 不可达应按 PROVIDER_UNAVAILABLE 处理")
    void testIdpUnreachable() throws IOException {
        idpServer.stop(0);
        idpServer = null;
        provider = newProvider(deadIssuer());

        beginAuthorization();
        IamProtocolException exception = assertThrows(IamProtocolException.class,
                () -> provider.consumeCallback(callbackParams("auth-code-1", STATE)));
        log.info("IdP 不可达：errorCode={}", exception.getErrorCode());
        assertEquals("BIZ_003", exception.getErrorCode());
    }

    @Test
    @DisplayName("ID token nonce 不符应拒绝")
    void testNonceMismatchRejected() {
        beginAuthorization();
        String idToken = OidcIdTokenMintSupport.mintIdToken(
                signingKey, issuer, CLIENT_ID, "subject-001", "e2e-user", "tampered-nonce");
        tokenResponseBody = withTokenBody(idToken);

        IamProtocolException exception = assertThrows(IamProtocolException.class,
                () -> provider.consumeCallback(callbackParams("auth-code-1", STATE)));
        log.info("nonce 不符拒绝：errorCode={}", exception.getErrorCode());
        assertEquals("BIZ_004", exception.getErrorCode());
    }

    @Test
    @DisplayName("ID token iss 与配置 issuer 不符应拒绝")
    void testIssuerMismatchRejected() {
        beginAuthorization();
        String idToken = OidcIdTokenMintSupport.mintIdToken(
                signingKey, "https://evil.example.test", CLIENT_ID,
                "subject-001", "e2e-user", nonceOfLastAuthorizeUrl());
        tokenResponseBody = withTokenBody(idToken);

        IamProtocolException exception = assertThrows(IamProtocolException.class,
                () -> provider.consumeCallback(callbackParams("auth-code-1", STATE)));
        log.info("iss 不符拒绝：errorCode={}", exception.getErrorCode());
        assertEquals("BIZ_004", exception.getErrorCode());
    }

    @Test
    @DisplayName("ID token aud 不含本客户端应拒绝")
    void testAudienceMismatchRejected() {
        beginAuthorization();
        String idToken = OidcIdTokenMintSupport.mintIdToken(
                signingKey, issuer, "other-client", "subject-001",
                "e2e-user", nonceOfLastAuthorizeUrl());
        tokenResponseBody = withTokenBody(idToken);

        IamProtocolException exception = assertThrows(IamProtocolException.class,
                () -> provider.consumeCallback(callbackParams("auth-code-1", STATE)));
        log.info("aud 不符拒绝：errorCode={}", exception.getErrorCode());
        assertEquals("BIZ_004", exception.getErrorCode());
    }

    @Test
    @DisplayName("令牌端点 200 但未返回 ID token 应按回调无效处理")
    void testTokenResponseWithoutIdToken() {
        beginAuthorization();
        tokenResponseBody = "{\"access_token\":\"unit-access-token\",\"token_type\":\"Bearer\"}";

        IamProtocolException exception = assertThrows(IamProtocolException.class,
                () -> provider.consumeCallback(callbackParams("auth-code-1", STATE)));
        log.info("令牌响应无 ID token：errorCode={}", exception.getErrorCode());
        assertEquals("BIZ_004", exception.getErrorCode());
    }

    @Test
    @DisplayName("ID token 缺少 sub 应拒绝")
    void testMissingSubjectRejected() {
        beginAuthorization();
        String idToken = OidcIdTokenMintSupport.mintIdToken(
                signingKey, issuer, CLIENT_ID, null, "e2e-user", nonceOfLastAuthorizeUrl());
        tokenResponseBody = withTokenBody(idToken);

        IamProtocolException exception = assertThrows(IamProtocolException.class,
                () -> provider.consumeCallback(callbackParams("auth-code-1", STATE)));
        log.info("sub 缺失拒绝：errorCode={}", exception.getErrorCode());
        assertEquals("BIZ_004", exception.getErrorCode());
    }

    @Test
    @DisplayName("ID token 签名密钥与 JWKS 不符应拒绝")
    void testSignatureMismatchRejected() {
        beginAuthorization();
        RSAKey attackerKey = OidcIdTokenMintSupport.generateSigningKey("attacker-kid");
        String idToken = OidcIdTokenMintSupport.mintIdToken(
                attackerKey, issuer, CLIENT_ID, "subject-001", "e2e-user", nonceOfLastAuthorizeUrl());
        tokenResponseBody = withTokenBody(idToken);

        IamProtocolException exception = assertThrows(IamProtocolException.class,
                () -> provider.consumeCallback(callbackParams("auth-code-1", STATE)));
        log.info("签名不符拒绝：errorCode={}", exception.getErrorCode());
        assertEquals("BIZ_004", exception.getErrorCode());
    }

    /**
     * 发起授权并返回本次授权生成的 nonce
     */
    private String beginAuthorization() {
        lastAuthorizeUrl = provider.buildAuthorizeUrl(CALLBACK_URL, STATE);
        return nonceOf(lastAuthorizeUrl);
    }

    private String nonceOfLastAuthorizeUrl() {
        if (lastAuthorizeUrl == null) {
            lastAuthorizeUrl = provider.buildAuthorizeUrl(CALLBACK_URL, STATE);
        }
        return nonceOf(lastAuthorizeUrl);
    }

    private String nonceOf(String url) {
        return queryOf(url).get("nonce");
    }

    private OidcBrowserLoginProvider newProvider(String base) {
        SimpleIamOidcAdapterProperties properties = new SimpleIamOidcAdapterProperties();
        properties.setIssuer(base);
        properties.setAuthorizationUri(base + "/authorize");
        properties.setTokenUri(base + "/token");
        properties.setJwkSetUri(base + "/certs");
        properties.setClientId(CLIENT_ID);
        properties.setClientSecret(CLIENT_SECRET);
        return new OidcBrowserLoginProvider(properties, new MemoryPendingStateStore());
    }

    /**
     * 取一个已释放端口的 issuer：连接必被拒，模拟 IdP 宕机
     */
    private String deadIssuer() throws IOException {
        HttpServer dead = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        String base = "http://127.0.0.1:" + dead.getAddress().getPort();
        dead.stop(0);
        return base;
    }

    private String withTokenBody(String idToken) {
        return "{\"access_token\":\"unit-access-token\",\"token_type\":\"Bearer\","
                + "\"expires_in\":300,\"id_token\":\"" + idToken + "\"}";
    }

    private Map<String, String> callbackParams(String code, String state) {
        Map<String, String> params = new HashMap<>();
        if (code != null) {
            params.put("code", code);
        }
        params.put("state", state);
        return params;
    }

    private Map<String, String> queryOf(String url) {
        return UriComponentsBuilder.fromUriString(url).build().getQueryParams().toSingleValueMap();
    }
}
