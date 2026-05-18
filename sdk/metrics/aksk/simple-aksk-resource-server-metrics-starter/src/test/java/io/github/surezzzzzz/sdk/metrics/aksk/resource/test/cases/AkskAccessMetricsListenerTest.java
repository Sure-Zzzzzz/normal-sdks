package io.github.surezzzzzz.sdk.metrics.aksk.resource.test.cases;

import io.github.surezzzzzz.sdk.auth.aksk.resource.core.event.AkskAccessEvent;
import io.github.surezzzzzz.sdk.metrics.aksk.resource.listener.AkskAccessMetricsListener;
import io.github.surezzzzzz.sdk.metrics.aksk.resource.test.SimpleAkskResourceMetricsTestApplication;
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
 * AKSK 认证指标监听器测试
 *
 * @author surezzzzzz
 */
@Slf4j
@SpringBootTest(classes = SimpleAkskResourceMetricsTestApplication.class)
public class AkskAccessMetricsListenerTest {

    @Autowired
    private ApplicationEventPublisher eventPublisher;

    @Autowired
    private MeterRegistry meterRegistry;

    @BeforeEach
    public void setUp() {
        meterRegistry.clear();
    }

    @Test
    public void testSuccessEventIncrementsCounter() {
        log.info("========== 测试：正常认证事件递增计数器 ==========");

        eventPublisher.publishEvent(buildEvent("platform", "header", null));

        Counter counter = meterRegistry.find("smart_aksk_access_total")
                .tag("result", "success")
                .tag("clientType", "platform")
                .tag("source", "header")
                .counter();
        assertNotNull(counter, "Success counter should be registered");
        assertEquals(1.0, counter.count(), 0.001);

        Counter failCounter = meterRegistry.find("smart_aksk_access_total")
                .tag("result", "fail")
                .counter();
        assertNull(failCounter, "Fail counter should not exist for success event");

        log.info("testSuccessEventIncrementsCounter passed, count={}", counter.count());
    }

    @Test
    public void testFailEventIncrementsCounter() {
        log.info("========== 测试：认证失败事件递增计数器 ==========");

        Map<String, String> context = new HashMap<>();
        context.put("error", "invalid signature");
        eventPublisher.publishEvent(buildEvent("user", "jwt", context));

        Counter counter = meterRegistry.find("smart_aksk_access_total")
                .tag("result", "fail")
                .tag("clientType", "user")
                .tag("source", "jwt")
                .counter();
        assertNotNull(counter, "Fail counter should be registered");
        assertEquals(1.0, counter.count(), 0.001);

        Counter successCounter = meterRegistry.find("smart_aksk_access_total")
                .tag("result", "success")
                .counter();
        assertNull(successCounter, "Success counter should not exist for fail event");

        log.info("testFailEventIncrementsCounter passed");
    }

    @Test
    public void testTimerRecordsDuration() {
        log.info("========== 测试：认证耗时 Timer 正确记录 ==========");

        Map<String, String> context = new HashMap<>();
        context.put("durationNanos", "500000");
        eventPublisher.publishEvent(buildEvent("platform", "header", context));

        Timer timer = meterRegistry.find("smart_aksk_authenticate_seconds")
                .tag("clientType", "platform")
                .tag("source", "header")
                .timer();
        assertNotNull(timer, "Timer should be registered");
        assertEquals(1, timer.count(), "Timer should have one record");
        assertEquals(500000L, (long) timer.totalTime(TimeUnit.NANOSECONDS),
                "Timer should record 500000 nanoseconds");

        log.info("testTimerRecordsDuration passed, count={}, totalNanos={}",
                timer.count(), (long) timer.totalTime(TimeUnit.NANOSECONDS));
    }

    @Test
    public void testTimerNotRecordedWhenNoDuration() {
        log.info("========== 测试：无 durationNanos 时不记录 Timer ==========");

        eventPublisher.publishEvent(buildEvent("platform", "header", null));

        Timer timer = meterRegistry.find("smart_aksk_authenticate_seconds").timer();
        assertNull(timer, "Timer should not exist when no durationNanos");

        log.info("testTimerNotRecordedWhenNoDuration passed");
    }

    @Test
    public void testMultipleEventsAggregated() {
        log.info("========== 测试：同标签事件合并计数 ==========");

        eventPublisher.publishEvent(buildEvent("platform", "header", null));
        eventPublisher.publishEvent(buildEvent("platform", "header", null));

        Counter counter = meterRegistry.find("smart_aksk_access_total")
                .tag("result", "success")
                .tag("clientType", "platform")
                .tag("source", "header")
                .counter();
        assertNotNull(counter, "Counter should be registered");
        assertEquals(2.0, counter.count(), 0.001, "Counter should be incremented twice");

        log.info("testMultipleEventsAggregated passed, count={}", counter.count());
    }

    @Test
    public void testDifferentTagsCreateSeparateCounters() {
        log.info("========== 测试：不同标签创建独立计数器 ==========");

        eventPublisher.publishEvent(buildEvent("platform", "header", null));
        eventPublisher.publishEvent(buildEvent("user", "jwt", null));

        Counter platformCounter = meterRegistry.find("smart_aksk_access_total")
                .tag("clientType", "platform")
                .tag("source", "header")
                .counter();
        Counter userCounter = meterRegistry.find("smart_aksk_access_total")
                .tag("clientType", "user")
                .tag("source", "jwt")
                .counter();

        assertNotNull(platformCounter, "Platform counter should exist");
        assertNotNull(userCounter, "User counter should exist");
        assertEquals(1.0, platformCounter.count(), 0.001);
        assertEquals(1.0, userCounter.count(), 0.001);

        log.info("testDifferentTagsCreateSeparateCounters passed");
    }

    // ==================== Helper ====================

    private AkskAccessEvent buildEvent(String clientType, String source, Map<String, String> context) {
        return new AkskAccessEvent(
                this,
                "client-1", clientType, "user-1", "testuser", "admin", "read write",
                "/api/test", "GET", "127.0.0.1", "TestAgent",
                source, "trace-123", context
        );
    }
}