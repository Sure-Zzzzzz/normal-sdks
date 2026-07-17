package io.github.surezzzzzz.sdk.retry.redis.smart.test.cases;

import io.github.surezzzzzz.sdk.retry.redis.smart.constant.RetryDecisionType;
import io.github.surezzzzzz.sdk.redis.route.template.RedisRouteTemplate;
import io.github.surezzzzzz.sdk.retry.redis.smart.configuration.SmartRedisRetryProperties;
import io.github.surezzzzzz.sdk.retry.redis.smart.engine.SmartRedisRetryEngine;
import io.github.surezzzzzz.sdk.retry.redis.smart.facade.RetryScene;
import io.github.surezzzzzz.sdk.retry.redis.smart.model.RetryDecision;
import io.github.surezzzzzz.sdk.retry.redis.smart.model.RetryFailure;
import io.github.surezzzzzz.sdk.retry.redis.smart.model.RetryInfo;
import io.github.surezzzzzz.sdk.retry.redis.smart.model.RetryPolicy;
import io.github.surezzzzzz.sdk.retry.redis.smart.model.RetryScanRequest;
import io.github.surezzzzzz.sdk.retry.redis.smart.model.RetryScanResult;
import io.github.surezzzzzz.sdk.retry.redis.smart.support.RetryKeyHelper;
import io.github.surezzzzzz.sdk.retry.redis.smart.test.SmartRedisRetryTestApplication;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import lombok.extern.slf4j.Slf4j;

/**
 * {@link SmartRedisRetryEngine} 端到端测试（依赖本地 Redis 16379）。
 *
 * @author surezzzzzz
 */
@SpringBootTest(classes = SmartRedisRetryTestApplication.class)
@Slf4j
class SmartRedisRetryEngineTest {

    @Autowired
    private SmartRedisRetryEngine engine;

    @Autowired
    private RetryKeyHelper retryKeyHelper;

    @Autowired
    private RedisRouteTemplate redisRouteTemplate;

    @Autowired
    private SmartRedisRetryProperties properties;

    @AfterEach
    void cleanUp() {
        log.info("清理 SmartRedisRetryEngine 测试资源");
        for (String retryKey : new String[]{"matrix-key", "matrix-key-2", "scan-key-1", "scan-key-2", "scene-key", "exhausted-key",
                "scene-policy-key", "failure-policy-key", "default-policy-key", "retain-key", "ttl-key", "context-update-key"}) {
            engine.clear("test-compensation", retryKey);
        }
        for (int i = 0; i < 40; i++) {
            engine.clear("test-compensation", "scan-page-key-" + i);
        }
        properties.getPolicy().getScene().remove("test-compensation");
        properties.getPolicy().getDefaultPolicy().setMaxRetryTimes(20);
        properties.getPolicy().getDefaultPolicy().setRetryIntervalMillis(60000L);
        properties.getPolicy().getDefaultPolicy().setMaxIntervalMillis(1800000L);
        properties.getPolicy().getDefaultPolicy().setBackoffMultiplier(2D);
        properties.getPolicy().getDefaultPolicy().setJitterRatio(0.2D);
        properties.getRedis().setRetainExhausted(true);
        properties.getRedis().setRecordTtlSeconds(86400L);
    }

    @Test
    void decideShouldReturnAllowWhenNoRecord() {
        RetryDecision decision = engine.decide("test-compensation", "matrix-key");
        assertEquals(RetryDecisionType.ALLOW, decision.getType());
        assertTrue(decision.isAllowed());
        assertTrue(engine.canRetry("test-compensation", "matrix-key"));
    }

    @Test
    void recordFailureShouldReturnLatestRetryInfo() {
        RetryInfo info = engine.recordFailure("test-compensation", "matrix-key");
        assertNotNull(info);
        assertEquals(Integer.valueOf(1), info.getCount());
        assertEquals(Integer.valueOf(20), info.getMaxRetryTimes());
        assertNotNull(info.getFirstFailTime());
        assertNotNull(info.getNextRetryTime());
        assertTrue(info.getNextRetryTime() >= info.getLastFailTime());
    }

