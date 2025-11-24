package io.github.surezzzzzz.sdk.retry.redis.cases;

import io.github.surezzzzzz.sdk.retry.redis.model.RetryInfo;
import io.github.surezzzzzz.sdk.retry.redis.service.RedisRetryService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import redis.embedded.RedisServer;

import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * RedisRetryService 单元测试
 *
 * @author: Sure.
 * @Date: 2025/10/25
 */
@SpringBootTest
class RedisRetryServiceTest {

    private RedisServer redisServer;
    private static int redisPort;

    @Autowired
    private RedisRetryService redisRetryService;

    @DynamicPropertySource
    static void configureRedis(DynamicPropertyRegistry registry) {
        redisPort = findAvailablePort();
        registry.add("spring.redis.host", () -> "localhost");
        registry.add("spring.redis.port", () -> redisPort);
    }

    @BeforeEach
    void setUp() throws Exception {
        try {
            redisServer = new RedisServer(redisPort);
            redisServer.start();
        } catch (Exception e) {
            redisPort = findAvailablePort();
            redisServer = new RedisServer(redisPort);
            redisServer.start();
        }
    }

    @AfterEach
    void tearDown() throws Exception {
        if (redisServer != null) {
            try {
                redisServer.stop();
            } catch (Exception e) {
                // 忽略停止时的异常
            }
        }
    }

    private static int findAvailablePort() {
        try (java.net.ServerSocket socket = new java.net.ServerSocket(0)) {
            return socket.getLocalPort();
        } catch (Exception e) {
            return 6380 + (int) (Math.random() * 1000);
        }
    }

    // ==================== 测试用例 ====================

    /**
     * 测试：第一次执行，没有重试记录，应该返回 true
     */
    @Test
    void testCanRetryFirstTimeShouldReturnTrue() {
        // Given
        String retryContext = "test-context";
        String retryKey = "test-key-1";

        // When
        boolean canRetry = redisRetryService.canRetry(retryContext, retryKey);

        // Then
        assertTrue(canRetry, "第一次执行应该返回 true");
    }

    /**
     * 测试：记录第一次失败
     */
    @Test
    void testRecordRetryFirstFailure() {
        // Given
        String retryContext = "test-context";
        String retryKey = "test-key-2";
        int maxRetryTimes = 5;
        int retryInterval = 60;

        // When
        redisRetryService.recordRetry(retryContext, retryKey, maxRetryTimes, retryInterval, TimeUnit.SECONDS);

        // Then
        RetryInfo retryInfo = redisRetryService.getCurrentRetryInfo(retryContext, retryKey);

        assertNotNull(retryInfo, "重试信息不应为 null");
        assertEquals(1, retryInfo.getCount(), "重试次数应为 1");
        assertEquals(maxRetryTimes, retryInfo.getMaxRetryTimes(), "最大重试次数应为 5");
        assertNotNull(retryInfo.getNextRetryTime(), "下次重试时间不应为 null");
    }

    /**
     * 测试：多次记录失败，重试次数应该累加
     */
    @Test
    void testRecordRetryMultipleFailures() {
        // Given
        String retryContext = "test-context";
        String retryKey = "test-key-3";
        int maxRetryTimes = 5;

        // When - 第 1 次失败
        redisRetryService.recordRetry(retryContext, retryKey, maxRetryTimes, 60, TimeUnit.SECONDS);
        RetryInfo retryInfo1 = redisRetryService.getCurrentRetryInfo(retryContext, retryKey);

        // When - 第 2 次失败
        redisRetryService.recordRetry(retryContext, retryKey, maxRetryTimes, 60, TimeUnit.SECONDS);
        RetryInfo retryInfo2 = redisRetryService.getCurrentRetryInfo(retryContext, retryKey);

        // When - 第 3 次失败
        redisRetryService.recordRetry(retryContext, retryKey, maxRetryTimes, 60, TimeUnit.SECONDS);
        RetryInfo retryInfo3 = redisRetryService.getCurrentRetryInfo(retryContext, retryKey);

        // Then
        assertEquals(1, retryInfo1.getCount(), "第 1 次失败，count 应为 1");
        assertEquals(2, retryInfo2.getCount(), "第 2 次失败，count 应为 2");
        assertEquals(3, retryInfo3.getCount(), "第 3 次失败，count 应为 3");
    }

    /**
     * 测试：达到最大重试次数后，canRetry 应返回 false
     */
    @Test
    void testCanRetryReachedMaxRetryTimesShouldReturnFalse() {
        // Given
        String retryContext = "test-context";
        String retryKey = "test-key-4";
        int maxRetryTimes = 3;

        // When - 记录 3 次失败（达到最大次数）
        redisRetryService.recordRetry(retryContext, retryKey, maxRetryTimes, 60, TimeUnit.SECONDS);
        redisRetryService.recordRetry(retryContext, retryKey, maxRetryTimes, 60, TimeUnit.SECONDS);
        redisRetryService.recordRetry(retryContext, retryKey, maxRetryTimes, 60, TimeUnit.SECONDS);

        // Then
        boolean canRetry = redisRetryService.canRetry(retryContext, retryKey);
        assertFalse(canRetry, "达到最大重试次数后，canRetry 应返回 false");
    }

