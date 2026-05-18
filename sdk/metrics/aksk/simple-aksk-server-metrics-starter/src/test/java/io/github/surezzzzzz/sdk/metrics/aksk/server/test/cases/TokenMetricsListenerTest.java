package io.github.surezzzzzz.sdk.metrics.aksk.server.test.cases;

import io.github.surezzzzzz.sdk.auth.aksk.server.event.AbstractTokenEvent;
import io.github.surezzzzzz.sdk.auth.aksk.server.event.TokenEventType;
import io.github.surezzzzzz.sdk.metrics.aksk.server.test.SimpleAkskServerMetricsTestApplication;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationEventPublisher;

import java.time.Instant;
import java.util.Arrays;
import java.util.HashSet;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Token 指标监听器测试
 *
 * @author surezzzzzz
 */
@Slf4j
@SpringBootTest(classes = SimpleAkskServerMetricsTestApplication.class)
public class TokenMetricsListenerTest {

    @Autowired
    private ApplicationEventPublisher eventPublisher;

    @Autowired
    private MeterRegistry meterRegistry;

    @BeforeEach
    public void setUp() {
        meterRegistry.clear();
    }

    @Test
    public void testIssuedEventIncrementsCounter() {
        log.info("========== 测试：Token 签发事件递增计数器 ==========");

        eventPublisher.publishEvent(buildEvent(TokenEventType.ISSUED, "platform"));

        Counter counter = meterRegistry.find("smart_aksk_token_total")
                .tag("eventType", "issued")
                .tag("clientType", "platform")
                .counter();
        assertNotNull(counter, "Counter with eventType=issued should be registered");
        assertEquals(1.0, counter.count(), 0.001);

        log.info("testIssuedEventIncrementsCounter passed, count={}", counter.count());
    }

    @Test
    public void testRevokedEventIncrementsCounter() {
        log.info("========== 测试：Token 撤销事件递增计数器 ==========");

        eventPublisher.publishEvent(buildEvent(TokenEventType.REVOKED, "user"));

        Counter counter = meterRegistry.find("smart_aksk_token_total")
                .tag("eventType", "revoked")
                .tag("clientType", "user")
                .counter();
        assertNotNull(counter, "Counter with eventType=revoked should be registered");
        assertEquals(1.0, counter.count(), 0.001);

        log.info("testRevokedEventIncrementsCounter passed");
    }

    @Test
    public void testIntrospectedEventIncrementsCounter() {
        log.info("========== 测试：Token 校验事件递增计数器 ==========");

        eventPublisher.publishEvent(buildEvent(TokenEventType.INTROSPECTED, "platform"));

        Counter counter = meterRegistry.find("smart_aksk_token_total")
                .tag("eventType", "introspected")
                .tag("clientType", "platform")
                .counter();
        assertNotNull(counter, "Counter with eventType=introspected should be registered");
        assertEquals(1.0, counter.count(), 0.001);

        log.info("testIntrospectedEventIncrementsCounter passed");
    }

    @Test
    public void testRemovedEventIncrementsCounter() {
        log.info("========== 测试：Token 移除事件递增计数器 ==========");

        eventPublisher.publishEvent(buildEvent(TokenEventType.REMOVED, "user"));

        Counter counter = meterRegistry.find("smart_aksk_token_total")
                .tag("eventType", "removed")
                .tag("clientType", "user")
                .counter();
        assertNotNull(counter, "Counter with eventType=removed should be registered");
        assertEquals(1.0, counter.count(), 0.001);

        log.info("testRemovedEventIncrementsCounter passed");
    }

    @Test
    public void testAllEventTypesCreateSeparateCounters() {
        log.info("========== 测试：四种事件类型各自独立计数 ==========");

        eventPublisher.publishEvent(buildEvent(TokenEventType.ISSUED, "platform"));
        eventPublisher.publishEvent(buildEvent(TokenEventType.REVOKED, "platform"));
        eventPublisher.publishEvent(buildEvent(TokenEventType.INTROSPECTED, "platform"));
        eventPublisher.publishEvent(buildEvent(TokenEventType.REMOVED, "platform"));

        for (String eventType : new String[]{"issued", "revoked", "introspected", "removed"}) {
            Counter counter = meterRegistry.find("smart_aksk_token_total")
                    .tag("eventType", eventType)
                    .tag("clientType", "platform")
                    .counter();
            assertNotNull(counter, "Counter for eventType=" + eventType + " should exist");
            assertEquals(1.0, counter.count(), 0.001, "Count for " + eventType + " should be 1");
        }

        log.info("testAllEventTypesCreateSeparateCounters passed");
    }

    // ==================== Helper ====================

    private AbstractTokenEvent buildEvent(TokenEventType eventType, String clientType) {
        return new AbstractTokenEvent(
                this,
                eventType,
                "client-test", clientType, "user-1", "testuser",
                "token-value-123", new HashSet<>(Arrays.asList("read", "write")),
                Instant.now(), Instant.now().plusSeconds(3600)
        ) {};
    }
}
