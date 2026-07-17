package io.github.surezzzzzz.sdk.retry.redis.smart.test.cases;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.surezzzzzz.sdk.redis.route.template.RedisRouteTemplate;
import io.github.surezzzzzz.sdk.retry.redis.smart.clock.SystemRetryClock;
import io.github.surezzzzzz.sdk.retry.redis.smart.configuration.SmartRedisRetryProperties;
import io.github.surezzzzzz.sdk.retry.redis.smart.constant.RedisFailureStrategy;
import io.github.surezzzzzz.sdk.retry.redis.smart.constant.RetryDecisionType;
import io.github.surezzzzzz.sdk.retry.redis.smart.engine.DefaultSmartRedisRetryEngine;
import io.github.surezzzzzz.sdk.retry.redis.smart.engine.SmartRedisRetryEngine;
import io.github.surezzzzzz.sdk.retry.redis.smart.exception.RetryOperationException;
import io.github.surezzzzzz.sdk.retry.redis.smart.listener.NoopSmartRedisRetryListener;
import io.github.surezzzzzz.sdk.retry.redis.smart.model.RetryDecision;
import io.github.surezzzzzz.sdk.retry.redis.smart.model.RetryInfo;
import io.github.surezzzzzz.sdk.retry.redis.smart.model.RetryScanRequest;
import io.github.surezzzzzz.sdk.retry.redis.smart.model.RetryScanResult;
import io.github.surezzzzzz.sdk.retry.redis.smart.policy.DefaultRetryPolicyResolver;
import io.github.surezzzzzz.sdk.retry.redis.smart.script.LuaRedisRetryScriptExecutor;
import io.github.surezzzzzz.sdk.retry.redis.smart.serializer.JacksonRetryContextSerializer;
import io.github.surezzzzzz.sdk.retry.redis.smart.serializer.RetryContextSerializer;
import io.github.surezzzzzz.sdk.retry.redis.smart.support.RetryInfoConvertHelper;
import io.github.surezzzzzz.sdk.retry.redis.smart.support.RetryKeyHelper;
import io.github.surezzzzzz.sdk.retry.redis.smart.validator.RetryContextValidator;
import io.github.surezzzzzz.sdk.retry.redis.smart.validator.RetryFailureValidator;
import io.github.surezzzzzz.sdk.retry.redis.smart.validator.RetryPolicyValidator;
import io.github.surezzzzzz.sdk.retry.redis.smart.validator.RetryRequestValidator;
import io.github.surezzzzzz.sdk.retry.redis.smart.validator.RetryRequestValidatorChain;
import io.github.surezzzzzz.sdk.retry.redis.smart.validator.RetryScanRequestValidator;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.Arrays;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import lombok.extern.slf4j.Slf4j;

/**
 * Redis 故障策略单元测试
 *
 * @author surezzzzzz
 */
@Slf4j
class SmartRedisRetryFailureStrategyTest {

    @Test
    void failOpenShouldAllowRetryWhenRedisOperationFails() {
        log.info("测试 FAIL_OPEN 策略：Redis 故障时放行重试");
        SmartRedisRetryEngine engine = engine(RedisFailureStrategy.FAIL_OPEN.getCode());
        RetryDecision decision = engine.decide("test-compensation", "test-key");
        assertEquals(RetryDecisionType.ALLOW, decision.getType());
        assertTrue(decision.isAllowed());
        assertNull(engine.recordFailure("test-compensation", "test-key"));
        assertNull(engine.getInfo("test-compensation", "test-key"));
        assertNull(engine.clear("test-compensation", "test-key"));
        RetryScanResult scanResult = engine.scan(scanRequest());
        assertTrue(scanResult.isFinished());
        assertTrue(scanResult.getKeys().isEmpty());
        assertTrue(scanResult.getInfos().isEmpty());
    }

    @Test
    void failClosedShouldRejectRetryWhenRedisOperationFails() {
        SmartRedisRetryEngine engine = engine(RedisFailureStrategy.FAIL_CLOSED.getCode());
        RetryDecision decision = engine.decide("test-compensation", "test-key");
        assertEquals(RetryDecisionType.WAITING, decision.getType());
        assertFalse(decision.isAllowed());
        assertNull(engine.recordFailure("test-compensation", "test-key"));
        assertNull(engine.getInfo("test-compensation", "test-key"));
        assertNull(engine.clear("test-compensation", "test-key"));
        RetryScanResult scanResult = engine.scan(scanRequest());
        assertTrue(scanResult.isFinished());
        assertTrue(scanResult.getKeys().isEmpty());
        assertTrue(scanResult.getInfos().isEmpty());
    }

