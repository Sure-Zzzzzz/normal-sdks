package io.github.surezzzzzz.sdk.limiter.redis.smart.test.cases;

import io.github.surezzzzzz.sdk.limiter.redis.smart.constant.ErrorCode;
import io.github.surezzzzzz.sdk.limiter.redis.smart.constant.SmartRedisLimiterConstant;
import io.github.surezzzzzz.sdk.limiter.redis.smart.constant.SmartRedisLimiterTimeUnit;
import io.github.surezzzzzz.sdk.limiter.redis.smart.exception.SmartRedisLimiterException;
import io.github.surezzzzzz.sdk.limiter.redis.smart.model.policy.SmartRedisLimiterLimit;
import io.github.surezzzzzz.sdk.limiter.redis.smart.model.policy.SmartRedisLimiterPolicy;
import io.github.surezzzzzz.sdk.limiter.redis.smart.model.policy.SmartRedisLimiterPolicyKey;
import io.github.surezzzzzz.sdk.limiter.redis.smart.model.policy.SmartRedisLimiterPolicySnapshot;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * SmartRedisLimiter 动态策略模型测试
 *
 * @author surezzzzzz
 */
@Slf4j
public class SmartRedisLimiterPolicyModelTest {

    @Test
    public void testPolicyKeyNormalizationAndValidation() {
        log.info("开始测试策略键规范化与校验");
        SmartRedisLimiterPolicyKey key = new SmartRedisLimiterPolicyKey(
                " test-service ", " test-resource ", " 测试对象 ");

        assertEquals("test-service", key.getServiceCode());
        assertEquals("test-resource", key.getResourceCode());
        assertEquals("测试对象", key.getSubject());
        assertNotEquals(key, new SmartRedisLimiterPolicyKey(
                "TEST-service", "test-resource", "测试对象"));

        assertErrorCode(ErrorCode.POLICY_KEY_INVALID,
                () -> new SmartRedisLimiterPolicyKey("test:service", "test-resource", "test-subject"));
        SmartRedisLimiterException subjectException = assertThrows(SmartRedisLimiterException.class,
                () -> new SmartRedisLimiterPolicyKey("test-service", "test-resource", "secret\nvalue"));
        assertEquals(ErrorCode.POLICY_KEY_INVALID, subjectException.getErrorCode());
        assertFalse(subjectException.getMessage().contains("secret"), "异常消息不应泄露 subject 原始值");
        log.info("策略键规范化与校验测试通过");
    }

    @Test
    public void testTimeUnitAndLimitValidation() {
        log.info("开始测试时间单位与限额校验");
        assertEquals(SmartRedisLimiterTimeUnit.MINUTES,
                SmartRedisLimiterTimeUnit.fromCode("MINUTES"));
        assertEquals(SmartRedisLimiterTimeUnit.MINUTES,
                SmartRedisLimiterTimeUnit.fromCode("minutes"));
        assertEquals(60L, SmartRedisLimiterTimeUnit.MINUTES.toSeconds(1));

        SmartRedisLimiterLimit limit = new SmartRedisLimiterLimit(
                100L, 2L, SmartRedisLimiterTimeUnit.MINUTES);
        assertEquals(120L, limit.getWindowSeconds());
        assertEquals(new SmartRedisLimiterLimit(100L, 120L, SmartRedisLimiterTimeUnit.SECONDS), limit,
                "等价标准化窗口应具有相同值语义");
        assertEquals(new SmartRedisLimiterLimit(100L, 120L, SmartRedisLimiterTimeUnit.SECONDS).hashCode(),
                limit.hashCode(), "等价标准化窗口应具有相同哈希值");

        assertErrorCode(ErrorCode.POLICY_LIMIT_INVALID,
                () -> new SmartRedisLimiterLimit(0L, 1L, SmartRedisLimiterTimeUnit.SECONDS));
        assertErrorCode(ErrorCode.POLICY_TIME_UNIT_INVALID,
                () -> new SmartRedisLimiterLimit(1L, 1L, null));
        SmartRedisLimiterException overflow = assertThrows(SmartRedisLimiterException.class,
                () -> SmartRedisLimiterTimeUnit.DAYS.toSeconds(Long.MAX_VALUE));
        assertEquals(ErrorCode.POLICY_WINDOW_OVERFLOW, overflow.getErrorCode());
        assertTrue(overflow.getCause() instanceof ArithmeticException, "溢出异常应保留原始原因");
        log.info("时间单位与限额校验测试通过");
    }

