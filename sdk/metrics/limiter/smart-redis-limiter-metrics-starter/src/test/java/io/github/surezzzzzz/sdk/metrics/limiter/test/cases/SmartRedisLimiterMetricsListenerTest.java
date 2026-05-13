package io.github.surezzzzzz.sdk.metrics.limiter.test.cases;

import io.github.surezzzzzz.sdk.limiter.redis.smart.event.SmartRedisLimiterEvent;
import io.github.surezzzzzz.sdk.metrics.limiter.test.SmartRedisLimiterMetricsTestApplication;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationEventPublisher;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * SmartRedisLimiter 指标监听器测试
 *
 * @author surezzzzzz
 */
@Slf4j
@SpringBootTest(classes = SmartRedisLimiterMetricsTestApplication.class)
public class SmartRedisLimiterMetricsListenerTest {

    @Autowired
    private ApplicationEventPublisher eventPublisher;

    @Autowired
    private MeterRegistry meterRegistry;

    @BeforeEach
    public void setUp() {
        meterRegistry.clear();
    }

    @Test
    public void testPassedEventIncrementsCounter() {
        log.info("========== 测试：通过事件递增计数器 ==========");

        SmartRedisLimiterEvent event = buildInterceptorEvent(true, "/api/user/**");
        eventPublisher.publishEvent(event);

        Counter counter = meterRegistry.find("smart_rate_limit_total")
                .tag("result", "passed")
                .tag("algorithm", "sliding")
                .tag("source", "interceptor")
                .tag("rule", "/api/user/**")
                .counter();
        assertNotNull(counter, "Counter with correct tags should be registered");
        assertEquals(1.0, counter.count(), 0.001, "Counter should be incremented once");

        // 确认不存在 rejected 的 counter
        Counter rejectedCounter = meterRegistry.find("smart_rate_limit_total")
                .tag("result", "rejected")
                .counter();
        assertNull(rejectedCounter, "Rejected counter should not exist for passed event");

        log.info("testPassedEventIncrementsCounter passed, count={}", counter.count());
    }

    @Test
    public void testRejectedEventHasCorrectTags() {
        log.info("========== 测试：拒绝事件标签正确 ==========");

        SmartRedisLimiterEvent event = buildInterceptorEvent(false, "/api/user/**");
        eventPublisher.publishEvent(event);

        Counter counter = meterRegistry.find("smart_rate_limit_total")
                .tag("result", "rejected")
                .tag("algorithm", "sliding")
                .tag("source", "interceptor")
                .tag("rule", "/api/user/**")
                .counter();
        assertNotNull(counter, "Counter with correct tags should exist");
        assertEquals(1.0, counter.count(), 0.001);

        // 确认不存在 passed 的 counter
        Counter passedCounter = meterRegistry.find("smart_rate_limit_total")
                .tag("result", "passed")
                .counter();
        assertNull(passedCounter, "Passed counter should not exist for rejected event");

        log.info("testRejectedEventHasCorrectTags passed");
    }

    @Test
    public void testSourceNormalization() {
        log.info("========== 测试：source 标签标准化 ==========");

        // ASPECT → annotation
        SmartRedisLimiterEvent aspectEvent = buildAspectEvent(true);
        eventPublisher.publishEvent(aspectEvent);

        Counter aspectCounter = meterRegistry.find("smart_rate_limit_total")
                .tag("source", "annotation")
                .counter();
        assertNotNull(aspectCounter, "ASPECT should be normalized to annotation");
        assertEquals(1.0, aspectCounter.count(), 0.001);

        // 确认不存在 source=ASPECT 的 counter
        Counter rawAspectCounter = meterRegistry.find("smart_rate_limit_total")
                .tag("source", "ASPECT")
                .counter();
        assertNull(rawAspectCounter, "Raw ASPECT tag should not exist");

        // INTERCEPTOR → interceptor
        meterRegistry.clear();
        SmartRedisLimiterEvent interceptorEvent = buildInterceptorEvent(true, "/api/query/**");
        eventPublisher.publishEvent(interceptorEvent);

        Counter interceptorCounter = meterRegistry.find("smart_rate_limit_total")
                .tag("source", "interceptor")
                .counter();
        assertNotNull(interceptorCounter, "INTERCEPTOR should be normalized to interceptor");
        assertEquals(1.0, interceptorCounter.count(), 0.001);

        log.info("testSourceNormalization passed");
    }