    @Test
    void decideShouldReturnWaitingBeforeNextRetryTime() {
        engine.recordFailure("test-compensation", "matrix-key");
        RetryDecision decision = engine.decide("test-compensation", "matrix-key");
        assertEquals(RetryDecisionType.WAITING, decision.getType());
        assertFalse(decision.isAllowed());
        assertNotNull(decision.getWaitMillis());
        assertTrue(decision.getWaitMillis() > 0L);
    }

    @Test
    void countShouldIncreaseOnMultipleRecordFailure() {
        engine.recordFailure("test-compensation", "matrix-key");
        RetryInfo info = engine.recordFailure("test-compensation", "matrix-key");
        assertEquals(Integer.valueOf(2), info.getCount());
    }

    @Test
    void clearShouldRemoveRecordAndReturnPreviousState() {
        engine.recordFailure("test-compensation", "matrix-key");
        RetryInfo before = engine.clear("test-compensation", "matrix-key");
        assertNotNull(before);
        assertNull(engine.getInfo("test-compensation", "matrix-key"));
    }

    @Test
    void clearShouldReturnStoredContextFromAtomicScript() {
        Map<String, Object> context = new HashMap<String, Object>();
        context.put("extraField", "mock-value");
        engine.recordFailure(RetryFailure.builder()
                .retryType("test-compensation")
                .retryKey("matrix-key")
                .context(context)
                .build());
        RetryInfo before = engine.clear("test-compensation", "matrix-key");
        assertNotNull(before);
        assertEquals("mock-value", before.getContext().get("extraField"));
        assertNull(engine.getInfo("test-compensation", "matrix-key"));
    }

    @Test
    void recordFailureWithExplicitFailureShouldPreserveContext() {
        Map<String, Object> context = new HashMap<String, Object>();
        context.put("extraField", "mock-value");
        RetryFailure failure = RetryFailure.builder()
                .retryType("test-compensation")
                .retryKey("matrix-key-2")
                .errorCode("MOCK_ERROR")
                .errorMessage("mock message")
                .context(context)
                .build();
        RetryInfo info = engine.recordFailure(failure);
        assertNotNull(info);
        assertEquals("MOCK_ERROR", info.getLastErrorCode());
        assertEquals("mock message", info.getLastErrorMessage());
        assertEquals("mock-value", info.getContext().get("extraField"));
    }

    @Test
    void scanShouldReturnKeysAndInfos() {
        engine.recordFailure("test-compensation", "scan-key-1");
        engine.recordFailure("test-compensation", "scan-key-2");
        RetryScanResult result = scanUntilFinished("sure-smart-redis-retry:retry:test-compensation:default::mock-route",
                "test-compensation", 50, true);
        String redisKey1 = retryKeyHelper.buildRedisKey("test-compensation", "scan-key-1");
        String redisKey2 = retryKeyHelper.buildRedisKey("test-compensation", "scan-key-2");
        assertTrue(result.isFinished());
        assertTrue(result.getKeys().contains(redisKey1));
        assertTrue(result.getKeys().contains(redisKey2));
        assertEquals(Integer.valueOf(1), result.getInfos().get(redisKey1).getCount());
        assertEquals(Integer.valueOf(1), result.getInfos().get(redisKey2).getCount());
    }

    @Test
    void scanShouldContinueFromReturnedCursorUntilFinished() {
        Set<String> expectedKeys = new HashSet<String>();
        for (int i = 0; i < 40; i++) {
            String retryKey = "scan-page-key-" + i;
            engine.recordFailure("test-compensation", retryKey);
            expectedKeys.add(retryKeyHelper.buildRedisKey("test-compensation", retryKey));
        }
        Set<String> actualKeys = new HashSet<String>();
        String cursor = "0";
        int pages = 0;
        do {
            RetryScanResult result = engine.scan(RetryScanRequest.builder()
                    .routeKey("sure-smart-redis-retry:retry:test-compensation:default::mock-route")
                    .retryType("test-compensation")
                    .cursor(cursor)
                    .count(1)
                    .includeInfo(false)
                    .build());
            actualKeys.addAll(result.getKeys());
            cursor = result.getNextCursor();
            pages++;
            assertTrue(pages <= 200, "SCAN 分页必须在合理次数内结束");
        } while (!"0".equals(cursor));
        assertTrue(actualKeys.containsAll(expectedKeys), "分页 SCAN 应最终覆盖所有目标记录");
        assertTrue(pages > 1, "count=1 时应通过多页游标扫描完成");
        log.info("分页 SCAN 验证通过，pages={}，keys={}", pages, actualKeys.size());
    }