    @Test
    public void testPolicySortsAndProtectsLimits() {
        log.info("开始测试策略窗口排序和不可变性");
        SmartRedisLimiterPolicyKey key = policyKey("test-subject");
        List<SmartRedisLimiterLimit> limits = new ArrayList<>(Arrays.asList(
                new SmartRedisLimiterLimit(10L, 1L, SmartRedisLimiterTimeUnit.DAYS),
                new SmartRedisLimiterLimit(2L, 1L, SmartRedisLimiterTimeUnit.SECONDS)));

        SmartRedisLimiterPolicy policy = new SmartRedisLimiterPolicy(key, limits);
        limits.clear();

        assertEquals(2, policy.getLimits().size());
        assertEquals(1L, policy.getLimits().get(0).getWindowSeconds());
        assertEquals(86400L, policy.getLimits().get(1).getWindowSeconds());
        assertThrows(UnsupportedOperationException.class,
                () -> policy.getLimits().add(new SmartRedisLimiterLimit(
                        1L, 1L, SmartRedisLimiterTimeUnit.HOURS)));

        assertErrorCode(ErrorCode.POLICY_DUPLICATE_WINDOW,
                () -> new SmartRedisLimiterPolicy(key, Arrays.asList(
                        new SmartRedisLimiterLimit(10L, 60L, SmartRedisLimiterTimeUnit.SECONDS),
                        new SmartRedisLimiterLimit(20L, 1L, SmartRedisLimiterTimeUnit.MINUTES))));
        log.info("策略窗口排序和不可变性测试通过");
    }

    @Test
    public void testSnapshotValidationAndEmptySnapshot() {
        log.info("开始测试服务级快照校验");
        Instant publishedAt = Instant.parse("2026-07-17T10:00:00Z");
        SmartRedisLimiterPolicy policy = new SmartRedisLimiterPolicy(
                policyKey("test-subject"),
                Collections.singletonList(new SmartRedisLimiterLimit(
                        10L, 1L, SmartRedisLimiterTimeUnit.MINUTES)));
        List<SmartRedisLimiterPolicy> policies = new ArrayList<>();
        policies.add(policy);

        SmartRedisLimiterPolicySnapshot snapshot = new SmartRedisLimiterPolicySnapshot(
                SmartRedisLimiterConstant.POLICY_SCHEMA_VERSION,
                " test-service ", 3L, publishedAt, policies);
        policies.clear();

        assertEquals("test-service", snapshot.getServiceCode());
        assertEquals(1, snapshot.getPolicies().size());
        assertThrows(UnsupportedOperationException.class, () -> snapshot.getPolicies().clear());

        SmartRedisLimiterPolicySnapshot emptySnapshot = new SmartRedisLimiterPolicySnapshot(
                SmartRedisLimiterConstant.POLICY_SCHEMA_VERSION,
                "test-service", 0L, publishedAt, Collections.emptyList());
        assertTrue(emptySnapshot.getPolicies().isEmpty());

        assertErrorCode(ErrorCode.POLICY_SNAPSHOT_SERVICE_MISMATCH,
                () -> new SmartRedisLimiterPolicySnapshot(
                        SmartRedisLimiterConstant.POLICY_SCHEMA_VERSION,
                        "other-service", 1L, publishedAt, Collections.singletonList(policy)));
        assertErrorCode(ErrorCode.POLICY_DUPLICATE_KEY,
                () -> new SmartRedisLimiterPolicySnapshot(
                        SmartRedisLimiterConstant.POLICY_SCHEMA_VERSION,
                        "test-service", 1L, publishedAt, Arrays.asList(policy, policy)));
        log.info("服务级快照校验测试通过");
    }

    private SmartRedisLimiterPolicyKey policyKey(String subject) {
        return new SmartRedisLimiterPolicyKey("test-service", "test-resource", subject);
    }

    private void assertErrorCode(String errorCode, Runnable action) {
        SmartRedisLimiterException exception = assertThrows(SmartRedisLimiterException.class, action::run);
        assertEquals(errorCode, exception.getErrorCode());
    }
}
