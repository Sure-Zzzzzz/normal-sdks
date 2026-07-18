package io.github.surezzzzzz.sdk.limiter.redis.smart.test.cases;

import io.github.surezzzzzz.sdk.limiter.redis.smart.constant.ErrorCode;
import io.github.surezzzzzz.sdk.limiter.redis.smart.constant.ErrorMessage;
import io.github.surezzzzzz.sdk.limiter.redis.smart.constant.SmartRedisLimiterConstant;
import io.github.surezzzzzz.sdk.limiter.redis.smart.constant.SmartRedisLimiterContextAttribute;
import io.github.surezzzzzz.sdk.limiter.redis.smart.exception.SmartRedisLimitExceededException;
import io.github.surezzzzzz.sdk.limiter.redis.smart.exception.SmartRedisLimiterException;
import io.github.surezzzzzz.sdk.limiter.redis.smart.model.SmartRedisLimiterRecord;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
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
        assertEquals("KEY_003", ErrorCode.KEY_PART_INVALID);
        assertEquals("KEY_004", ErrorCode.KEY_PART_CLIENT_IP_MISSING);
        assertEquals("KEY_005", ErrorCode.KEY_PART_REQUEST_PATH_MISSING);
        assertEquals("KEY_006", ErrorCode.KEY_PART_METHOD_MISSING);
        assertEquals("KEY_007", ErrorCode.KEY_PART_PATH_PATTERN_MISSING);
        assertEquals("BIZ_001", ErrorCode.RATE_LIMIT_EXCEEDED);
        assertEquals("VALIDATION_001", ErrorCode.POLICY_KEY_INVALID);
        assertEquals("VALIDATION_011", ErrorCode.EXECUTION_POLICY_CONTEXT_INVALID);
        assertEquals("VALIDATION_012", ErrorCode.ATTRIBUTE_VALUE_INVALID);
        assertEquals("VALIDATION_013", ErrorCode.EXECUTION_EVENT_PAYLOAD_INVALID);
        assertEquals("1", SmartRedisLimiterConstant.POLICY_SCHEMA_VERSION);
        assertEquals("local", SmartRedisLimiterConstant.POLICY_SOURCE_LOCAL);
        assertEquals("remote", SmartRedisLimiterConstant.POLICY_SOURCE_REMOTE);
        assertEquals(ErrorCode.RATE_LIMIT_EXCEEDED, SmartRedisLimiterConstant.ERROR_CODE_RATE_LIMIT_EXCEEDED);
        assertEquals(ErrorMessage.RATE_LIMIT_EXCEEDED, SmartRedisLimiterConstant.MSG_RATE_LIMIT_EXCEEDED);
        assertEquals(ErrorMessage.KEY_GENERATOR_NOT_FOUND_PREFIX,
                SmartRedisLimiterConstant.MSG_KEY_GENERATOR_NOT_FOUND);
        assertEquals(ErrorMessage.KEY_PART_INVALID, ErrorMessage.KEY_PART_EMPTY);
        assertEquals(ErrorMessage.KEY_PART_CLIENT_IP_MISSING, ErrorMessage.CLIENT_IP_NULL);
        assertEquals(ErrorMessage.KEY_PART_REQUEST_PATH_MISSING, ErrorMessage.REQUEST_PATH_NULL);
        assertEquals(ErrorMessage.KEY_PART_METHOD_MISSING, ErrorMessage.METHOD_NULL);
        assertEquals(ErrorMessage.KEY_PART_PATH_PATTERN_MISSING, ErrorMessage.PATH_PATTERN_NULL);
        log.info("错误码和错误消息测试通过");
    }

    @Test
    public void testDefaultExcludePatternListIsImmutable() {
        log.info("开始测试默认排除路径不可变集合");
        assertEquals(4, SmartRedisLimiterConstant.DEFAULT_EXCLUDE_PATTERN_LIST.size(),
                "默认排除路径数量应保持为 4");
        assertThrows(UnsupportedOperationException.class,
                () -> SmartRedisLimiterConstant.DEFAULT_EXCLUDE_PATTERN_LIST.add("/test/**"),
                "默认排除路径集合不应允许修改");
        log.info("默认排除路径不可变集合测试通过");
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
                .resourceCode("test-resource")
                .policySource("remote")
                .policyRevision(3L)
                .build();
        record.validatePolicyContext();

        assertEquals("route-key", record.getRouteKey());
        assertEquals("default", record.getDatasourceKey());
        assertEquals("standalone", record.getRedisMode());
        assertTrue(record.isRouteRequired());
        assertTrue(record.isRouteResolved());
        assertEquals("redis_error", record.getFallbackReason());
        assertEquals("test-resource", record.getResourceCode());
        assertEquals("remote", record.getPolicySource());
        assertEquals(3L, record.getPolicyRevision());

        SmartRedisLimiterRecord invalidRecord = SmartRedisLimiterRecord.builder()
                .policySource(SmartRedisLimiterConstant.POLICY_SOURCE_REMOTE)
                .build();
        SmartRedisLimiterException invalid = assertThrows(SmartRedisLimiterException.class,
                invalidRecord::validatePolicyContext,
                "remote Record 缺少资源编码和版本时应拒绝通过最终校验");
        assertEquals(ErrorCode.EXECUTION_POLICY_CONTEXT_INVALID, invalid.getErrorCode(),
                "Record 非法策略上下文应使用统一错误码");
        log.info("审计记录 route 字段测试通过");
    }

    @Test
    public void testRecordPolicyContextValidationMatrix() {
        log.info("开始测试 Record 策略上下文完整矩阵");
        SmartRedisLimiterRecord defaultLocal = new SmartRedisLimiterRecord();
        defaultLocal.setPolicySource(null);
        defaultLocal.validatePolicyContext();
        assertEquals(SmartRedisLimiterConstant.POLICY_SOURCE_LOCAL, defaultLocal.getPolicySource(),
                "空策略来源应规范化为 local");

        SmartRedisLimiterRecord normalizedRemote = SmartRedisLimiterRecord.builder()
                .policySource(SmartRedisLimiterConstant.POLICY_SOURCE_REMOTE)
                .resourceCode(" test-resource ")
                .policyRevision(1L)
                .build();
        normalizedRemote.validatePolicyContext();
        assertEquals("test-resource", normalizedRemote.getResourceCode(),
                "远程资源编码应完成规范化");

        assertRecordPolicyContextInvalid(SmartRedisLimiterRecord.builder()
                .policySource(SmartRedisLimiterConstant.POLICY_SOURCE_LOCAL)
                .policyRevision(1L)
                .build());
        assertRecordPolicyContextInvalid(SmartRedisLimiterRecord.builder()
                .policySource(SmartRedisLimiterConstant.POLICY_SOURCE_REMOTE)
                .policyRevision(1L)
                .build());
        assertRecordPolicyContextInvalid(SmartRedisLimiterRecord.builder()
                .policySource(SmartRedisLimiterConstant.POLICY_SOURCE_REMOTE)
                .resourceCode("test-resource")
                .build());
        assertRecordPolicyContextInvalid(SmartRedisLimiterRecord.builder()
                .policySource(SmartRedisLimiterConstant.POLICY_SOURCE_REMOTE)
                .resourceCode("test-resource")
                .policyRevision(-1L)
                .build());
        assertRecordPolicyContextInvalid(SmartRedisLimiterRecord.builder()
                .policySource("invalid")
                .build());

        SmartRedisLimiterRecord legacy = new SmartRedisLimiterRecord(
                null, null, null, null, null, null, null, null,
                true, null, null, null, false, false, null,
                null, null, null, null, null, null, null,
                0L, 0L, 0L, 0L, null, null, null);
        legacy.validatePolicyContext();
        assertEquals(SmartRedisLimiterConstant.POLICY_SOURCE_LOCAL, legacy.getPolicySource(),
                "2.0.0 旧构造器创建的 Record 应通过 local 校验");
        log.info("Record 策略上下文完整矩阵测试通过");
    }

    private void assertRecordPolicyContextInvalid(SmartRedisLimiterRecord record) {
        SmartRedisLimiterException exception = assertThrows(SmartRedisLimiterException.class,
                record::validatePolicyContext,
                "非法 Record 策略上下文应被拒绝");
        assertEquals(ErrorCode.EXECUTION_POLICY_CONTEXT_INVALID, exception.getErrorCode(),
                "非法 Record 策略上下文应使用统一错误码");
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
