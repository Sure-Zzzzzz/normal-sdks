package io.github.surezzzzzz.sdk.retry.redis.smart.test.cases;

import io.github.surezzzzzz.sdk.redis.route.template.RedisRouteTemplate;
import io.github.surezzzzzz.sdk.retry.redis.smart.engine.SmartRedisRetryEngine;
import io.github.surezzzzzz.sdk.retry.redis.smart.model.RetryFailure;
import io.github.surezzzzzz.sdk.retry.redis.smart.model.RetryInfo;
import io.github.surezzzzzz.sdk.retry.redis.smart.model.RetryPolicy;
import io.github.surezzzzzz.sdk.retry.redis.smart.script.RedisRetryScriptExecutor;
import io.github.surezzzzzz.sdk.retry.redis.smart.support.RetryKeyHelper;
import io.github.surezzzzzz.sdk.retry.redis.smart.test.SmartRedisRetryTestApplication;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import lombok.extern.slf4j.Slf4j;

/**
 * Lua 退避算法与 expectedCount 竞态保护验证（依赖本地 Redis 16379）。
 *
 * @author surezzzzzz
 */
@SpringBootTest(classes = SmartRedisRetryTestApplication.class)
@Slf4j
class SmartRedisRetryBackoffAlgorithmTest {

    @Autowired
    private SmartRedisRetryEngine engine;

    @Autowired
    private RedisRouteTemplate redisRouteTemplate;

    @Autowired
    private RetryKeyHelper retryKeyHelper;

    @Autowired
    private RedisRetryScriptExecutor scriptExecutor;

    @AfterEach
    void cleanUp() {
        engine.clear("test-compensation", "backoff-no-jitter");
        engine.clear("test-compensation", "backoff-jitter-range");
        engine.clear("test-compensation", "expected-count-racy");
    }

    @Test
    void backoffShouldGrowExponentiallyAndCapAtMaxIntervalWithoutJitter() {
        // interval=100, multiplier=2, jitter=0, maxInterval=1000
        // 预期间隔：100 -> 200 -> 400 -> 800 -> 1000(封顶) -> 1000(封顶)
        RetryPolicy policy = noJitterPolicy(10, 100L, 1000L, 2D);
        String key = "backoff-no-jitter";

        RetryInfo r1 = engine.recordFailure(failure(key, policy));
        assertEquals(Integer.valueOf(1), r1.getCount());
        assertEquals(100L, intervalOf(r1), "第 1 次间隔应为 100");

        RetryInfo r2 = engine.recordFailure(failure(key, policy));
        assertEquals(200L, intervalOf(r2), "第 2 次间隔应为 200");

        RetryInfo r3 = engine.recordFailure(failure(key, policy));
        assertEquals(400L, intervalOf(r3), "第 3 次间隔应为 400");

        RetryInfo r4 = engine.recordFailure(failure(key, policy));
        assertEquals(800L, intervalOf(r4), "第 4 次间隔应为 800");

        RetryInfo r5 = engine.recordFailure(failure(key, policy));
        assertEquals(1000L, intervalOf(r5), "第 5 次间隔应达到 maxInterval 上限 1000");

        RetryInfo r6 = engine.recordFailure(failure(key, policy));
        assertEquals(1000L, intervalOf(r6), "第 6 次间隔应保持 maxInterval 上限不再增长");
        log.info("Lua 指数退避 + 封顶验证通过");
    }

    @Test
    void jitterShouldKeepIntervalWithinRange() {
        // interval=1000, multiplier=1, jitter=0.5, maxInterval=10000
        // cappedInterval=1000, jitterMillis=500, currentInterval ∈ [500, 1500]
        RetryPolicy policy = RetryPolicy.builder()
                .maxRetryTimes(3)
                .retryIntervalMillis(1000L)
                .maxIntervalMillis(10000L)
                .backoffMultiplier(1D)
                .jitterRatio(0.5D)
                .build();
        String key = "backoff-jitter-range";

        for (int i = 0; i < 8; i++) {
            RetryInfo info = engine.recordFailure(failure(key, policy));
            long interval = intervalOf(info);
            assertTrue(interval >= 500L && interval <= 1500L,
                    "jitter 后间隔应落在 [500, 1500]，实际=" + interval + "，count=" + info.getCount());
        }
        log.info("Lua jitter 范围验证通过");
    }

    @Test
    void clearWithStaleExpectedCountShouldNotDeleteChangedRecord() {
        // 引入 expectedCount 的核心动机：旧决策不得删除并发新 record 的记录
        RetryPolicy policy = noJitterPolicy(10, 100L, 1000L, 2D);
        String key = "expected-count-racy";

        engine.recordFailure(failure(key, policy));
        engine.recordFailure(failure(key, policy));
        engine.recordFailure(failure(key, policy));

        String redisKey = retryKeyHelper.buildRedisKey("test-compensation", key);

        // 期望 count=2（旧决策快照），但实际 count=3，clear 不得删除
        RetryInfo stale = redisRouteTemplate.execute(redisKey,
                template -> scriptExecutor.clear(template, redisKey, 2));
        assertNull(stale, "expectedCount 不匹配时 clear 不应返回记录");
        RetryInfo stillThere = engine.getInfo("test-compensation", key);
        assertNotNull(stillThere, "count 已变化时 expectedCount=2 的 clear 不得误删新记录");
        assertEquals(Integer.valueOf(3), stillThere.getCount());

        // 期望 count=3（当前），clear 应删除并返回
        RetryInfo matched = redisRouteTemplate.execute(redisKey,
                template -> scriptExecutor.clear(template, redisKey, 3));
        assertNotNull(matched, "expectedCount 匹配时 clear 应返回删除前记录");
        assertEquals(Integer.valueOf(3), matched.getCount());
        assertNull(engine.getInfo("test-compensation", key), "expectedCount 匹配后记录应被删除");
        log.info("expectedCount 竞态保护验证通过");
    }

    private RetryPolicy noJitterPolicy(int maxRetryTimes, long interval, long maxInterval, double multiplier) {
        return RetryPolicy.builder()
                .maxRetryTimes(maxRetryTimes)
                .retryIntervalMillis(interval)
                .maxIntervalMillis(maxInterval)
                .backoffMultiplier(multiplier)
                .jitterRatio(0D)
                .build();
    }

    private RetryFailure failure(String retryKey, RetryPolicy policy) {
        return RetryFailure.builder()
                .retryType("test-compensation")
                .retryKey(retryKey)
                .policy(policy)
                .build();
    }

    private long intervalOf(RetryInfo info) {
        return info.getNextRetryTime() - info.getLastFailTime();
    }
}
