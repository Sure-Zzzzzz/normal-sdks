package io.github.surezzzzzz.sdk.auth.aksk.client.core.test.cases;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import io.github.surezzzzzz.sdk.auth.aksk.client.core.configuration.SimpleAkskClientCoreProperties;
import io.github.surezzzzzz.sdk.auth.aksk.client.core.constant.ClientErrorCode;
import io.github.surezzzzzz.sdk.auth.aksk.client.core.constant.ClientErrorMessage;
import io.github.surezzzzzz.sdk.auth.aksk.client.core.exception.TokenFetchException;
import io.github.surezzzzzz.sdk.auth.aksk.client.core.executor.TokenRefreshExecutor;
import io.github.surezzzzzz.sdk.auth.aksk.client.core.test.SimpleAkskClientCoreTestApplication;
import io.github.surezzzzzz.sdk.auth.aksk.client.core.test.TestRetrySleeper;
import io.github.surezzzzzz.sdk.auth.aksk.client.core.test.TokenRefreshRetryTestConfiguration;
import io.github.surezzzzzz.sdk.retry.task.executor.TaskRetryExecutor;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
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
@Import(TokenRefreshRetryTestConfiguration.class)
class TokenRefreshExecutorTest {

    private static final String TOKEN_ENDPOINT = "/oauth2/token";
    private static final String TEST_CLIENT_ID = "fixture-client";
    private static final String TEST_CLIENT_SECRET = "fixture-client-secret";
    private static final String TEST_ACCESS_TOKEN = "fixture-access-token";
    private static final String VALID_TOKEN_RESPONSE = "{\"access_token\":\"fixture-access-token\",\"expires_in\":3600}";
    private static final String EMPTY_TOKEN_RESPONSE = "{\"expires_in\":3600}";
    private static final String BLANK_TOKEN_RESPONSE = "{\"access_token\":\"   \",\"expires_in\":3600}";
    private static final String INVALID_EXPIRY_RESPONSE = "{\"access_token\":\"fixture-access-token\"}";
    private static final String ZERO_EXPIRY_RESPONSE = "{\"access_token\":\"fixture-access-token\",\"expires_in\":0}";
    private static final String NEGATIVE_EXPIRY_RESPONSE = "{\"access_token\":\"fixture-access-token\",\"expires_in\":-1}";
    private static final String FAILURE_RESPONSE = "{\"error\":\"fixture-downstream-detail\"}";

    @Autowired
    private SimpleAkskClientCoreProperties properties;

    @Autowired
    private TestRetrySleeper retrySleeper;

    @Autowired
    private TaskRetryExecutor retryExecutor;

    private TokenRefreshExecutor tokenRefreshExecutor;

    @BeforeEach
    void setUp() {
        retrySleeper.clear();
        tokenRefreshExecutor = new TokenRefreshExecutor(properties, retryExecutor);
    }

    @Test
    @DisplayName("应通过 Client Credentials 获取 Token 并传递安全上下文")
    void shouldFetchTokenAndForwardSecurityContext() throws Exception {
        AtomicReference<String> requestBody = new AtomicReference<>();
        AtomicBoolean basicAuthorizationPresent = new AtomicBoolean();
        HttpServer server = startServer(exchange -> {
            requestBody.set(readRequestBody(exchange));
            basicAuthorizationPresent.set(exchange.getRequestHeaders().containsKey(HttpHeaders.AUTHORIZATION));
            writeJson(exchange, 200, VALID_TOKEN_RESPONSE);
        });

        try {
            AtomicReference<String> callbackToken = new AtomicReference<>();
            AtomicReference<Long> callbackExpiresIn = new AtomicReference<>();

            String token = tokenRefreshExecutor.fetchTokenFromServer(
                    "{\"subject_id\":\"fixture-subject\"}",
                    (accessToken, expiresIn) -> {
                        callbackToken.set(accessToken);
                        callbackExpiresIn.set(expiresIn);
                    });

            log.info("Token 刷新成功并完成缓存回调");
            assertEquals(TEST_ACCESS_TOKEN, token, "应返回服务端响应中的 Access Token");
            assertEquals(TEST_ACCESS_TOKEN, callbackToken.get(), "缓存回调应接收同一个 Access Token");
            assertEquals(Long.valueOf(3600L), callbackExpiresIn.get(), "缓存回调应接收有效期");
            assertNotNull(requestBody.get(), "服务端应接收到表单请求");
            assertTrue(requestBody.get().contains("grant_type=client_credentials"), "请求应使用 Client Credentials");
            assertTrue(requestBody.get().contains("security_context="), "非空安全上下文应进入表单参数");
            assertTrue(basicAuthorizationPresent.get(), "请求应携带 Client Credentials 认证头");
        } finally {
            server.stop(0);
        }
    }

