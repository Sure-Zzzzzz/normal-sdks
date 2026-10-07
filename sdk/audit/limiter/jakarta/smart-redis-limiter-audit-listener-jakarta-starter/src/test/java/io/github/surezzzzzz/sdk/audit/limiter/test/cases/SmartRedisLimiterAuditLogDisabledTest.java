package io.github.surezzzzzz.sdk.audit.limiter.test.cases;

import io.github.surezzzzzz.sdk.audit.limiter.handler.SmartRedisLimiterAuditHandler;
import io.github.surezzzzzz.sdk.audit.limiter.test.SmartRedisLimiterAuditListenerTestApplication;
import io.github.surezzzzzz.sdk.audit.limiter.test.support.TestSmartRedisLimiterAuditHandler;
import io.github.surezzzzzz.sdk.limiter.redis.smart.constant.SmartRedisLimiterConstant;
import io.github.surezzzzzz.sdk.limiter.redis.smart.event.SmartRedisLimiterEvent;
import io.github.surezzzzzz.sdk.limiter.redis.smart.model.SmartRedisLimiterEventPayload;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.context.TestPropertySource;

import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 测试 log.enabled=false 时 LogHandler 不注册
 *
 * @author surezzzzzz
 */
@Slf4j
@SpringBootTest(classes = SmartRedisLimiterAuditListenerTestApplication.class)
@TestPropertySource(properties = {
        "io.github.surezzzzzz.sdk.audit.limiter.listener.handler.log.enabled=false"
})
public class SmartRedisLimiterAuditLogDisabledTest {

    @Autowired
    private ApplicationEventPublisher eventPublisher;

    @Autowired
    private List<SmartRedisLimiterAuditHandler> handlers;

    @Autowired
    private TestSmartRedisLimiterAuditHandler testHandler;

    @BeforeEach
    public void setUp() {
        testHandler.reset();
    }

    @Test
    public void testLogHandlerNotRegisteredWhenDisabled() {
        log.info("默认日志关闭后的 Handler 列表是否存在：{}", handlers != null);
        assertNotNull(handlers, "Handler 列表不应为空");
        boolean hasLogHandler = handlers.stream()
                .anyMatch(handler -> handler.getClass().getSimpleName().contains("LogSmartRedisLimiterAuditHandler"));
        log.info("默认日志关闭后的 Handler 数量：{}", handlers.size());
        assertFalse(hasLogHandler, "关闭配置后不得注册默认日志 Handler");
    }

    @Test
    public void testCustomHandlerStillWorksWhenLogDisabled() throws InterruptedException {
        SmartRedisLimiterEventPayload payload = SmartRedisLimiterEventPayload.builder()
                .limitKey("smart-limiter:test-service:path")
                .routeKey("smart-limiter:test-service:path")
                .redisMode(SmartRedisLimiterConstant.REDIS_MODE_STANDALONE)
                .routeRequired(true)
                .routeResolved(true)
                .keyStrategy("path")
                .algorithm("fixed")
                .limitRules("5/10s")
                .passed(false)
                .sourceType("INTERCEPTOR")
                .limit(5L)
                .remaining(0L)
                .resetAt(1715635500L)
                .durationNanos(100L)
                .policySource(SmartRedisLimiterConstant.POLICY_SOURCE_LOCAL)
                .build();

        eventPublisher.publishEvent(new SmartRedisLimiterEvent(this, payload));

        log.info("默认日志关闭时已发布审计事件，等待自定义 Handler 投递");
        assertTrue(testHandler.latch.await(5, TimeUnit.SECONDS), "关闭默认日志不得影响自定义 Handler 投递");
        log.info("默认日志关闭时自定义 Handler 收到记录数：{}", testHandler.records.size());
        assertEquals(1, testHandler.records.size());
        assertFalse(testHandler.records.get(0).isPassed());
    }
}