    @Test
    public void testRuleTagFromMethodQualifiedName() {
        log.info("========== 测试：注解模式 rule 标签来自 methodQualifiedName ==========");

        SmartRedisLimiterEvent event = buildAspectEvent(false);
        eventPublisher.publishEvent(event);

        Counter counter = meterRegistry.find("smart_rate_limit_total")
                .tag("rule", "com.example.UserService.getUser")
                .counter();
        assertNotNull(counter, "Rule tag should use methodQualifiedName for annotation mode");
        assertEquals(1.0, counter.count(), 0.001);

        log.info("testRuleTagFromMethodQualifiedName passed");
    }

    @Test
    public void testRuleTagDefaultWhenNoMatch() {
        log.info("========== 测试：无匹配时 rule 标签为 default ==========");

        SmartRedisLimiterEvent event = new SmartRedisLimiterEvent(
                this, "key", "path", "fixed", "5/10s", true,
                "INTERCEPTOR",
                "/api/unknown", "GET", "127.0.0.1", null,
                null, null,
                null,
                5, 4, 1715635500L, 100L
        );

        eventPublisher.publishEvent(event);

        Counter counter = meterRegistry.find("smart_rate_limit_total")
                .tag("rule", "default")
                .counter();
        assertNotNull(counter, "Rule tag should be 'default' when no pathPattern and no methodName");
        assertEquals(1.0, counter.count(), 0.001);

        log.info("testRuleTagDefaultWhenNoMatch passed");
    }

    @Test
    public void testFallbackCounterIncremented() {
        log.info("========== 测试：降级计数器递增 ==========");

        Map<String, Object> attributes = new HashMap<>();
        attributes.put("fallback", true);
        attributes.put("fallbackStrategy", "allow");

        SmartRedisLimiterEvent event = new SmartRedisLimiterEvent(
                this, "key", "path", "fixed", "5/10s", true,
                "INTERCEPTOR",
                "/api/query", "GET", "127.0.0.1", "/api/query",
                null, null,
                attributes,
                5, 4, 1715635600L, 1_000_000L
        );

        eventPublisher.publishEvent(event);

        Counter fallbackCounter = meterRegistry.find("smart_rate_limit_fallback_total")
                .tag("strategy", "allow")
                .tag("algorithm", "fixed")
                .tag("source", "interceptor")
                .counter();
        assertNotNull(fallbackCounter, "Fallback counter should be registered with correct tags");
        assertEquals(1.0, fallbackCounter.count(), 0.001);

        log.info("testFallbackCounterIncremented passed");
    }

    @Test
    public void testNoFallbackCounterWhenNoFallback() {
        log.info("========== 测试：无降级时不记录降级计数 ==========");

        SmartRedisLimiterEvent event = buildInterceptorEvent(true, "/api/user/**");
        eventPublisher.publishEvent(event);

        Counter fallbackCounter = meterRegistry.find("smart_rate_limit_fallback_total").counter();
        assertNull(fallbackCounter, "Fallback counter should not exist when no fallback");

        log.info("testNoFallbackCounterWhenNoFallback passed");
    }

    @Test
    public void testCommandTimerRecorded() {
        log.info("========== 测试：命令耗时 Timer 记录 ==========");

        SmartRedisLimiterEvent event = buildInterceptorEvent(true, "/api/user/**");
        eventPublisher.publishEvent(event);

        Timer timer = meterRegistry.find("smart_rate_limit_command_seconds")
                .tag("algorithm", "sliding")
                .tag("source", "interceptor")
                .timer();
        assertNotNull(timer, "Timer should be registered");
        assertEquals(1, timer.count(), "Timer should have one record");
        assertEquals(500_000L, (long) timer.totalTime(TimeUnit.NANOSECONDS),
                "Timer should record 500_000 nanoseconds");

        log.info("testCommandTimerRecorded passed, count={}, totalNanos={}",
                timer.count(), (long) timer.totalTime(TimeUnit.NANOSECONDS));
    }

    // ==================== Helper ====================

    private SmartRedisLimiterEvent buildInterceptorEvent(boolean passed, String matchedPathPattern) {
        return new SmartRedisLimiterEvent(
                this, "smart-limiter:my-service:path:/api/user/123:10s",
                "path", "sliding", "10/1s", passed,
                "INTERCEPTOR",
                "/api/user/123", "GET", "192.168.1.1", matchedPathPattern,
                null, null,
                null,
                10, passed ? 5 : 0, 1715635200L, 500_000L
        );
    }

    private SmartRedisLimiterEvent buildAspectEvent(boolean passed) {
        return new SmartRedisLimiterEvent(
                this, "smart-limiter:my-service:method:UserService.getUser:60s",
                "method", "fixed", "100/1m", passed,
                "ASPECT",
                null, null, null, null,
                "getUser", "com.example.UserService.getUser",
                null,
                100, passed ? 95 : 0, 1715635260L, 200_000L
        );
    }
}
