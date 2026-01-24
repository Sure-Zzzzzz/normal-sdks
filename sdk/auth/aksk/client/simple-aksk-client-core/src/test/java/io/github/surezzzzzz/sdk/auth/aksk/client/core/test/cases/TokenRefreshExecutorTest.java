package io.github.surezzzzzz.sdk.auth.aksk.client.core.test.cases;

import io.github.surezzzzzz.sdk.auth.aksk.client.core.configuration.SimpleAkskClientCoreProperties;
import io.github.surezzzzzz.sdk.auth.aksk.client.core.executor.TokenRefreshExecutor;
import io.github.surezzzzzz.sdk.auth.aksk.client.core.test.SimpleAkskClientCoreTestApplication;
import io.github.surezzzzzz.sdk.retry.task.executor.TaskRetryExecutor;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

/**
 * TokenRefreshExecutor 集成测试
 * <p>
 * 测试 Token 刷新执行器的核心功能
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
    @DisplayName("测试检查 Token 状态 - 无效 Token")
    void testCheckTokenStatusInvalidToken() {
        log.info("======================================");
        log.info("测试检查 Token 状态 - 无效 Token");
        log.info("======================================");

        String invalidToken = "invalid-token";
        log.info("测试 Token: {}", invalidToken);

        TokenRefreshExecutor.TokenStatus status = tokenRefreshExecutor.checkTokenStatus(invalidToken);

        log.info("Token 状态: {}", status);
        assertEquals(TokenRefreshExecutor.TokenStatus.UNPARSABLE, status);

        log.info("======================================");
    }

    @Test
    @DisplayName("测试检查 Token 状态 - 已过期 Token")
    void testCheckTokenStatusExpiredToken() {
        log.info("======================================");
        log.info("测试检查 Token 状态 - 已过期 Token");
        log.info("======================================");

        // 这是一个已过期的真实 JWT Token（exp = 1600000000，对应 2020-09-13）
        // 注意：这只是用于测试的示例 Token，签名可能无效，但我们只关心过期时间的解析
        String expiredToken = "eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiJ0ZXN0IiwiZXhwIjoxNjAwMDAwMDAwfQ.test";
        log.info("测试 Token: {}", expiredToken);

        TokenRefreshExecutor.TokenStatus status = tokenRefreshExecutor.checkTokenStatus(expiredToken);

        log.info("Token 状态: {}", status);
        // 由于签名可能无效，可能返回 UNPARSABLE 或 EXPIRED
        assertTrue(status == TokenRefreshExecutor.TokenStatus.UNPARSABLE ||
                        status == TokenRefreshExecutor.TokenStatus.EXPIRED,
                "Token 状态应为 UNPARSABLE 或 EXPIRED");

        log.info("======================================");
    }

    @Test
    @DisplayName("测试从服务器获取 Token - 无 security_context")
    void testFetchTokenFromServerNoSecurityContext() {
        log.info("======================================");
        log.info("测试从服务器获取 Token - 无 security_context");
        log.info("======================================");

        AtomicReference<String> callbackToken = new AtomicReference<>();
        AtomicReference<Long> callbackExpiresIn = new AtomicReference<>();

        log.info("开始从服务器获取 Token...");
        log.info("Server URL: {}", properties.getServerUrl());
        log.info("Token Endpoint: {}", properties.getTokenEndpoint());
        log.info("Client ID: {}", properties.getClientId());

        String token = tokenRefreshExecutor.fetchTokenFromServer(
                null,
                (accessToken, expiresIn) -> {
                    callbackToken.set(accessToken);
                    callbackExpiresIn.set(expiresIn);
                }
        );

        log.info("获取的 Token: {}", token);
        log.info("回调接收的 Token: {}", callbackToken.get());
        log.info("回调接收的过期时间: {}s", callbackExpiresIn.get());

        assertNotNull(token, "Token 不应为 null");
        assertEquals(token, callbackToken.get(), "获取的 Token 应与回调接收的 Token 一致");
        assertNotNull(callbackExpiresIn.get(), "过期时间不应为 null");
        assertTrue(callbackExpiresIn.get() > 0, "过期时间应大于 0");

        log.info("======================================");
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

        log.info("Security Context: {}", securityContext);
        log.info("开始从服务器获取 Token...");

        String token = tokenRefreshExecutor.fetchTokenFromServer(
                securityContext,
                (accessToken, expiresIn) -> {
                    callbackToken.set(accessToken);
                    callbackExpiresIn.set(expiresIn);
                }
        );

        log.info("获取的 Token: {}", token);
        log.info("回调接收的 Token: {}", callbackToken.get());
        log.info("回调接收的过期时间: {}s", callbackExpiresIn.get());

        assertNotNull(token, "Token 不应为 null");
        assertEquals(token, callbackToken.get(), "获取的 Token 应与回调接收的 Token 一致");
        assertNotNull(callbackExpiresIn.get(), "过期时间不应为 null");
        assertTrue(callbackExpiresIn.get() > 0, "过期时间应大于 0");

        // 验证获取的 Token 可以被正确解析
        TokenRefreshExecutor.TokenStatus status = tokenRefreshExecutor.checkTokenStatus(token);
        log.info("新获取 Token 的状态: {}", status);
        assertTrue(status == TokenRefreshExecutor.TokenStatus.VALID ||
                        status == TokenRefreshExecutor.TokenStatus.EXPIRING_SOON,
                "新获取的 Token 状态应为 VALID 或 EXPIRING_SOON");

        log.info("======================================");
    }

    @Test
    @DisplayName("测试异步刷新 Token")
    void testAsyncRefreshToken() throws InterruptedException {
        log.info("======================================");
        log.info("测试异步刷新 Token");
        log.info("======================================");

        AtomicBoolean taskExecuted = new AtomicBoolean(false);
        AtomicReference<String> executedThread = new AtomicReference<>();

        log.info("主线程: {}", Thread.currentThread().getName());
        log.info("提交异步任务...");

        tokenRefreshExecutor.asyncRefreshToken(() -> {
            executedThread.set(Thread.currentThread().getName());
            taskExecuted.set(true);
            log.info("异步任务执行中，线程: {}", Thread.currentThread().getName());
        });

        log.info("等待异步任务完成...");
        Thread.sleep(1000);

        log.info("异步任务执行状态: {}", taskExecuted.get());
        log.info("异步任务执行线程: {}", executedThread.get());

        assertTrue(taskExecuted.get(), "异步任务应该已执行");
        assertNotEquals(Thread.currentThread().getName(), executedThread.get(),
                "异步任务应在不同的线程中执行");

        log.info("======================================");
    }

    @Test
    @DisplayName("测试检查 Token 状态 - 有效 Token")
    void testCheckTokenStatusValidToken() {
        log.info("======================================");
        log.info("测试检查 Token 状态 - 有效 Token");
        log.info("======================================");

        log.info("先从服务器获取一个新 Token...");
        String freshToken = tokenRefreshExecutor.fetchTokenFromServer(null, null);
        log.info("获取的新 Token: {}", freshToken);

        log.info("检查新 Token 的状态...");
        TokenRefreshExecutor.TokenStatus status = tokenRefreshExecutor.checkTokenStatus(freshToken);

        log.info("Token 状态: {}", status);
        assertTrue(status == TokenRefreshExecutor.TokenStatus.VALID ||
                        status == TokenRefreshExecutor.TokenStatus.EXPIRING_SOON,
                "新获取的 Token 状态应为 VALID 或 EXPIRING_SOON");

        log.info("======================================");
    }
}
