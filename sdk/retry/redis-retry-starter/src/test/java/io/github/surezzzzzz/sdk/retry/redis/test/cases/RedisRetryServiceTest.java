package io.github.surezzzzzz.sdk.retry.redis.test.cases;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.surezzzzzz.sdk.retry.redis.model.RetryInfo;
import io.github.surezzzzzz.sdk.retry.redis.service.RedisRetryService;
import io.github.surezzzzzz.sdk.retry.redis.test.RedisRetryTestApplication;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import redis.embedded.RedisServer;

import java.net.ServerSocket;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Redis 重试服务测试
 *
 * @author surezzzzzz
 */
@Slf4j
@SpringBootTest(classes = RedisRetryTestApplication.class)
class RedisRetryServiceTest {

    private static int redisPort;

    private static RedisServer redisServer;

    @Autowired
    private RedisRetryService redisRetryService;

    @Autowired
    private RedisTemplate<String, String> redisRetryTemplate;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @DynamicPropertySource
    static void configureRedis(DynamicPropertyRegistry registry) throws Exception {
        redisPort = findAvailablePort();
        redisServer = new RedisServer(redisPort);
        redisServer.start();
        registry.add("spring.redis.host", () -> "localhost");
        registry.add("spring.redis.port", () -> redisPort);
    }

    @AfterEach
    void tearDown() {
        redisRetryTemplate.getConnectionFactory().getConnection().flushDb();
    }

    @AfterAll
    static void stopRedis() {
        if (redisServer != null) {
            redisServer.stop();
        }
    }

    @Test
    @DisplayName("测试首次执行允许重试")
    void testCanRetryFirstTimeShouldReturnTrue() {
        String retryType = "test-context";
        String retryKey = "test-key-1";

        boolean canRetry = redisRetryService.canRetry(retryType, retryKey);

        log.info("首次重试判断结果: retryType={}, retryKey={}, canRetry={}", retryType, retryKey, canRetry);
        assertTrue(canRetry, "第一次执行应该返回 true");
    }

    @Test
    @DisplayName("测试记录首次失败")
    void testRecordRetryFirstFailure() throws Exception {
        String retryType = "test-context";
        String retryKey = "test-key-2";
        int maxRetryTimes = 5;
        int retryInterval = 60;

        redisRetryService.recordRetry(retryType, retryKey, maxRetryTimes, retryInterval, TimeUnit.SECONDS);
        RetryInfo retryInfo = redisRetryService.getCurrentRetryInfo(retryType, retryKey);
        String fullKey = redisRetryService.buildRetryKey(retryType, retryKey);
        RetryInfo redisRetryInfo = objectMapper.readValue(redisRetryTemplate.opsForValue().get(fullKey), RetryInfo.class);
        Long ttl = redisRetryTemplate.getExpire(fullKey, TimeUnit.SECONDS);

        log.info("首次失败重试信息: retryInfo={}, redisRetryInfo={}, ttl={}", retryInfo, redisRetryInfo, ttl);
        assertNotNull(retryInfo, "重试信息不应为 null");
        assertEquals(1, retryInfo.getCount(), "重试次数应为 1");
        assertEquals(maxRetryTimes, retryInfo.getMaxRetryTimes(), "最大重试次数应匹配");
        assertEquals(TimeUnit.SECONDS.toMillis(retryInterval), retryInfo.getRetryIntervalMs(), "重试间隔毫秒数应匹配");
        assertNotNull(retryInfo.getFirstFailTime(), "首次失败时间不应为 null");
        assertNotNull(retryInfo.getLastFailTime(), "最近失败时间不应为 null");
        assertNotNull(retryInfo.getNextRetryTime(), "下次重试时间不应为 null");
        assertEquals(retryInfo.getLastFailTime() + retryInfo.getRetryIntervalMs(), retryInfo.getNextRetryTime(), "下次重试时间应等于最近失败时间加间隔");
        assertSameRetryInfo(retryInfo, redisRetryInfo);
        assertTrue(ttl > 3600, "TTL 应大于一小时缓冲时间");
    }

