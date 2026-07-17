package io.github.surezzzzzz.sdk.retry.redis.smart.test.cases;

import io.github.surezzzzzz.sdk.retry.redis.smart.engine.SmartRedisRetryEngine;
import io.github.surezzzzzz.sdk.retry.redis.smart.listener.NoopSmartRedisRetryListener;
import io.github.surezzzzzz.sdk.retry.redis.smart.listener.SmartRedisRetryListener;
import io.github.surezzzzzz.sdk.retry.redis.smart.configuration.SmartRedisRetryAutoConfiguration;
import io.github.surezzzzzz.sdk.retry.redis.smart.configuration.SmartRedisRetryProperties;
import io.github.surezzzzzz.sdk.retry.redis.smart.exception.RetryValidationException;
import io.github.surezzzzzz.sdk.retry.redis.smart.support.RetryInfoConvertHelper;
import io.github.surezzzzzz.sdk.retry.redis.smart.test.SmartRedisRetryTestApplication;
import io.github.surezzzzzz.sdk.retry.redis.smart.validator.RetryPolicyValidator;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import lombok.extern.slf4j.Slf4j;

/**
 * Smart Redis Retry 自动配置让位测试。
 *
 * @author surezzzzzz
 */
@SpringBootTest(classes = {SmartRedisRetryTestApplication.class, SmartRedisRetryAutoConfigurationTest.CustomListener.class})
@Slf4j
class SmartRedisRetryAutoConfigurationTest {

    @Autowired
    private SmartRedisRetryListener listener;

    @Autowired
    private SmartRedisRetryEngine engine;

    @Autowired
    private RetryPolicyValidator retryPolicyValidator;

    @Autowired
    private RetryInfoConvertHelper retryInfoConvertHelper;

    @Test
    void smartRedisRetryComponentsShouldBeRegistered() {
        assertNotNull(retryPolicyValidator, "RetryPolicyValidator 应由 starter 扫描注册");
        assertNotNull(retryInfoConvertHelper, "RetryInfoConvertHelper 应由 starter 扫描注册");
    }

    @Test
    void customListenerShouldOverrideDefault() {
        assertNotNull(listener);
        assertNotSame(NoopSmartRedisRetryListener.class, listener.getClass(),
                "自定义 Listener Bean 应让位默认实现");
    }

    @Test
    void engineShouldBeRegistered() {
        assertNotNull(engine);
        log.info("SmartRedisRetryEngine Bean 注册验证通过，实现类={}", engine.getClass().getSimpleName());
    }

    @Test
    void invalidRedisFailureStrategyShouldThrowAtBeanCreation() {
        SmartRedisRetryAutoConfiguration config = new SmartRedisRetryAutoConfiguration();
        SmartRedisRetryProperties props = new SmartRedisRetryProperties();
        props.getGuard().setRedisFailureStrategy("INVALID");
        assertThrows(RetryValidationException.class, () ->
                config.smartRedisRetryEngine(null, props, null, null, null, null, null, null, null,
                        new RetryPolicyValidator(), null));
        log.info("非法 redisFailureStrategy 启动校验测试通过");
    }

    @Test
    void negativeRecordTtlSecondsShouldThrowAtBeanCreation() {
        SmartRedisRetryAutoConfiguration config = new SmartRedisRetryAutoConfiguration();
        SmartRedisRetryProperties props = new SmartRedisRetryProperties();
        props.getRedis().setRecordTtlSeconds(-1L);
        assertThrows(RetryValidationException.class, () ->
                config.smartRedisRetryEngine(null, props, null, null, null, null, null, null, null,
                        new RetryPolicyValidator(), null));
        log.info("非法 recordTtlSeconds 启动校验测试通过");
    }

