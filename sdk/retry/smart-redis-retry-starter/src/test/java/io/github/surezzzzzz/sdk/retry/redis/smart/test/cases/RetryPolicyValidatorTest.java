package io.github.surezzzzzz.sdk.retry.redis.smart.test.cases;

import io.github.surezzzzzz.sdk.retry.redis.smart.constant.ErrorCode;
import io.github.surezzzzzz.sdk.retry.redis.smart.constant.SmartRedisRetryConstant;
import io.github.surezzzzzz.sdk.retry.redis.smart.exception.RetryValidationException;
import io.github.surezzzzzz.sdk.retry.redis.smart.model.RetryPolicy;
import io.github.surezzzzzz.sdk.retry.redis.smart.validator.RetryPolicyValidator;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import lombok.extern.slf4j.Slf4j;

/**
 * {@link RetryPolicyValidator} 单元测试
 *
 * @author surezzzzzz
 */
@Slf4j
class RetryPolicyValidatorTest {

    private final RetryPolicyValidator validator = new RetryPolicyValidator();

    private RetryPolicy policy(int retryTimes, long interval, long maxInterval, double multiplier, double jitter) {
        return RetryPolicy.builder()
                .maxRetryTimes(retryTimes)
                .retryIntervalMillis(interval)
                .maxIntervalMillis(maxInterval)
                .backoffMultiplier(multiplier)
                .jitterRatio(jitter)
                .build();
    }

    @Test
    void shouldRejectNullPolicy() {
        RetryValidationException ex = assertThrows(RetryValidationException.class, () -> validator.validate(null));
        assertEquals(ErrorCode.RETRY_POLICY_INVALID, ex.getErrorCode());
    }

    @Test
    void shouldRejectNonPositiveMaxRetryTimes() {
        assertThrows(RetryValidationException.class, () -> validator.validate(policy(0, 1L, 1L, 1D, 0D)));
    }

    @Test
    void shouldRejectBackoffMultiplierBelowOne() {
        assertThrows(RetryValidationException.class, () -> validator.validate(policy(3, 1L, 1L, 0.5D, 0D)));
    }

    @Test
    void shouldRejectJitterOutOfRange() {
        assertThrows(RetryValidationException.class, () -> validator.validate(policy(3, 1L, 1L, 1D, 1.5D)));
    }

    @Test
    void shouldRejectMaxIntervalLowerThanInterval() {
        assertThrows(RetryValidationException.class, () -> validator.validate(policy(3, 1000L, 100L, 1D, 0D)));
    }

    @Test
    void shouldRejectRetryTimesAboveLuaLimit() {
        assertThrows(RetryValidationException.class, () -> validator.validate(policy(
                SmartRedisRetryConstant.MAX_RETRY_TIMES + 1, 1L, 1L, 1D, 0D)));
        log.info("超出 Lua 重试次数上限的策略校验失败");
    }

    @Test
    void shouldRejectIntervalAboveLuaSafeLimit() {
        long invalidInterval = SmartRedisRetryConstant.MAX_RETRY_INTERVAL_MILLIS + 1L;
        assertThrows(RetryValidationException.class, () -> validator.validate(policy(
                1, invalidInterval, invalidInterval, 1D, 0D)));
        log.info("超出 Lua 安全整数范围的间隔校验失败");
    }

    @Test
    void shouldRejectNonFiniteRetryParameters() {
        assertThrows(RetryValidationException.class, () -> validator.validate(policy(
                1, 1L, 1L, Double.NaN, 0D)));
        assertThrows(RetryValidationException.class, () -> validator.validate(policy(
                1, 1L, 1L, 1D, Double.NaN)));
        assertThrows(RetryValidationException.class, () -> validator.validate(policy(
                1, 1L, 1L, Double.POSITIVE_INFINITY, 0D)));
        log.info("NaN 和无穷重试参数校验失败");
    }

    @Test
    void shouldAcceptLuaParameterLimits() {
        assertDoesNotThrow(() -> validator.validate(policy(SmartRedisRetryConstant.MAX_RETRY_TIMES,
                SmartRedisRetryConstant.MAX_RETRY_INTERVAL_MILLIS,
                SmartRedisRetryConstant.MAX_RETRY_INTERVAL_MILLIS,
                SmartRedisRetryConstant.MAX_BACKOFF_MULTIPLIER,
                SmartRedisRetryConstant.MAX_JITTER_RATIO)));
        log.info("Lua 参数边界策略校验通过");
    }

    @Test
    void shouldAcceptDefaultPolicy() {
        assertDoesNotThrow(() -> validator.validate(RetryPolicy.defaultPolicy()));
        log.info("默认策略校验通过");
    }

    @Test
    void shouldSupportRetryPolicyType() {
        assertTrue(validator.supports(RetryPolicy.class));
    }

    @Test
    void shouldNotSupportUnrelatedType() {
        assertFalse(validator.supports(String.class));
    }
}