    @Test
    @DisplayName("测试多次失败计数累加")
    void testRecordRetryMultipleFailures() {
        String retryType = "test-context";
        String retryKey = "test-key-3";
        int maxRetryTimes = 5;

        redisRetryService.recordRetry(retryType, retryKey, maxRetryTimes, 60, TimeUnit.SECONDS);
        RetryInfo retryInfo1 = redisRetryService.getCurrentRetryInfo(retryType, retryKey);
        redisRetryService.recordRetry(retryType, retryKey, maxRetryTimes, 60, TimeUnit.SECONDS);
        RetryInfo retryInfo2 = redisRetryService.getCurrentRetryInfo(retryType, retryKey);
        redisRetryService.recordRetry(retryType, retryKey, maxRetryTimes, 60, TimeUnit.SECONDS);
        RetryInfo retryInfo3 = redisRetryService.getCurrentRetryInfo(retryType, retryKey);

        log.info("多次失败计数: first={}, second={}, third={}", retryInfo1, retryInfo2, retryInfo3);
        assertEquals(1, retryInfo1.getCount(), "第 1 次失败，count 应为 1");
        assertEquals(2, retryInfo2.getCount(), "第 2 次失败，count 应为 2");
        assertEquals(3, retryInfo3.getCount(), "第 3 次失败，count 应为 3");
    }

    @Test
    @DisplayName("测试达到最大次数后禁止重试")
    void testCanRetryReachedMaxRetryTimesShouldReturnFalse() {
        String retryType = "test-context";
        String retryKey = "test-key-4";
        int maxRetryTimes = 3;

        redisRetryService.recordRetry(retryType, retryKey, maxRetryTimes, 60, TimeUnit.SECONDS);
        redisRetryService.recordRetry(retryType, retryKey, maxRetryTimes, 60, TimeUnit.SECONDS);
        redisRetryService.recordRetry(retryType, retryKey, maxRetryTimes, 60, TimeUnit.SECONDS);
        boolean canRetry = redisRetryService.canRetry(retryType, retryKey);

        log.info("达到最大次数后的重试判断: canRetry={}", canRetry);
        assertFalse(canRetry, "达到最大重试次数后，canRetry 应返回 false");
    }

    @Test
    @DisplayName("测试重试间隔控制")
    void testCanRetryIntervalShouldBeChecked() throws InterruptedException {
        String retryType = "test-context";
        String retryKey = "test-key-5";
        int maxRetryTimes = 5;

        redisRetryService.recordRetry(retryType, retryKey, maxRetryTimes, 200, TimeUnit.MILLISECONDS);
        boolean canRetryImmediately = redisRetryService.canRetry(retryType, retryKey);
        TimeUnit.MILLISECONDS.sleep(300);
        boolean canRetryAfterInterval = redisRetryService.canRetry(retryType, retryKey);

        log.info("间隔判断结果: immediately={}, afterInterval={}", canRetryImmediately, canRetryAfterInterval);
        assertFalse(canRetryImmediately, "重试间隔未到，canRetry 应返回 false");
        assertTrue(canRetryAfterInterval, "重试间隔到达后，canRetry 应返回 true");
    }

