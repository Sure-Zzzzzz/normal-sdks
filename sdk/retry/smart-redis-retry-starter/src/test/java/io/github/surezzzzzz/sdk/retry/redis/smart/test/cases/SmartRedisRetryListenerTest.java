package io.github.surezzzzzz.sdk.retry.redis.smart.test.cases;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.surezzzzzz.sdk.redis.route.template.RedisRouteTemplate;
import io.github.surezzzzzz.sdk.retry.redis.smart.clock.SystemRetryClock;
import io.github.surezzzzzz.sdk.retry.redis.smart.configuration.SmartRedisRetryProperties;
import io.github.surezzzzzz.sdk.retry.redis.smart.engine.DefaultSmartRedisRetryEngine;
import io.github.surezzzzzz.sdk.retry.redis.smart.engine.SmartRedisRetryEngine;
import io.github.surezzzzzz.sdk.retry.redis.smart.listener.NoopSmartRedisRetryListener;
import io.github.surezzzzzz.sdk.retry.redis.smart.model.RetryFailure;
import io.github.surezzzzzz.sdk.retry.redis.smart.model.RetryInfo;
import io.github.surezzzzzz.sdk.retry.redis.smart.model.RetryPolicy;
import io.github.surezzzzzz.sdk.retry.redis.smart.policy.DefaultRetryPolicyResolver;
import io.github.surezzzzzz.sdk.retry.redis.smart.script.RedisRetryScriptExecutor;
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
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Smart Redis Retry 监听器测试
 *
 * @author surezzzzzz
 */
@Slf4j
class SmartRedisRetryListenerTest {

    @Test
    void exhaustedListenerShouldOnlyBeCalledWhenRecordFirstReachesThreshold() {
        AtomicInteger exhaustedCount = new AtomicInteger();
        SmartRedisRetryEngine engine = engine(exhaustedCount);
        RetryFailure failure = RetryFailure.builder()
                .retryType("test-compensation")
                .retryKey("test-key")
                .policy(RetryPolicy.builder().maxRetryTimes(2).retryIntervalMillis(1000L)
                        .maxIntervalMillis(1000L).backoffMultiplier(1D).jitterRatio(0D).build())
                .build();

        engine.recordFailure(failure);
        engine.recordFailure(failure);
        engine.recordFailure(failure);

        log.info("首次耗尽监听回调次数={}", exhaustedCount.get());
        assertEquals(1, exhaustedCount.get(), "耗尽监听器只能在首次达到阈值时回调一次");
    }

    @Test
    void exhaustedListenerShouldFireImmediatelyWhenMaxRetryTimesIsOne() {
        AtomicInteger exhaustedCount = new AtomicInteger();
        RedisRetryScriptExecutor executor = new SequentialRetryScriptExecutor(Arrays.asList(
                RetryInfo.builder().count(1).maxRetryTimes(1).build(),
                RetryInfo.builder().count(2).maxRetryTimes(1).build()));
        SmartRedisRetryEngine engine = engine(exhaustedCount, executor);
        RetryFailure failure = RetryFailure.builder()
                .retryType("test-compensation")
                .retryKey("test-key")
                .policy(RetryPolicy.builder().maxRetryTimes(1).retryIntervalMillis(1000L)
                        .maxIntervalMillis(1000L).backoffMultiplier(1D).jitterRatio(0D).build())
                .build();

        engine.recordFailure(failure);
        engine.recordFailure(failure);

        log.info("max=1 时首次即触发的耗尽监听回调次数={}", exhaustedCount.get());
        assertEquals(1, exhaustedCount.get(), "maxRetryTimes=1 时首次 record 即应触发耗尽回调，后续不再重复");
    }

    private SmartRedisRetryEngine engine(AtomicInteger exhaustedCount) {
        return engine(exhaustedCount, new SequentialRetryScriptExecutor());
    }

    private SmartRedisRetryEngine engine(AtomicInteger exhaustedCount, RedisRetryScriptExecutor scriptExecutor) {
        SmartRedisRetryProperties properties = new SmartRedisRetryProperties();
        RetryContextSerializer serializer = new JacksonRetryContextSerializer(new ObjectMapper());
        RetryPolicyValidator policyValidator = new RetryPolicyValidator();
        RetryInfoConvertHelper convertHelper = new RetryInfoConvertHelper();
        RetryRequestValidatorChain chain = new RetryRequestValidatorChain(Arrays.<RetryRequestValidator<?>>asList(
                new RetryFailureValidator(properties, policyValidator),
                new RetryContextValidator(properties, serializer),
                new RetryScanRequestValidator(properties)));
        return new DefaultSmartRedisRetryEngine(new CallbackRedisRouteTemplate(), properties,
                new DefaultRetryPolicyResolver(properties), new NoopSmartRedisRetryListener() {
                    @Override
                    public void onExhausted(String retryType, String retryKey, RetryInfo retryInfo) {
                        exhaustedCount.incrementAndGet();
                    }
                }, new SystemRetryClock(), new RetryKeyHelper(properties), convertHelper, serializer,
                chain, policyValidator, scriptExecutor);
    }

    private static class CallbackRedisRouteTemplate extends RedisRouteTemplate {
        private CallbackRedisRouteTemplate() {
            super(null, null);
        }

        @Override
        public <T> T execute(String redisKey, Function<StringRedisTemplate, T> callback) {
            return callback.apply(null);
        }
    }

    private static class SequentialRetryScriptExecutor implements RedisRetryScriptExecutor {
        private final List<RetryInfo> retryInfos;
        private int index;

        SequentialRetryScriptExecutor() {
            this(Arrays.asList(
                    RetryInfo.builder().count(1).maxRetryTimes(2).build(),
                    RetryInfo.builder().count(2).maxRetryTimes(2).build(),
                    RetryInfo.builder().count(3).maxRetryTimes(2).build()));
        }

        SequentialRetryScriptExecutor(List<RetryInfo> retryInfos) {
            this.retryInfos = retryInfos;
        }

        @Override
        public RetryInfo recordFailure(StringRedisTemplate template, String redisKey, RetryFailure failure,
                                       RetryPolicy policy, long nowMillis, long ttlMillis) {
            return retryInfos.get(index++);
        }

        @Override
        public RetryInfo clear(StringRedisTemplate template, String redisKey, Integer expectedCount) {
            return null;
        }
    }
}