    /**
     * 测试：重试间隔未到，canRetry 应返回 false
     */
    @Test
    void testCanRetryIntervalNotReachedShouldReturnFalse() throws InterruptedException {
        // Given
        String retryContext = "test-context";
        String retryKey = "test-key-5";
        int maxRetryTimes = 5;
        int retryInterval = 2; // 2 秒

        // When - 记录第 1 次失败
        redisRetryService.recordRetry(retryContext, retryKey, maxRetryTimes, retryInterval, TimeUnit.SECONDS);

        // Then - 立即检查，应该返回 false（因为间隔未到）
        boolean canRetryImmediately = redisRetryService.canRetry(retryContext, retryKey);
        assertFalse(canRetryImmediately, "重试间隔未到，canRetry 应返回 false");

        // When - 等待 2.5 秒后再检查
        Thread.sleep(2500);

        // Then - 应该返回 true
        boolean canRetryAfterInterval = redisRetryService.canRetry(retryContext, retryKey);
        assertTrue(canRetryAfterInterval, "重试间隔到达后，canRetry 应返回 true");
    }

    /**
     * 测试：清除重试信息后，应该能重新开始
     */
    @Test
    void testClearRetryShouldAllowRetryAgain() {
        // Given
        String retryContext = "test-context";
        String retryKey = "test-key-6";
        int maxRetryTimes = 5;

        // When - 记录失败
        redisRetryService.recordRetry(retryContext, retryKey, maxRetryTimes, 60, TimeUnit.SECONDS);

        // Then - 应该有重试信息
        RetryInfo retryInfoBefore = redisRetryService.getCurrentRetryInfo(retryContext, retryKey);
        assertNotNull(retryInfoBefore, "清除前应该有重试信息");

        // When - 清除重试信息
        redisRetryService.clearRetry(retryContext, retryKey);

        // Then - 重试信息应该为 null
        RetryInfo retryInfoAfter = redisRetryService.getCurrentRetryInfo(retryContext, retryKey);
        assertNull(retryInfoAfter, "清除后重试信息应该为 null");

        // Then - 应该可以重新开始
        boolean canRetry = redisRetryService.canRetry(retryContext, retryKey);
        assertTrue(canRetry, "清除后应该可以重新开始");
    }

    /**
     * 测试：成功后清除，重试次数应该重置
     */
    @Test
    void testSuccessFlowRecordThenClear() {
        // Given
        String retryContext = "test-context";
        String retryKey = "test-key-7";
        int maxRetryTimes = 5;

        // When - 记录 2 次失败
        redisRetryService.recordRetry(retryContext, retryKey, maxRetryTimes, 60, TimeUnit.SECONDS);
        redisRetryService.recordRetry(retryContext, retryKey, maxRetryTimes, 60, TimeUnit.SECONDS);

        RetryInfo retryInfoBefore = redisRetryService.getCurrentRetryInfo(retryContext, retryKey);
        assertEquals(2, retryInfoBefore.getCount(), "失败 2 次，count 应为 2");

        // When - 成功后清除
        redisRetryService.clearRetry(retryContext, retryKey);

        // When - 再次记录失败
        redisRetryService.recordRetry(retryContext, retryKey, maxRetryTimes, 60, TimeUnit.SECONDS);

        // Then - 重试次数应该从 1 开始
        RetryInfo retryInfoAfter = redisRetryService.getCurrentRetryInfo(retryContext, retryKey);
        assertEquals(1, retryInfoAfter.getCount(), "清除后重新记录，count 应为 1");
    }

    /**
     * 测试：不同的 retryKey 应该独立计数
     */
    @Test
    void testDifferentRetryKeysShouldBeIndependent() {
        // Given
        String retryContext = "test-context";
        String retryKey1 = "test-key-8-a";
        String retryKey2 = "test-key-8-b";
        int maxRetryTimes = 5;

        // When
        redisRetryService.recordRetry(retryContext, retryKey1, maxRetryTimes, 60, TimeUnit.SECONDS);
        redisRetryService.recordRetry(retryContext, retryKey1, maxRetryTimes, 60, TimeUnit.SECONDS);

        redisRetryService.recordRetry(retryContext, retryKey2, maxRetryTimes, 60, TimeUnit.SECONDS);

        // Then
        RetryInfo retryInfo1 = redisRetryService.getCurrentRetryInfo(retryContext, retryKey1);
        RetryInfo retryInfo2 = redisRetryService.getCurrentRetryInfo(retryContext, retryKey2);

        assertEquals(2, retryInfo1.getCount(), "retryKey1 失败 2 次，count 应为 2");
        assertEquals(1, retryInfo2.getCount(), "retryKey2 失败 1 次，count 应为 1");
    }

    /**
     * 测试：不同的 retryContext 应该独立
     */
    @Test
    void testDifferentRetryContextsShouldBeIndependent() {
        // Given
        String retryContext1 = "context-1";
        String retryContext2 = "context-2";
        String retryKey = "test-key-9";
        int maxRetryTimes = 5;

        // When
        redisRetryService.recordRetry(retryContext1, retryKey, maxRetryTimes, 60, TimeUnit.SECONDS);
        redisRetryService.recordRetry(retryContext2, retryKey, maxRetryTimes, 60, TimeUnit.SECONDS);
        redisRetryService.recordRetry(retryContext2, retryKey, maxRetryTimes, 60, TimeUnit.SECONDS);

        // Then
        RetryInfo retryInfo1 = redisRetryService.getCurrentRetryInfo(retryContext1, retryKey);
        RetryInfo retryInfo2 = redisRetryService.getCurrentRetryInfo(retryContext2, retryKey);

        assertEquals(1, retryInfo1.getCount(), "context1 失败 1 次");
        assertEquals(2, retryInfo2.getCount(), "context2 失败 2 次");
    }

    /**
     * 测试：getCurrentRetryInfo 返回 null（没有记录时）
     */
    @Test
    void testGetCurrentRetryInfoNotExistsShouldReturnNull() {
        // Given
        String retryContext = "test-context";
        String retryKey = "test-key-10";

        // When
        RetryInfo retryInfo = redisRetryService.getCurrentRetryInfo(retryContext, retryKey);

        // Then
        assertNull(retryInfo, "没有记录时应该返回 null");
    }

}