    @Test
    void sceneShouldBindRetryType() {
        RetryScene scene = engine.scene("test-compensation");
        assertTrue(scene.canRetry("scene-key"));
        RetryInfo info = scene.recordFailure(RetryFailure.builder()
                .retryType("wrong-type")
                .retryKey("scene-key")
                .errorCode("SCENE_ERROR")
                .build());
        assertNotNull(info);
        assertEquals("SCENE_ERROR", scene.getInfo("scene-key").getLastErrorCode());
        assertEquals(RetryDecisionType.WAITING, scene.decide("scene-key").getType());
        assertNotNull(scene.clear("scene-key"));
    }

    @Test
    void decideShouldReturnExhaustedWhenMaxRetryTimesReached() {
        RetryFailure failure = RetryFailure.builder()
                .retryType("test-compensation")
                .retryKey("exhausted-key")
                .policy(RetryPolicy.builder()
                        .maxRetryTimes(1)
                        .retryIntervalMillis(60000L)
                        .maxIntervalMillis(60000L)
                        .backoffMultiplier(1D)
                        .jitterRatio(0D)
                        .build())
                .build();
        engine.recordFailure(failure);
        RetryDecision decision = engine.decide("test-compensation", "exhausted-key");
        assertEquals(RetryDecisionType.EXHAUSTED, decision.getType());
        assertFalse(decision.isAllowed());
        assertEquals(Integer.valueOf(1), decision.getCurrentCount());
        assertEquals(Integer.valueOf(1), decision.getMaxRetryTimes());
        assertNotNull(engine.getInfo("test-compensation", "exhausted-key"));
    }

    @Test
    void engineBehaviorShouldUseScenePolicyBeforeDefaultPolicy() {
        properties.getPolicy().getDefaultPolicy().setMaxRetryTimes(9);
        properties.getPolicy().getScene().put("test-compensation", RetryPolicy.builder()
                .maxRetryTimes(2)
                .retryIntervalMillis(60000L)
                .maxIntervalMillis(60000L)
                .backoffMultiplier(1D)
                .jitterRatio(0D)
                .build());
        engine.recordFailure("test-compensation", "scene-policy-key");
        RetryInfo second = engine.recordFailure("test-compensation", "scene-policy-key");
        assertEquals(Integer.valueOf(2), second.getCount());
        assertEquals(Integer.valueOf(2), second.getMaxRetryTimes());
        RetryDecision decision = engine.decide("test-compensation", "scene-policy-key");
        assertEquals(RetryDecisionType.EXHAUSTED, decision.getType());
        assertEquals(Integer.valueOf(2), decision.getMaxRetryTimes());
    }

    @Test
    void engineBehaviorShouldUseFailurePolicyBeforeScenePolicy() {
        properties.getPolicy().getScene().put("test-compensation", RetryPolicy.builder()
                .maxRetryTimes(9)
                .retryIntervalMillis(60000L)
                .maxIntervalMillis(60000L)
                .backoffMultiplier(1D)
                .jitterRatio(0D)
                .build());
        RetryFailure failure = RetryFailure.builder()
                .retryType("test-compensation")
                .retryKey("failure-policy-key")
                .policy(RetryPolicy.builder()
                        .maxRetryTimes(1)
                        .retryIntervalMillis(60000L)
                        .maxIntervalMillis(60000L)
                        .backoffMultiplier(1D)
                        .jitterRatio(0D)
                        .build())
                .build();
        RetryInfo info = engine.recordFailure(failure);
        assertEquals(Integer.valueOf(1), info.getMaxRetryTimes());
        RetryDecision decision = engine.decide("test-compensation", "failure-policy-key");
        assertEquals(RetryDecisionType.EXHAUSTED, decision.getType());
        assertEquals(Integer.valueOf(1), decision.getMaxRetryTimes());
    }

    @Test
    void engineBehaviorShouldUseDefaultPolicyWhenNoOverrideExists() {
        properties.getPolicy().getDefaultPolicy().setMaxRetryTimes(4);
        RetryInfo info = engine.recordFailure("test-compensation", "default-policy-key");
        assertEquals(Integer.valueOf(4), info.getMaxRetryTimes());
        RetryDecision decision = engine.decide("test-compensation", "default-policy-key");
        assertEquals(RetryDecisionType.WAITING, decision.getType());
        assertEquals(Integer.valueOf(4), decision.getMaxRetryTimes());
    }