    @Test
    @DisplayName("空 Access Token 应拒绝响应且不调用缓存回调")
    void shouldRejectEmptyAccessToken() throws Exception {
        HttpServer server = startServer(exchange -> writeJson(exchange, 200, EMPTY_TOKEN_RESPONSE));

        try {
            AtomicBoolean callbackCalled = new AtomicBoolean();

            TokenFetchException exception = assertThrows(TokenFetchException.class,
                    () -> tokenRefreshExecutor.fetchTokenFromServer(null,
                            (accessToken, expiresIn) -> callbackCalled.set(true)),
                    "空 Access Token 时应抛出 TokenFetchException");

            log.info("空 Access Token 已被拒绝，错误码: {}", exception.getErrorCode());
            assertEquals(ClientErrorCode.HTTP_RESPONSE_INVALID, exception.getErrorCode(),
                    "空 Access Token 应保留响应无效错误码");
            assertFalse(callbackCalled.get(), "无效响应不应调用缓存回调");
        } finally {
            server.stop(0);
        }
    }

    @Test
    @DisplayName("空白 Access Token 应拒绝响应且不调用缓存回调")
    void shouldRejectBlankAccessToken() throws Exception {
        HttpServer server = startServer(exchange -> writeJson(exchange, 200, BLANK_TOKEN_RESPONSE));

        try {
            AtomicBoolean callbackCalled = new AtomicBoolean();

            TokenFetchException exception = assertThrows(TokenFetchException.class,
                    () -> tokenRefreshExecutor.fetchTokenFromServer(null,
                            (accessToken, expiresIn) -> callbackCalled.set(true)),
                    "空白 Access Token 时应抛出 TokenFetchException");

            log.info("空白 Access Token 已被拒绝，错误码: {}", exception.getErrorCode());
            assertEquals(ClientErrorCode.HTTP_RESPONSE_INVALID, exception.getErrorCode(),
                    "空白 Access Token 应保留响应无效错误码");
            assertFalse(callbackCalled.get(), "无效响应不应调用缓存回调");
        } finally {
            server.stop(0);
        }
    }

    @Test
    @DisplayName("非法 expires_in 应拒绝响应且不调用缓存回调")
    void shouldRejectInvalidExpiry() throws Exception {
        HttpServer server = startServer(exchange -> writeJson(exchange, 200, INVALID_EXPIRY_RESPONSE));

        try {
            AtomicBoolean callbackCalled = new AtomicBoolean();

            TokenFetchException exception = assertThrows(TokenFetchException.class,
                    () -> tokenRefreshExecutor.fetchTokenFromServer(null,
                            (accessToken, expiresIn) -> callbackCalled.set(true)),
                    "非法 expires_in 时应抛出 TokenFetchException");

            log.info("非法 expires_in 已被拒绝，错误码: {}", exception.getErrorCode());
            assertEquals(ClientErrorCode.HTTP_RESPONSE_INVALID, exception.getErrorCode(),
                    "非法 expires_in 应保留响应无效错误码");
            assertFalse(callbackCalled.get(), "无效响应不应调用缓存回调");
        } finally {
            server.stop(0);
        }
    }

    @Test
    @DisplayName("零值和负值 expires_in 应拒绝响应")
    void shouldRejectNonPositiveExpiry() throws Exception {
        assertInvalidExpiryResponse(ZERO_EXPIRY_RESPONSE, "零值 expires_in");
        assertInvalidExpiryResponse(NEGATIVE_EXPIRY_RESPONSE, "负值 expires_in");
    }

