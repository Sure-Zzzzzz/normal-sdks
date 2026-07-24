package io.github.surezzzzzz.sdk.audit.limiter.test.cases;

import io.github.surezzzzzz.sdk.audit.limiter.handler.SmartRedisLimiterAuditHandler;
import io.github.surezzzzzz.sdk.limiter.redis.smart.audit.SmartRedisLimiterTraceIdProvider;
import io.github.surezzzzzz.sdk.limiter.redis.smart.audit.SmartRedisLimiterUserProvider;
import io.github.surezzzzzz.sdk.limiter.redis.smart.constant.SmartRedisLimiterConstant;
import io.github.surezzzzzz.sdk.limiter.redis.smart.event.SmartRedisLimiterEvent;
import io.github.surezzzzzz.sdk.limiter.redis.smart.model.SmartRedisLimiterEventPayload;
import io.github.surezzzzzz.sdk.limiter.redis.smart.model.SmartRedisLimiterRecord;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;

import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * SmartRedisLimiter 审计上下文快照测试
 *
 * @author surezzzzzz
 */
@Slf4j
@SpringBootTest(classes = SmartRedisLimiterAuditContextSnapshotTest.TestApplication.class)
@Import(SmartRedisLimiterAuditContextSnapshotTest.ContextConfiguration.class)
public class SmartRedisLimiterAuditContextSnapshotTest {

    private static final ThreadLocal<String> CLIENT_ID = new ThreadLocal<>();
    private static final ThreadLocal<String> TRACE_ID = new ThreadLocal<>();

    @Autowired
    private ApplicationEventPublisher eventPublisher;

    @Autowired
    private CapturingAuditHandler capturingAuditHandler;

    @BeforeEach
    public void setUp() {
        capturingAuditHandler.reset();
    }

    @AfterEach
    public void tearDown() {
        CLIENT_ID.remove();
        TRACE_ID.remove();
    }

    @Test
    public void testSnapshotsThreadLocalValuesBeforeAsyncDispatch() throws InterruptedException {
        CLIENT_ID.set("publisher-client");
        TRACE_ID.set("publisher-trace");

        eventPublisher.publishEvent(new SmartRedisLimiterEvent(this, payload()));
        CLIENT_ID.remove();
        TRACE_ID.remove();

        log.info("已清理发布线程 ThreadLocal，等待异步 Handler 使用快照记录");
        assertTrue(capturingAuditHandler.latch.await(5, TimeUnit.SECONDS), "异步 Handler 应收到审计记录");
        log.info("异步 Handler 收到记录数：{}", capturingAuditHandler.records.size());
        assertEquals(1, capturingAuditHandler.records.size(), "每个审计事件应只投递一条快照记录");
        SmartRedisLimiterRecord record = capturingAuditHandler.records.get(0);
        log.info("发布线程快照：clientId={}, traceId={}", record.getClientId(), record.getTraceId());
        assertEquals("publisher-client", record.getClientId());
        assertEquals("publisher-trace", record.getTraceId());
    }

    @Test
    public void testThrowingHandlerDoesNotBlockFollowingHandler() throws InterruptedException {
        eventPublisher.publishEvent(new SmartRedisLimiterEvent(this, payload()));

        log.info("已发布包含抛异常 Handler 的审计事件，等待后续 Handler 投递");
        assertTrue(capturingAuditHandler.latch.await(5, TimeUnit.SECONDS), "抛异常的 Handler 不得阻断后续 Handler");
        log.info("Handler 隔离后收到记录数：{}", capturingAuditHandler.records.size());
        assertEquals(1, capturingAuditHandler.records.size());
    }

    private SmartRedisLimiterEventPayload payload() {
        return SmartRedisLimiterEventPayload.builder()
                .limitKey("smart-limiter:test-service:context")
                .routeKey("smart-limiter:test-service:context")
                .redisMode(SmartRedisLimiterConstant.REDIS_MODE_STANDALONE)
                .routeRequired(true)
                .routeResolved(true)
                .keyStrategy("path")
                .algorithm("fixed")
                .limitRules("1/1s")
                .passed(false)
                .sourceType("INTERCEPTOR")
                .limit(1L)
                .remaining(0L)
                .resetAt(1715635200L)
                .durationNanos(100L)
                .policySource(SmartRedisLimiterConstant.POLICY_SOURCE_LOCAL)
                .build();
    }

    @SpringBootApplication
    public static class TestApplication {
    }

    @TestConfiguration
    public static class ContextConfiguration {

        @Bean
        public SmartRedisLimiterUserProvider threadLocalUserProvider() {
            return new SmartRedisLimiterUserProvider() {
                @Override
                public String getClientId() {
                    return CLIENT_ID.get();
                }

                @Override
                public String getClientType() {
                    return null;
                }

                @Override
                public String getUserId() {
                    return null;
                }

                @Override
                public String getUsername() {
                    return null;
                }
            };
        }

        @Bean
        public SmartRedisLimiterTraceIdProvider threadLocalTraceIdProvider() {
            return TRACE_ID::get;
        }

        @Bean
        public SmartRedisLimiterAuditHandler throwingAuditHandler() {
            return record -> {
                throw new IllegalStateException("test handler failure");
            };
        }

        @Bean
        public CapturingAuditHandler capturingAuditHandler() {
            return new CapturingAuditHandler();
        }
    }

    public static class CapturingAuditHandler implements SmartRedisLimiterAuditHandler {

        private final CopyOnWriteArrayList<SmartRedisLimiterRecord> records = new CopyOnWriteArrayList<>();
        private CountDownLatch latch = new CountDownLatch(1);

        @Override
        public void handle(SmartRedisLimiterRecord record) {
            records.add(record);
            latch.countDown();
        }

        private void reset() {
            records.clear();
            latch = new CountDownLatch(1);
        }
    }
}
