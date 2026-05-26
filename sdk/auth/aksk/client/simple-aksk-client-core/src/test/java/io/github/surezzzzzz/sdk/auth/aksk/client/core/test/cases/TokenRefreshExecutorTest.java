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