    @Test
    @DisplayName("缓存回调失败时应返回中性错误消息")
    void shouldHideCacheCallbackFailureMessage() throws Exception {
        HttpServer server = startServer(exchange -> writeJson(exchange, 200, VALID_TOKEN_RESPONSE));

        try {
            TokenFetchException exception = assertThrows(TokenFetchException.class,
                    () -> tokenRefreshExecutor.fetchTokenFromServer(null,
                            (accessToken, expiresIn) -> {
                                throw new RuntimeException("fixture-callback-failure");
                            }),
                    "缓存回调失败时应抛出 TokenFetchException");

            log.info("缓存回调失败已映射为 Token 获取错误，错误码: {}", exception.getErrorCode());
            assertEquals(ClientErrorCode.TOKEN_FETCH_FAILED, exception.getErrorCode(),
                    "缓存回调失败应映射为 Token 获取错误");
            assertEquals(ClientErrorMessage.TOKEN_FETCH_FAILED, exception.getMessage(),
                    "对外错误消息应保持中性");
            assertFalse(exception.getMessage().contains("fixture-callback-failure"),
                    "对外错误消息不得泄露下游异常原文");
        } finally {
            server.stop(0);
        }
    }

    @Test
    @DisplayName("Token 端点失败时不应泄露下游错误正文")
    void shouldHideTokenEndpointFailureDetail() throws Exception {
        HttpServer server = startServer(exchange -> writeJson(exchange, 500, FAILURE_RESPONSE));

        try {
            TokenFetchException exception = assertThrows(TokenFetchException.class,
                    () -> tokenRefreshExecutor.fetchTokenFromServer(null, null),
                    "Token 端点失败时应抛出 TokenFetchException");

            log.info("Token 端点失败已映射为 Token 获取错误，错误码: {}", exception.getErrorCode());
            assertEquals(ClientErrorCode.TOKEN_FETCH_FAILED, exception.getErrorCode(),
                    "Token 端点失败应映射为 Token 获取错误");
            assertEquals(ClientErrorMessage.TOKEN_FETCH_FAILED, exception.getMessage(),
                    "对外错误消息应保持中性");
            assertFalse(exception.getMessage().contains("fixture-downstream-detail"),
                    "对外错误消息不得泄露下游错误正文");
        } finally {
            server.stop(0);
        }
    }

    private HttpServer startServer(com.sun.net.httpserver.HttpHandler handler) throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext(TOKEN_ENDPOINT, handler);
        server.start();

        properties.setServerUrl("http://127.0.0.1:" + server.getAddress().getPort());
        properties.setTokenEndpoint(TOKEN_ENDPOINT);
        properties.setClientId(TEST_CLIENT_ID);
        properties.setClientSecret(TEST_CLIENT_SECRET);
        return server;
    }

    private String readRequestBody(HttpExchange exchange) throws IOException {
        try (InputStream inputStream = exchange.getRequestBody();
             ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[1024];
            int readCount;
            while ((readCount = inputStream.read(buffer)) != -1) {
                outputStream.write(buffer, 0, readCount);
            }
            return new String(outputStream.toByteArray(), StandardCharsets.UTF_8);
        }
    }

    private void assertInvalidExpiryResponse(String response, String scenario) throws Exception {
        HttpServer server = startServer(exchange -> writeJson(exchange, 200, response));

        try {
            TokenFetchException exception = assertThrows(TokenFetchException.class,
                    () -> tokenRefreshExecutor.fetchTokenFromServer(null, null),
                    scenario + "时应抛出 TokenFetchException");

            log.info("{} 已被拒绝，错误码: {}", scenario, exception.getErrorCode());
            assertEquals(ClientErrorCode.HTTP_RESPONSE_INVALID, exception.getErrorCode(),
                    scenario + "应保留响应无效错误码");
        } finally {
            server.stop(0);
        }
    }

    private void writeJson(HttpExchange exchange, int statusCode, String response) throws IOException {
        byte[] responseBytes = response.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(statusCode, responseBytes.length);
        exchange.getResponseBody().write(responseBytes);
        exchange.close();
    }
}