    @Test
    @DisplayName("测试清除重试记录")
    void testClearRetryShouldAllowRetryAgain() {
        String retryType = "test-context";
        String retryKey = "test-key-6";
        int maxRetryTimes = 5;

        redisRetryService.recordRetry(retryType, retryKey, maxRetryTimes, 60, TimeUnit.SECONDS);
        String fullKey = redisRetryService.buildRetryKey(retryType, retryKey);
        RetryInfo retryInfoBefore = redisRetryService.getCurrentRetryInfo(retryType, retryKey);
        redisRetryService.clearRetry(retryType, retryKey);
        RetryInfo retryInfoAfter = redisRetryService.getCurrentRetryInfo(retryType, retryKey);
        boolean canRetry = redisRetryService.canRetry(retryType, retryKey);
        Boolean keyExists = redisRetryTemplate.hasKey(fullKey);

        log.info("清除前后重试信息: before={}, after={}, canRetry={}, keyExists={}", retryInfoBefore, retryInfoAfter, canRetry, keyExists);
        assertNotNull(retryInfoBefore, "清除前应该有重试信息");
        assertNull(retryInfoAfter, "清除后重试信息应该为 null");
        assertFalse(Boolean.TRUE.equals(keyExists), "清除后 Redis Key 不应存在");
        assertTrue(canRetry, "清除后应该可以重新开始");
    }

    @Test
    @DisplayName("测试上下文写入和更新")
    void testContextShouldBeMerged() {
        String retryType = "test-context";
        String retryKey = "test-key-7";
        Map<String, Object> firstContext = new HashMap<>();
        firstContext.put("extraField", "value1");
        Map<String, Object> secondContext = new HashMap<>();
        secondContext.put("extraField2", "value2");

        redisRetryService.recordRetry(retryType, retryKey, 5, 60, TimeUnit.SECONDS, firstContext);
        redisRetryService.recordRetry(retryType, retryKey, 5, 60, TimeUnit.SECONDS, secondContext);
        RetryInfo retryInfo = redisRetryService.getCurrentRetryInfo(retryType, retryKey);

        log.info("合并后的上下文: {}", retryInfo.getContext());
        assertEquals("value1", retryInfo.getContext().get("extraField"), "应保留第一次上下文");
        assertEquals("value2", retryInfo.getContext().get("extraField2"), "应追加第二次上下文");
    }

    @Test
    @DisplayName("测试不同 retryKey 独立计数")
    void testDifferentRetryKeysShouldBeIndependent() {
        String retryType = "test-context";
        String retryKey1 = "test-key-8-a";
        String retryKey2 = "test-key-8-b";

        redisRetryService.recordRetry(retryType, retryKey1, 5, 60, TimeUnit.SECONDS);
        redisRetryService.recordRetry(retryType, retryKey1, 5, 60, TimeUnit.SECONDS);
        redisRetryService.recordRetry(retryType, retryKey2, 5, 60, TimeUnit.SECONDS);
        RetryInfo retryInfo1 = redisRetryService.getCurrentRetryInfo(retryType, retryKey1);
        RetryInfo retryInfo2 = redisRetryService.getCurrentRetryInfo(retryType, retryKey2);
        List<String> retryKeys = redisRetryService.getRetryKeys(retryType);
        String fullKey1 = redisRetryService.buildRetryKey(retryType, retryKey1);
        String fullKey2 = redisRetryService.buildRetryKey(retryType, retryKey2);

        log.info("不同 key 重试信息: retryInfo1={}, retryInfo2={}, retryKeys={}", retryInfo1, retryInfo2, retryKeys);
        assertEquals(2, retryInfo1.getCount(), "retryKey1 失败 2 次");
        assertEquals(1, retryInfo2.getCount(), "retryKey2 失败 1 次");
        assertEquals(2, retryKeys.size(), "应查询到两个重试 Key");
        assertTrue(retryKeys.contains(fullKey1), "重试 Key 列表应包含 retryKey1 对应 Key");
        assertTrue(retryKeys.contains(fullKey2), "重试 Key 列表应包含 retryKey2 对应 Key");
    }