    @Test
    void throwStrategyShouldThrowRetryOperationExceptionWhenRedisOperationFails() {
        SmartRedisRetryEngine engine = engine(RedisFailureStrategy.THROW.getCode());
        assertThrows(RetryOperationException.class, () -> engine.decide("test-compensation", "test-key"));
        assertThrows(RetryOperationException.class, () -> engine.recordFailure("test-compensation", "test-key"));
        assertThrows(RetryOperationException.class, () -> engine.getInfo("test-compensation", "test-key"));
        assertThrows(RetryOperationException.class, () -> engine.clear("test-compensation", "test-key"));
        assertThrows(RetryOperationException.class, () -> engine.scan(scanRequest()));
    }

    @Test
    void exhaustedDecisionShouldNotBeChangedWhenAutomaticClearFails() {
        log.info("测试自动清理失败不改变已经得出的耗尽决策");
        SmartRedisRetryProperties properties = new SmartRedisRetryProperties();
        properties.getGuard().setRedisFailureStrategy(RedisFailureStrategy.FAIL_OPEN.getCode());
        properties.getRedis().setRetainExhausted(false);
        RetryInfo exhausted = RetryInfo.builder().count(1).maxRetryTimes(1).build();
        SmartRedisRetryEngine engine = engine(properties, new ExhaustedThenClearFailRedisRouteTemplate(exhausted));

        RetryDecision decision = engine.decide("test-compensation", "test-key");

        assertEquals(RetryDecisionType.EXHAUSTED, decision.getType());
        assertFalse(decision.isAllowed());
    }

    private RetryScanRequest scanRequest() {
        return RetryScanRequest.builder()
                .routeKey("sure-smart-redis-retry:retry:test-compensation:default::mock-route")
                .retryType("test-compensation")
                .cursor("0")
                .count(50)
                .includeInfo(true)
                .build();
    }

    private SmartRedisRetryEngine engine(String failureStrategy) {
        SmartRedisRetryProperties properties = new SmartRedisRetryProperties();
        properties.getGuard().setRedisFailureStrategy(failureStrategy);
        return engine(properties, new FailingRedisRouteTemplate());
    }

    private SmartRedisRetryEngine engine(SmartRedisRetryProperties properties,
                                         RedisRouteTemplate redisRouteTemplate) {
        RetryContextSerializer serializer = new JacksonRetryContextSerializer(new ObjectMapper());
        RetryPolicyValidator policyValidator = new RetryPolicyValidator();
        RetryInfoConvertHelper convertHelper = new RetryInfoConvertHelper();
        RetryRequestValidatorChain chain = new RetryRequestValidatorChain(Arrays.<RetryRequestValidator<?>>asList(
                new RetryFailureValidator(properties, policyValidator),
                new RetryContextValidator(properties, serializer),
                new RetryScanRequestValidator(properties)));
        return new DefaultSmartRedisRetryEngine(redisRouteTemplate, properties,
                new DefaultRetryPolicyResolver(properties), new NoopSmartRedisRetryListener(),
                new SystemRetryClock(), new RetryKeyHelper(properties), convertHelper, serializer,
                chain, policyValidator, new LuaRedisRetryScriptExecutor(serializer, convertHelper));
    }

    private static class FailingRedisRouteTemplate extends RedisRouteTemplate {
        private FailingRedisRouteTemplate() {
            super(null, null);
        }

        @Override
        public <T> T execute(String redisKey, Function<StringRedisTemplate, T> callback) {
            throw new IllegalStateException("mock redis failure");
        }

        @Override
        public StringRedisTemplate stringTemplateByKey(String redisKey) {
            throw new IllegalStateException("mock redis failure");
        }
    }

    private static class ExhaustedThenClearFailRedisRouteTemplate extends RedisRouteTemplate {
        private final RetryInfo exhausted;
        private int executeCount;

        private ExhaustedThenClearFailRedisRouteTemplate(RetryInfo exhausted) {
            super(null, null);
            this.exhausted = exhausted;
        }

        @Override
        public <T> T execute(String redisKey, Function<StringRedisTemplate, T> callback) {
            executeCount++;
            if (executeCount == 1) {
                return (T) exhausted;
            }
            throw new IllegalStateException("mock automatic clear failure");
        }
    }
}
