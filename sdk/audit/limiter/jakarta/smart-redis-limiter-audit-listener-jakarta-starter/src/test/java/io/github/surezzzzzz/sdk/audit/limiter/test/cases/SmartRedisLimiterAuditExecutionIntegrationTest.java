package io.github.surezzzzzz.sdk.audit.limiter.test.cases;

import io.github.surezzzzzz.sdk.audit.limiter.handler.SmartRedisLimiterAuditHandler;
import io.github.surezzzzzz.sdk.limiter.redis.smart.annotation.SmartRedisLimitRule;
import io.github.surezzzzzz.sdk.limiter.redis.smart.annotation.SmartRedisLimiter;
import io.github.surezzzzzz.sdk.limiter.redis.smart.configuration.SmartRedisLimiterProperties;
import io.github.surezzzzzz.sdk.limiter.redis.smart.constant.SmartRedisLimiterConstant;
import io.github.surezzzzzz.sdk.limiter.redis.smart.constant.SmartRedisLimiterFallbackStrategy;
import io.github.surezzzzzz.sdk.limiter.redis.smart.constant.SmartRedisLimiterRedisKeyConstant;
import io.github.surezzzzzz.sdk.limiter.redis.smart.constant.SmartRedisLimiterTimeUnit;
import io.github.surezzzzzz.sdk.limiter.redis.smart.exception.SmartRedisLimitExceededException;
import io.github.surezzzzzz.sdk.limiter.redis.smart.model.SmartRedisLimiterRecord;
import io.github.surezzzzzz.sdk.redis.route.template.RedisRouteTemplate;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;

import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * SmartRedisLimiter 审计真实执行链路测试
 *
 * @author surezzzzzz
 */
@Slf4j
@SpringBootTest(
        classes = SmartRedisLimiterAuditExecutionIntegrationTest.IntegrationApplication.class,
        properties = "spring.config.import=classpath:application-integration.yml"
)
@Import(SmartRedisLimiterAuditExecutionIntegrationTest.IntegrationConfiguration.class)
public class SmartRedisLimiterAuditExecutionIntegrationTest {

    @Autowired
    private LimiterService limiterService;

    @Autowired
    private CapturingAuditHandler auditHandler;

    @Autowired
    private RedisRouteTemplate redisRouteTemplate;

    @Autowired
    private SmartRedisLimiterProperties limiterProperties;

    @Value("${io.github.surezzzzzz.sdk.redis.route.rules[0].datasource}")
    private String fallbackDatasource;

    @Value("${io.github.surezzzzzz.sdk.limiter.redis.smart.fallback.on-redis-error}")
    private String configuredRedisErrorFallback;

    @BeforeEach
    public void setUp() {
        auditHandler.reset();
        limiterProperties.setLogOnPass(false);
        limiterProperties.getFallback().setOnRedisError(configuredRedisErrorFallback);
        cleanLimiterKeys();
    }

    @AfterEach
    public void tearDown() {
        cleanLimiterKeys();
    }

    @Test
    public void testRealLimiterPublishesDeniedAndConfiguredPassEvents() throws InterruptedException {
        limiterService.normalLimited();
        log.info("真实通过请求已在 logOnPass=false 下执行，确认审计 Handler 未收到记录");
        assertFalse(auditHandler.latch.await(300, TimeUnit.MILLISECONDS),
                "logOnPass=false 时真实通过请求不得产生审计记录");
        log.info("logOnPass=false 时审计记录数：{}", auditHandler.records.size());
        assertEquals(0, auditHandler.records.size(), "真实通过请求不得遗留审计记录");

        limiterProperties.setLogOnPass(true);
        auditHandler.reset();
        limiterService.normalLimited();
        SmartRedisLimiterRecord passRecord = awaitRecord();
        log.info("真实通过审计快照：passed={}, source={}, datasource={}",
                passRecord.isPassed(), passRecord.getSource(), passRecord.getDatasourceKey());
        assertTrue(passRecord.isPassed());
        assertTrue(passRecord.isRouteRequired());
        assertTrue(passRecord.isRouteResolved());
        assertEquals(SmartRedisLimiterConstant.REDIS_MODE_STANDALONE, passRecord.getRedisMode());
        assertEquals(SmartRedisLimiterConstant.POLICY_SOURCE_LOCAL, passRecord.getPolicySource());

        limiterProperties.setLogOnPass(false);
        auditHandler.reset();
        log.info("调用超出窗口阈值的真实限流方法，预期抛出拒绝异常");
        assertThrows(SmartRedisLimitExceededException.class, () -> limiterService.normalLimited());
        SmartRedisLimiterRecord deniedRecord = awaitRecord();
        log.info("真实拒绝审计快照：passed={}, remaining={}",
                deniedRecord.isPassed(), deniedRecord.getRemaining());
        assertFalse(deniedRecord.isPassed());
        assertEquals(Long.valueOf(0L), deniedRecord.getRemaining());
        assertTrue(deniedRecord.isRouteRequired());
        assertTrue(deniedRecord.isRouteResolved());
    }