    @Test
    void overflowRecordTtlSecondsShouldThrowAtBeanCreation() {
        SmartRedisRetryAutoConfiguration config = new SmartRedisRetryAutoConfiguration();
        SmartRedisRetryProperties props = new SmartRedisRetryProperties();
        props.getRedis().setRecordTtlSeconds(Long.MAX_VALUE);
        assertThrows(RetryValidationException.class, () ->
                config.smartRedisRetryEngine(null, props, null, null, null, null, null, null, null,
                        new RetryPolicyValidator(), null));
        log.info("超大 recordTtlSeconds 启动校验测试通过");
    }

    @Test
    void invalidScanCountShouldThrowAtBeanCreation() {
        SmartRedisRetryAutoConfiguration config = new SmartRedisRetryAutoConfiguration();
        SmartRedisRetryProperties props = new SmartRedisRetryProperties();
        props.getRedis().setScanCount(0);
        assertThrows(RetryValidationException.class, () ->
                config.smartRedisRetryEngine(null, props, null, null, null, null, null, null, null,
                        new RetryPolicyValidator(), null));
        log.info("非法 scanCount 启动校验测试通过");
    }

    @Test
    void invalidGuardLimitsShouldThrowAtBeanCreation() {
        SmartRedisRetryAutoConfiguration config = new SmartRedisRetryAutoConfiguration();
        SmartRedisRetryProperties retryKeyProps = new SmartRedisRetryProperties();
        retryKeyProps.getGuard().setMaxRetryKeyLength(0);
        assertThrows(RetryValidationException.class, () ->
                config.smartRedisRetryEngine(null, retryKeyProps, null, null, null, null, null, null, null,
                        new RetryPolicyValidator(), null));
        SmartRedisRetryProperties contextProps = new SmartRedisRetryProperties();
        contextProps.getGuard().setMaxContextJsonLength(0);
        assertThrows(RetryValidationException.class, () ->
                config.smartRedisRetryEngine(null, contextProps, null, null, null, null, null, null, null,
                        new RetryPolicyValidator(), null));
        log.info("非法防护阈值启动校验测试通过");
    }

    @Test
    void invalidDefaultAndScenePoliciesShouldThrowAtBeanCreation() {
        SmartRedisRetryAutoConfiguration config = new SmartRedisRetryAutoConfiguration();
        SmartRedisRetryProperties defaultPolicyProps = new SmartRedisRetryProperties();
        defaultPolicyProps.getPolicy().setDefaultPolicy(null);
        assertThrows(RetryValidationException.class, () ->
                config.smartRedisRetryEngine(null, defaultPolicyProps, null, null, null, null, null, null, null,
                        new RetryPolicyValidator(), null));
        SmartRedisRetryProperties scenePolicyProps = new SmartRedisRetryProperties();
        scenePolicyProps.getPolicy().getScene().put("test-compensation", null);
        assertThrows(RetryValidationException.class, () ->
                config.smartRedisRetryEngine(null, scenePolicyProps, null, null, null, null, null, null, null,
                        new RetryPolicyValidator(), null));
        log.info("非法默认和场景策略启动校验测试通过");
    }

    @TestConfiguration
    static class CustomListener {
        @Bean
        public SmartRedisRetryListener customListener() {
            return new SmartRedisRetryListener() {
                @Override
                public void onDecision(String retryType, String retryKey, io.github.surezzzzzz.sdk.retry.redis.smart.model.RetryDecision decision) {
                }

                @Override
                public void onRecord(io.github.surezzzzzz.sdk.retry.redis.smart.model.RetryFailure failure, io.github.surezzzzzz.sdk.retry.redis.smart.model.RetryInfo retryInfo) {
                }

                @Override
                public void onClear(String retryType, String retryKey, io.github.surezzzzzz.sdk.retry.redis.smart.model.RetryInfo retryInfo) {
                }

                @Override
                public void onExhausted(String retryType, String retryKey, io.github.surezzzzzz.sdk.retry.redis.smart.model.RetryInfo retryInfo) {
                }
            };
        }
    }
}
