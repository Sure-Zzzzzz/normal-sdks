package io.github.surezzzzzz.sdk.limiter.redis.smart.test.cases;

import io.github.surezzzzzz.sdk.limiter.redis.smart.constant.ErrorCode;
import io.github.surezzzzzz.sdk.limiter.redis.smart.constant.ErrorMessage;
import io.github.surezzzzzz.sdk.limiter.redis.smart.constant.SmartRedisLimiterConstant;
import io.github.surezzzzzz.sdk.limiter.redis.smart.constant.SmartRedisLimiterContextAttribute;
import io.github.surezzzzzz.sdk.limiter.redis.smart.exception.SmartRedisLimitExceededException;
import io.github.surezzzzzz.sdk.limiter.redis.smart.model.SmartRedisLimiterRecord;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * SmartRedisLimiter core 契约测试
 *
 * @author surezzzzzz
 */
@Slf4j
public class SmartRedisLimiterCoreContractTest {

    @Test
    public void testRouteContextAttributes() {
        log.info("开始测试 route 上下文属性");
        assertEquals(SmartRedisLimiterContextAttribute.ROUTE_KEY,
                SmartRedisLimiterContextAttribute.fromCode("routeKey"));
        assertEquals(SmartRedisLimiterContextAttribute.DATASOURCE_KEY,
                SmartRedisLimiterContextAttribute.fromCode("datasourceKey"));
        assertEquals(SmartRedisLimiterContextAttribute.REDIS_MODE,
                SmartRedisLimiterContextAttribute.fromCode("redisMode"));
        assertEquals(SmartRedisLimiterContextAttribute.ROUTE_REQUIRED,
                SmartRedisLimiterContextAttribute.fromCode("routeRequired"));
        assertEquals(SmartRedisLimiterContextAttribute.ROUTE_RESOLVED,
                SmartRedisLimiterContextAttribute.fromCode("routeResolved"));
        assertEquals(SmartRedisLimiterContextAttribute.FALLBACK_REASON,
                SmartRedisLimiterContextAttribute.fromCode("fallbackReason"));
        assertTrue(SmartRedisLimiterContextAttribute.isValid("routeKey"));
        assertEquals("routeKey", SmartRedisLimiterContextAttribute.ROUTE_KEY.toString());
        log.info("route 上下文属性测试通过");
    }

    @Test
    public void testConstantValues() {
        log.info("开始测试 2.0 常量");
        assertEquals("route_error", SmartRedisLimiterConstant.FALLBACK_REASON_ROUTE_ERROR);
        assertEquals("redis_error", SmartRedisLimiterConstant.FALLBACK_REASON_REDIS_ERROR);
        assertEquals("script_error", SmartRedisLimiterConstant.FALLBACK_REASON_SCRIPT_ERROR);
        assertEquals("key_provider_error", SmartRedisLimiterConstant.FALLBACK_REASON_KEY_PROVIDER_ERROR);
        assertEquals("timeout", SmartRedisLimiterConstant.FALLBACK_REASON_TIMEOUT);
        assertEquals("interrupted", SmartRedisLimiterConstant.FALLBACK_REASON_INTERRUPTED);
        assertEquals("unknown", SmartRedisLimiterConstant.FALLBACK_REASON_UNKNOWN);
        assertEquals("standalone", SmartRedisLimiterConstant.REDIS_MODE_STANDALONE);
        assertEquals("cluster", SmartRedisLimiterConstant.REDIS_MODE_CLUSTER);
        assertEquals("unknown", SmartRedisLimiterConstant.REDIS_MODE_UNKNOWN);
        assertTrue(SmartRedisLimiterConstant.DEFAULT_USE_HASH_TAG);
        assertEquals(1024, SmartRedisLimiterConstant.DEFAULT_TIMEOUT_EXECUTOR_QUEUE_CAPACITY);
        assertEquals("io.github.surezzzzzz.sdk.redis.route.template.RedisRouteTemplate",
                SmartRedisLimiterConstant.REDIS_ROUTE_TEMPLATE_CLASS_NAME);
        assertEquals("io.github.surezzzzzz.sdk.redis.route.configuration.SimpleRedisRouteConfiguration",
                SmartRedisLimiterConstant.REDIS_ROUTE_CONFIGURATION_CLASS_NAME);
        log.info("2.0 常量测试通过");
    }

    @Test
    public void testErrorCodeAndMessage() {
        log.info("开始测试错误码和错误消息");
        assertEquals("CONFIG_001", ErrorCode.CONFIG_REDIS_ROUTE_TEMPLATE_MISSING);
        assertEquals("ROUTE_001", ErrorCode.ROUTE_EXECUTION_FAILED);
        assertEquals("KEY_001", ErrorCode.KEY_PROVIDER_ERROR);
        assertEquals("KEY_002", ErrorCode.KEY_GENERATOR_NOT_FOUND);
        assertEquals("BIZ_001", ErrorCode.RATE_LIMIT_EXCEEDED);
        assertEquals(ErrorCode.RATE_LIMIT_EXCEEDED, SmartRedisLimiterConstant.ERROR_CODE_RATE_LIMIT_EXCEEDED);
        assertEquals(ErrorMessage.RATE_LIMIT_EXCEEDED, SmartRedisLimiterConstant.MSG_RATE_LIMIT_EXCEEDED);
        assertEquals(ErrorMessage.KEY_GENERATOR_NOT_FOUND_PREFIX,
                SmartRedisLimiterConstant.MSG_KEY_GENERATOR_NOT_FOUND);
        log.info("错误码和错误消息测试通过");
    }

    @Test
    public void testRecordRouteFields() {
        log.info("开始测试审计记录 route 字段");
        SmartRedisLimiterRecord record = SmartRedisLimiterRecord.builder()
                .limitKey("limit-key")
                .routeKey("route-key")
                .datasourceKey("default")
                .redisMode("standalone")
                .routeRequired(true)
                .routeResolved(true)
                .fallbackReason("redis_error")
                .build();

        assertEquals("route-key", record.getRouteKey());
        assertEquals("default", record.getDatasourceKey());
        assertEquals("standalone", record.getRedisMode());
        assertTrue(record.isRouteRequired());
        assertTrue(record.isRouteResolved());
        assertEquals("redis_error", record.getFallbackReason());
        log.info("审计记录 route 字段测试通过");
    }

    @Test
    public void testLimitExceededExceptionUsesStandardErrorCode() {
        log.info("开始测试限流异常标准错误码");
        SmartRedisLimitExceededException exception = new SmartRedisLimitExceededException("limit-key", 3);
        assertEquals(ErrorCode.RATE_LIMIT_EXCEEDED, exception.getErrorCode());
        assertEquals("limit-key", exception.getKey());
        assertEquals(3, exception.getRetryAfter());
        log.info("限流异常标准错误码测试通过");
    }
}
