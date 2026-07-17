package io.github.surezzzzzz.sdk.retry.redis.smart.test.cases;

import io.github.surezzzzzz.sdk.retry.redis.smart.configuration.SmartRedisRetryProperties;
import io.github.surezzzzzz.sdk.retry.redis.smart.model.RetryFailure;
import io.github.surezzzzzz.sdk.retry.redis.smart.model.RetryPolicy;
import io.github.surezzzzzz.sdk.retry.redis.smart.policy.DefaultRetryPolicyResolver;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import lombok.extern.slf4j.Slf4j;

/**
 * {@link DefaultRetryPolicyResolver} 单元测试
 *
 * @author surezzzzzz
 */
@Slf4j
class DefaultRetryPolicyResolverTest {

    private SmartRedisRetryProperties properties;
    private DefaultRetryPolicyResolver resolver;

    @BeforeEach
    void setUp() {
        properties = new SmartRedisRetryProperties();
        resolver = new DefaultRetryPolicyResolver(properties);
        log.info("初始化 DefaultRetryPolicyResolver 测试");
    }

    @Test
    void failurePolicyShouldTakePriority() {
        RetryPolicy custom = RetryPolicy.builder()
                .maxRetryTimes(9).retryIntervalMillis(2000L).maxIntervalMillis(20000L)
                .backoffMultiplier(2D).jitterRatio(0.3D).build();
        RetryFailure failure = RetryFailure.builder().retryType("test-compensation").retryKey("k").policy(custom).build();
        RetryPolicy resolved = resolver.resolve("test-compensation", failure);
        assertEquals(Integer.valueOf(9), resolved.getMaxRetryTimes());
        assertEquals(Long.valueOf(2000L), resolved.getRetryIntervalMillis());
    }

    @Test
    void scenePolicyShouldTakePriorityOverDefault() {
        RetryPolicy scene = RetryPolicy.builder()
                .maxRetryTimes(20).retryIntervalMillis(60000L).maxIntervalMillis(1800000L)
                .backoffMultiplier(2D).jitterRatio(0.2D).build();
        properties.getPolicy().getScene().put("test-compensation", scene);
        RetryFailure failure = RetryFailure.builder().retryType("test-compensation").retryKey("k").build();
        RetryPolicy resolved = resolver.resolve("test-compensation", failure);
        assertEquals(Integer.valueOf(20), resolved.getMaxRetryTimes());
    }

    @Test
    void defaultPolicyShouldBeUsedWhenNoSceneAndNoFailurePolicy() {
        RetryFailure failure = RetryFailure.builder().retryType("unknown-scene").retryKey("k").build();
        RetryPolicy resolved = resolver.resolve("unknown-scene", failure);
        RetryPolicy expected = RetryPolicy.defaultPolicy();
        assertEquals(expected.getMaxRetryTimes(), resolved.getMaxRetryTimes());
        assertEquals(expected.getRetryIntervalMillis(), resolved.getRetryIntervalMillis());
    }

    @Test
    void partialScenePolicyShouldInheritConfiguredDefaultPolicy() {
        RetryPolicy defaultPolicy = RetryPolicy.builder()
                .maxRetryTimes(8).retryIntervalMillis(3000L).maxIntervalMillis(30000L)
                .backoffMultiplier(1.8D).jitterRatio(0.4D).build();
        RetryPolicy scenePolicy = RetryPolicy.builder().maxRetryTimes(5).build();
        properties.getPolicy().setDefaultPolicy(defaultPolicy);
        properties.getPolicy().getScene().put("test-compensation", scenePolicy);

        RetryPolicy resolved = resolver.resolve("test-compensation",
                RetryFailure.builder().retryType("test-compensation").retryKey("k").build());

        assertEquals(Integer.valueOf(5), resolved.getMaxRetryTimes());
        assertEquals(Long.valueOf(3000L), resolved.getRetryIntervalMillis());
        assertEquals(Long.valueOf(30000L), resolved.getMaxIntervalMillis());
        assertEquals(Double.valueOf(1.8D), resolved.getBackoffMultiplier());
        assertEquals(Double.valueOf(0.4D), resolved.getJitterRatio());
    }

    @Test
    void partialFailurePolicyShouldInheritSceneAndConfiguredDefaultPolicy() {
        RetryPolicy defaultPolicy = RetryPolicy.builder()
                .maxRetryTimes(8).retryIntervalMillis(3000L).maxIntervalMillis(30000L)
                .backoffMultiplier(1.8D).jitterRatio(0.4D).build();
        RetryPolicy scenePolicy = RetryPolicy.builder().maxRetryTimes(5).backoffMultiplier(2D).build();
        RetryPolicy failurePolicy = RetryPolicy.builder().retryIntervalMillis(1000L).build();
        properties.getPolicy().setDefaultPolicy(defaultPolicy);
        properties.getPolicy().getScene().put("test-compensation", scenePolicy);

        RetryPolicy resolved = resolver.resolve("test-compensation", RetryFailure.builder()
                .retryType("test-compensation").retryKey("k").policy(failurePolicy).build());

        assertEquals(Integer.valueOf(5), resolved.getMaxRetryTimes());
        assertEquals(Long.valueOf(1000L), resolved.getRetryIntervalMillis());
        assertEquals(Long.valueOf(30000L), resolved.getMaxIntervalMillis());
        assertEquals(Double.valueOf(2D), resolved.getBackoffMultiplier());
        assertEquals(Double.valueOf(0.4D), resolved.getJitterRatio());
    }

    @Test
    void nullFieldsShouldBeFilledWithBuiltInDefaults() {
        RetryPolicy partial = RetryPolicy.builder().maxRetryTimes(5).build();
        RetryFailure failure = RetryFailure.builder().retryType("t").retryKey("k").policy(partial).build();
        RetryPolicy resolved = resolver.resolve("t", failure);
        RetryPolicy defaults = RetryPolicy.defaultPolicy();
        assertEquals(Integer.valueOf(5), resolved.getMaxRetryTimes());
        assertEquals(defaults.getRetryIntervalMillis(), resolved.getRetryIntervalMillis());
        assertEquals(defaults.getBackoffMultiplier(), resolved.getBackoffMultiplier());
    }
}