    @Test
    public void testRealRouteFallbackPublishesAllowAndDenyAuditEvents() throws InterruptedException {
        limiterService.fallbackAllowed();

        SmartRedisLimiterRecord allowedRecord = awaitRecord();
        log.info("真实 Route allow 降级审计快照：passed={}, fallbackReason={}, routeResolved={}",
                allowedRecord.isPassed(), allowedRecord.getFallbackReason(), allowedRecord.isRouteResolved());
        assertTrue(allowedRecord.isPassed());
        assertRouteFallbackRecord(allowedRecord);

        auditHandler.reset();
        limiterProperties.getFallback().setOnRedisError(SmartRedisLimiterFallbackStrategy.ALLOW_CODE);
        log.info("全局降级策略已设为 allow，调用显式 deny 的不可用 Route 限流方法，预期仍抛出拒绝异常");
        assertThrows(SmartRedisLimitExceededException.class, () -> limiterService.fallbackDenied());
        SmartRedisLimiterRecord deniedRecord = awaitRecord();
        log.info("真实 Route deny 降级审计快照：passed={}, fallbackReason={}, routeResolved={}",
                deniedRecord.isPassed(), deniedRecord.getFallbackReason(), deniedRecord.isRouteResolved());
        assertFalse(deniedRecord.isPassed());
        assertRouteFallbackRecord(deniedRecord);
    }

    private void assertRouteFallbackRecord(SmartRedisLimiterRecord record) {
        log.info("校验 Route 降级审计字段：datasource={}, redisMode={}, policySource={}",
                record.getDatasourceKey(), record.getRedisMode(), record.getPolicySource());
        assertTrue(record.isRouteRequired());
        assertTrue(record.isRouteResolved(), "Route 已解析到不可用数据源时仍应保留解析快照");
        assertEquals(fallbackDatasource, record.getDatasourceKey(), "审计记录必须使用 YAML 配置的 Route 数据源");
        assertEquals(SmartRedisLimiterConstant.REDIS_MODE_UNKNOWN, record.getRedisMode());
        assertTrue(
                SmartRedisLimiterConstant.FALLBACK_REASON_REDIS_ERROR.equals(record.getFallbackReason())
                        || SmartRedisLimiterConstant.FALLBACK_REASON_TIMEOUT.equals(record.getFallbackReason()),
                "不可用 Route 数据源必须归类为 redis_error 或 timeout");
        assertEquals(SmartRedisLimiterConstant.POLICY_SOURCE_LOCAL, record.getPolicySource());
    }

    private SmartRedisLimiterRecord awaitRecord() throws InterruptedException {
        log.info("等待真实限流事件异步投递至审计 Handler");
        assertTrue(auditHandler.latch.await(5, TimeUnit.SECONDS), "真实限流事件应异步投递至审计 Handler");
        log.info("真实限流事件收到审计记录数：{}", auditHandler.records.size());
        assertEquals(1, auditHandler.records.size(), "每个真实限流事件应只投递一条审计记录");
        return auditHandler.records.get(0);
    }

    private void cleanLimiterKeys() {
        Set<String> keys = redisRouteTemplate.stringTemplate().keys(
                SmartRedisLimiterRedisKeyConstant.KEY_PREFIX + "audit-integration:*");
        if (keys != null && !keys.isEmpty()) {
            redisRouteTemplate.stringTemplate().delete(keys);
            log.info("清理真实限流测试 Redis key 数量：{}", keys.size());
        }
    }

    @SpringBootConfiguration
    @EnableAutoConfiguration(exclude = {
            DataSourceAutoConfiguration.class,
            SecurityAutoConfiguration.class
    })
    public static class IntegrationApplication {
    }

    @TestConfiguration
    public static class IntegrationConfiguration {

        @Bean
        public LimiterService limiterService() {
            return new LimiterService();
        }

        @Bean
        public CapturingAuditHandler capturingAuditHandler() {
            return new CapturingAuditHandler();
        }
    }

    public static class LimiterService {

        @SmartRedisLimiter(rules = {
                @SmartRedisLimitRule(count = 2, window = 1, unit = SmartRedisLimiterTimeUnit.MINUTES)
        })
        public void normalLimited() {
        }

        @SmartRedisLimiter(
                fallback = "allow",
                rules = {
                        @SmartRedisLimitRule(count = 2, window = 1, unit = SmartRedisLimiterTimeUnit.MINUTES)
                }
        )
        public void fallbackAllowed() {
        }

        @SmartRedisLimiter(
                fallback = "deny",
                rules = {
                        @SmartRedisLimitRule(count = 2, window = 1, unit = SmartRedisLimiterTimeUnit.MINUTES)
                }
        )
        public void fallbackDenied() {
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