    @Test
    @DisplayName("测试不同 retryType 独立计数")
    void testDifferentRetryTypesShouldBeIndependent() {
        String retryType1 = "context-1";
        String retryType2 = "context-2";
        String retryKey = "test-key-9";

        redisRetryService.recordRetry(retryType1, retryKey, 5, 60, TimeUnit.SECONDS);
        redisRetryService.recordRetry(retryType2, retryKey, 5, 60, TimeUnit.SECONDS);
        redisRetryService.recordRetry(retryType2, retryKey, 5, 60, TimeUnit.SECONDS);
        RetryInfo retryInfo1 = redisRetryService.getCurrentRetryInfo(retryType1, retryKey);
        RetryInfo retryInfo2 = redisRetryService.getCurrentRetryInfo(retryType2, retryKey);

        log.info("不同 type 重试信息: retryInfo1={}, retryInfo2={}", retryInfo1, retryInfo2);
        assertEquals(1, retryInfo1.getCount(), "retryType1 失败 1 次");
        assertEquals(2, retryInfo2.getCount(), "retryType2 失败 2 次");
    }

    @Test
    @DisplayName("测试标准 Key 包含 me")
    void testStandardKeyShouldContainMe() throws Exception {
        String retryType = "test-context";
        String retryKey = "test-key-10";

        String fullKey = redisRetryService.buildRetryKey(retryType, retryKey);
        String expectedKey = "sure-redis-retry-test:retry:test-context:test-app::" + sha1(retryKey);

        log.info("标准重试 Key: {}, expectedKey={}", fullKey, expectedKey);
        assertEquals(expectedKey, fullKey, "标准 Key 应由 keyPrefix、retryType、me 和 retryKey 摘要组成");
    }

    @Test
    @DisplayName("测试 legacy Key 可读取并迁移")
    void testLegacyKeyShouldBeReadableAndMigrated() throws Exception {
        String retryType = "test-context";
        String retryKey = "test-key-11";
        String legacyKey = "test-context:retry:" + sha1(retryKey);
        RetryInfo legacyInfo = new RetryInfo();
        legacyInfo.setCount(1);
        legacyInfo.setMaxRetryTimes(5);
        legacyInfo.setRetryIntervalMs(60000L);
        legacyInfo.setFirstFailTime(System.currentTimeMillis());
        legacyInfo.setLastFailTime(System.currentTimeMillis());
        legacyInfo.setNextRetryTime(System.currentTimeMillis() + 60000L);
        redisRetryTemplate.opsForValue().set(legacyKey, objectMapper.writeValueAsString(legacyInfo));

        String standardKey = redisRetryService.buildRetryKey(retryType, retryKey);
        RetryInfo beforeRecord = redisRetryService.getCurrentRetryInfo(retryType, retryKey);
        redisRetryService.recordRetry(retryType, retryKey, 5, 60, TimeUnit.SECONDS);
        RetryInfo afterRecord = redisRetryService.getCurrentRetryInfo(retryType, retryKey);
        RetryInfo redisRetryInfo = objectMapper.readValue(redisRetryTemplate.opsForValue().get(standardKey), RetryInfo.class);
        Boolean legacyExists = redisRetryTemplate.hasKey(legacyKey);
        Boolean standardExists = redisRetryTemplate.hasKey(standardKey);
        List<String> retryKeys = redisRetryService.getRetryKeys(retryType);

        log.info("legacy 迁移结果: before={}, after={}, redisRetryInfo={}, legacyExists={}, standardExists={}, retryKeys={}",
                beforeRecord, afterRecord, redisRetryInfo, legacyExists, standardExists, retryKeys);
        assertEquals(1, beforeRecord.getCount(), "应能读取 legacy 记录");
        assertEquals(2, afterRecord.getCount(), "继续记录后应累加 legacy 计数");
        assertSameRetryInfo(afterRecord, redisRetryInfo);
        assertFalse(Boolean.TRUE.equals(legacyExists), "迁移后 legacy Key 应被清理");
        assertTrue(Boolean.TRUE.equals(standardExists), "迁移后标准 Key 应存在");
        assertEquals(1, retryKeys.size(), "迁移后同一记录只应查询到一个 Key");
        assertEquals(standardKey, retryKeys.get(0), "迁移后查询结果应返回标准 Key");
    }

