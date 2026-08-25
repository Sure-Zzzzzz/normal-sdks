package io.github.surezzzzzz.sdk.auth.aksk.client.core.test.cases;

import com.sun.net.httpserver.HttpServer;
import io.github.surezzzzzz.sdk.auth.aksk.client.core.configuration.SimpleAkskClientCoreProperties;
import io.github.surezzzzzz.sdk.auth.aksk.client.core.constant.ClientErrorCode;
import io.github.surezzzzzz.sdk.auth.aksk.client.core.exception.TokenFetchException;
import io.github.surezzzzzz.sdk.auth.aksk.client.core.executor.TokenRefreshExecutor;
import io.github.surezzzzzz.sdk.auth.aksk.client.core.test.SimpleAkskClientCoreTestApplication;
import io.github.surezzzzzz.sdk.retry.task.executor.TaskRetryExecutor;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

/**
 * TokenRefreshExecutor 集成测试
 *
 * @author surezzzzzz
 */
@Slf4j
@SpringBootTest(classes = SimpleAkskClientCoreTestApplication.class)
class TokenRefreshExecutorTest {

    @Autowired
    private SimpleAkskClientCoreProperties properties;

    @Autowired
    private TaskRetryExecutor retryExecutor;

    private TokenRefreshExecutor tokenRefreshExecutor;

    @BeforeEach
    void setUp() {
        tokenRefreshExecutor = new TokenRefreshExecutor(properties, retryExecutor);
    }

    @Test
    @DisplayName("测试从服务器获取 Token - 无 security_context")
    void testFetchTokenFromServerNoSecurityContext() {
        log.info("======================================");
        log.info("测试从服务器获取 Token - 无 security_context");
        log.info("======================================");

        AtomicReference<String> callbackToken = new AtomicReference<>();
        AtomicReference<Long> callbackExpiresIn = new AtomicReference<>();

        String token = tokenRefreshExecutor.fetchTokenFromServer(
                null,
                (accessToken, expiresIn) -> {
                    callbackToken.set(accessToken);
                    callbackExpiresIn.set(expiresIn);
                }
        );

        log.info("获取的 Token: {}", token);
        assertNotNull(token, "Token 不应为 null");
        assertEquals(token, callbackToken.get(), "获取的 Token 应与回调接收的 Token 一致");
        assertNotNull(callbackExpiresIn.get(), "过期时间不应为 null");
        assertTrue(callbackExpiresIn.get() > 0, "过期时间应大于 0");

        log.info("======================================");
    }

    @Test
    @DisplayName("无效 expires_in 应返回 HTTP 响应错误")
    void shouldRejectTokenResponseWithInvalidExpiry() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        byte[] response = "{\"access_token\":\"test-token\"}".getBytes(StandardCharsets.UTF_8);
        server.createContext("/oauth2/token", exchange -> writeResponse(exchange, response));
        server.start();

        String originalServerUrl = properties.getServerUrl();
        String originalTokenEndpoint = properties.getTokenEndpoint();
        try {
            properties.setServerUrl("http://localhost:" + server.getAddress().getPort());
            properties.setTokenEndpoint("/oauth2/token");
            AtomicBoolean callbackCalled = new AtomicBoolean();

            TokenFetchException exception = assertThrows(TokenFetchException.class,
                    () -> tokenRefreshExecutor.fetchTokenFromServer(null,
                            (accessToken, expiresIn) -> callbackCalled.set(true)),
                    "expires_in 缺失时应抛出 TokenFetchException");

            log.info("无效 Token 响应错误码: {}, 错误消息: {}", exception.getErrorCode(), exception.getMessage());
            assertEquals(ClientErrorCode.HTTP_RESPONSE_INVALID, exception.getErrorCode(),
                    "无效 Token 响应应保留 HTTP_RESPONSE_INVALID 错误码");
            assertTrue(exception.getMessage().contains("expires_in"),
                    "错误消息应指出 expires_in 无效");
            assertFalse(callbackCalled.get(), "无效 Token 响应不应调用缓存回调");
        } finally {
            properties.setServerUrl(originalServerUrl);
            properties.setTokenEndpoint(originalTokenEndpoint);
            server.stop(0);
        }
    }

    private void writeResponse(com.sun.net.httpserver.HttpExchange exchange, byte[] response) throws IOException {
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(200, response.length);
        try (OutputStream outputStream = exchange.getResponseBody()) {
            outputStream.write(response);
        }
    }

    @Test
    @DisplayName("测试从服务器获取 Token - 带 security_context")
    void testFetchTokenFromServerWithSecurityContext() {
        log.info("======================================");
        log.info("测试从服务器获取 Token - 带 security_context");
        log.info("======================================");

        String securityContext = "{\"user_id\":\"test-user-123\"}";
        AtomicReference<String> callbackToken = new AtomicReference<>();
        AtomicReference<Long> callbackExpiresIn = new AtomicReference<>();

        String token = tokenRefreshExecutor.fetchTokenFromServer(
                securityContext,
                (accessToken, expiresIn) -> {
                    callbackToken.set(accessToken);
                    callbackExpiresIn.set(expiresIn);
                }
        );

        log.info("获取的 Token: {}", token);
        assertNotNull(token, "Token 不应为 null");
        assertEquals(token, callbackToken.get(), "获取的 Token 应与回调接收的 Token 一致");
        assertNotNull(callbackExpiresIn.get(), "过期时间不应为 null");
        assertTrue(callbackExpiresIn.get() > 0, "过期时间应大于 0");

        log.info("======================================");
    }
}