    @Test
    void recordFailureShouldSetRedisTtlFromConfiguration() {
        properties.getRedis().setRecordTtlSeconds(120L);
        engine.recordFailure("test-compensation", "ttl-key");
        String redisKey = retryKeyHelper.buildRedisKey("test-compensation", "ttl-key");
        Long ttl = redisRouteTemplate.execute(redisKey, template -> template.getExpire(redisKey, TimeUnit.SECONDS));
        assertNotNull(ttl);
        assertTrue(ttl > 0L, "Redis 记录必须设置 TTL");
        assertTrue(ttl <= 120L, "TTL 不应超过配置值");
        assertTrue(ttl >= 100L, "TTL 不应明显低于刚设置的配置值");
    }

    @Test
    void exhaustedRecordShouldBeRemovedWhenRetainExhaustedDisabled() {
        properties.getRedis().setRetainExhausted(false);
        engine.recordFailure(RetryFailure.builder()
                .retryType("test-compensation")
                .retryKey("retain-key")
                .policy(RetryPolicy.builder()
                        .maxRetryTimes(1)
                        .retryIntervalMillis(60000L)
                        .maxIntervalMillis(60000L)
                        .backoffMultiplier(1D)
                        .jitterRatio(0D)
                        .build())
                .build());
        RetryDecision decision = engine.decide("test-compensation", "retain-key");
        assertEquals(RetryDecisionType.EXHAUSTED, decision.getType());
        assertNull(engine.getInfo("test-compensation", "retain-key"));
    }

    @Test
    void repeatedRecordFailureShouldKeepFirstFailTimeAndOverwriteLastErrorAndContext() {
        Map<String, Object> firstContext = new HashMap<String, Object>();
        firstContext.put("extraField", "first");
        RetryInfo first = engine.recordFailure(RetryFailure.builder()
                .retryType("test-compensation")
                .retryKey("context-update-key")
                .errorCode("FIRST_ERROR")
                .errorMessage("first message")
                .context(firstContext)
                .build());
        Map<String, Object> secondContext = new HashMap<String, Object>();
        secondContext.put("extraField", "second");
        RetryInfo second = engine.recordFailure(RetryFailure.builder()
                .retryType("test-compensation")
                .retryKey("context-update-key")
                .errorCode("SECOND_ERROR")
                .errorMessage("second message")
                .context(secondContext)
                .build());
        assertEquals(Integer.valueOf(2), second.getCount());
        assertEquals(first.getFirstFailTime(), second.getFirstFailTime());
        assertTrue(second.getLastFailTime() >= first.getLastFailTime());
        assertEquals("SECOND_ERROR", second.getLastErrorCode());
        assertEquals("second message", second.getLastErrorMessage());
        assertEquals("second", second.getContext().get("extraField"));
        RetryInfo stored = engine.getInfo("test-compensation", "context-update-key");
        assertEquals(second, stored);
    }

    private RetryScanResult scanUntilFinished(String routeKey, String retryType, int count, boolean includeInfo) {
        String cursor = "0";
        int pages = 0;
        java.util.List<String> keys = new java.util.ArrayList<String>();
        Map<String, RetryInfo> infos = new HashMap<String, RetryInfo>();
        RetryScanResult result;
        do {
            result = engine.scan(RetryScanRequest.builder()
                    .routeKey(routeKey)
                    .retryType(retryType)
                    .cursor(cursor)
                    .count(count)
                    .includeInfo(includeInfo)
                    .build());
            keys.addAll(result.getKeys());
            infos.putAll(result.getInfos());
            cursor = result.getNextCursor();
            pages++;
            assertTrue(pages <= 200, "SCAN 分页必须在合理次数内结束");
        } while (!result.isFinished());
        log.info("SCAN 聚合完成，retryType={}，pages={}，keys={}", retryType, pages, keys.size());
        return RetryScanResult.builder()
                .nextCursor("0")
                .finished(true)
                .keys(keys)
                .infos(infos)
                .build();
    }
}