    @Test
    @DisplayName("测试 fullKey API 保持兼容")
    void testFullKeyApiShouldBeCompatible() {
        String fullKey = "legacy-full-key";
        RuntimeException error = new RuntimeException("test error");

        boolean firstCanRetry = redisRetryService.canRetry(fullKey);
        redisRetryService.recordFailure(fullKey, error);
        RetryInfo retryInfo = redisRetryService.getCurrentRetryInfo(fullKey);
        boolean secondCanRetry = redisRetryService.canRetry(fullKey);
        redisRetryService.clearRetryRecord(fullKey);
        RetryInfo clearedInfo = redisRetryService.getCurrentRetryInfo(fullKey);
        Boolean keyExists = redisRetryTemplate.hasKey(fullKey);

        log.info("fullKey API 兼容结果: firstCanRetry={}, retryInfo={}, secondCanRetry={}, clearedInfo={}, keyExists={}",
                firstCanRetry, retryInfo, secondCanRetry, clearedInfo, keyExists);
        assertTrue(firstCanRetry, "fullKey 首次应允许重试");
        assertEquals(1, retryInfo.getCount(), "fullKey 记录失败后 count 应为 1");
        assertEquals("test error", retryInfo.getLastError(), "fullKey 记录应保存错误信息");
        assertTrue(secondCanRetry, "未达到默认最大次数前 fullKey 仍应允许重试");
        assertEquals(0, clearedInfo.getCount(), "fullKey 清理后默认信息 count 应为 0");
        assertFalse(Boolean.TRUE.equals(keyExists), "fullKey 清理后 Redis Key 不应存在");
    }

    @Test
    @DisplayName("测试无记录时返回 null")
    void testGetCurrentRetryInfoNotExistsShouldReturnNull() {
        String retryType = "test-context";
        String retryKey = "test-key-12";

        RetryInfo retryInfo = redisRetryService.getCurrentRetryInfo(retryType, retryKey);

        log.info("不存在记录查询结果: {}", retryInfo);
        assertNull(retryInfo, "没有记录时应该返回 null");
    }

    private static int findAvailablePort() {
        try {
            ServerSocket socket = new ServerSocket(0);
            int port = socket.getLocalPort();
            socket.close();
            return port;
        } catch (Exception e) {
            return 6380;
        }
    }

    private String sha1(String value) throws Exception {
        java.security.MessageDigest messageDigest = java.security.MessageDigest.getInstance("SHA-1");
        byte[] digest = messageDigest.digest(value.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        StringBuilder builder = new StringBuilder();
        for (byte item : digest) {
            builder.append(String.format("%02x", item));
        }
        return builder.toString().toUpperCase();
    }

    private void assertSameRetryInfo(RetryInfo expected, RetryInfo actual) {
        assertEquals(expected.getCount(), actual.getCount(), "Redis 中 count 应与查询结果一致");
        assertEquals(expected.getMaxRetryTimes(), actual.getMaxRetryTimes(), "Redis 中 maxRetryTimes 应与查询结果一致");
        assertEquals(expected.getRetryIntervalMs(), actual.getRetryIntervalMs(), "Redis 中 retryIntervalMs 应与查询结果一致");
        assertEquals(expected.getFirstFailTime(), actual.getFirstFailTime(), "Redis 中 firstFailTime 应与查询结果一致");
        assertEquals(expected.getLastFailTime(), actual.getLastFailTime(), "Redis 中 lastFailTime 应与查询结果一致");
        assertEquals(expected.getNextRetryTime(), actual.getNextRetryTime(), "Redis 中 nextRetryTime 应与查询结果一致");
        assertEquals(expected.getLastError(), actual.getLastError(), "Redis 中 lastError 应与查询结果一致");
        assertEquals(expected.getContext(), actual.getContext(), "Redis 中 context 应与查询结果一致");
    }
}
