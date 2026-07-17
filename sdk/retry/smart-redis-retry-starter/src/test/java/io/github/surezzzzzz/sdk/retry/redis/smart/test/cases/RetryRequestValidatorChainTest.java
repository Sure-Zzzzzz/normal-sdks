package io.github.surezzzzzz.sdk.retry.redis.smart.test.cases;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.surezzzzzz.sdk.retry.redis.smart.configuration.SmartRedisRetryProperties;
import io.github.surezzzzzz.sdk.retry.redis.smart.exception.RetryValidationException;
import io.github.surezzzzzz.sdk.retry.redis.smart.model.RetryFailure;
import io.github.surezzzzzz.sdk.retry.redis.smart.model.RetryScanRequest;
import io.github.surezzzzzz.sdk.retry.redis.smart.serializer.JacksonRetryContextSerializer;
import io.github.surezzzzzz.sdk.retry.redis.smart.validator.RetryContextValidator;
import io.github.surezzzzzz.sdk.retry.redis.smart.validator.RetryFailureValidator;
import io.github.surezzzzzz.sdk.retry.redis.smart.validator.RetryPolicyValidator;
import io.github.surezzzzzz.sdk.retry.redis.smart.validator.RetryRequestValidator;
import io.github.surezzzzzz.sdk.retry.redis.smart.validator.RetryRequestValidatorChain;
import io.github.surezzzzzz.sdk.retry.redis.smart.validator.RetryScanRequestValidator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import lombok.extern.slf4j.Slf4j;

/**
 * {@link RetryRequestValidatorChain} 单元测试
 *
 * @author surezzzzzz
 */
@Slf4j
class RetryRequestValidatorChainTest {

    private SmartRedisRetryProperties properties;
    private RetryRequestValidatorChain chain;

    @BeforeEach
    void setUp() {
        properties = new SmartRedisRetryProperties();
        properties.getGuard().setMaxRetryKeyLength(16);
        properties.getGuard().setMaxContextJsonLength(32);
        properties.getRedis().setScanCount(50);
        RetryPolicyValidator retryPolicyValidator = new RetryPolicyValidator();
        chain = new RetryRequestValidatorChain(Arrays.<RetryRequestValidator<?>>asList(
                new RetryFailureValidator(properties, retryPolicyValidator),
                new RetryContextValidator(properties, new JacksonRetryContextSerializer(new ObjectMapper())),
                new RetryScanRequestValidator(properties)));
        log.info("初始化 RetryRequestValidatorChain 测试");
    }

    @Test
    void shouldAcceptValidFailure() {
        assertDoesNotThrow(() -> chain.validate(RetryFailure.builder()
                .retryType("test-compensation")
                .retryKey("test-key")
                .build()));
    }

    @Test
    void shouldRejectBlankRetryType() {
        assertThrows(RetryValidationException.class, () -> chain.validate(RetryFailure.builder()
                .retryType(" ")
                .retryKey("test-key")
                .build()));
    }

    @Test
    void shouldRejectBlankRetryKey() {
        assertThrows(RetryValidationException.class, () -> chain.validate(RetryFailure.builder()
                .retryType("test-compensation")
                .retryKey(" ")
                .build()));
    }

    @Test
    void shouldRejectTooLongRetryKey() {
        assertThrows(RetryValidationException.class, () -> chain.validate(RetryFailure.builder()
                .retryType("test-compensation")
                .retryKey("12345678901234567")
                .build()));
    }

    @Test
    void shouldRejectTooLargeContext() {
        Map<String, Object> context = new HashMap<String, Object>();
        context.put("extraField", "1234567890123456789012345678901234567890");
        assertThrows(RetryValidationException.class, () -> chain.validate(RetryFailure.builder()
                .retryType("test-compensation")
                .retryKey("test-key")
                .context(context)
                .build()));
    }

    @Test
    void shouldAcceptValidScanRequest() {
        assertDoesNotThrow(() -> chain.validate(RetryScanRequest.builder()
                .routeKey("sure-smart-redis-retry:retry:test-compensation:default::mock-route")
                .retryType("test-compensation")
                .count(50)
                .build()));
    }

    @Test
    void shouldRejectBlankRouteKey() {
        assertThrows(RetryValidationException.class, () -> chain.validate(RetryScanRequest.builder()
                .routeKey(" ")
                .retryType("test-compensation")
                .count(50)
                .build()));
    }

    @Test
    void shouldRejectNonPositiveScanCount() {
        assertThrows(RetryValidationException.class, () -> chain.validate(RetryScanRequest.builder()
                .routeKey("sure-smart-redis-retry:retry:test-compensation:default::mock-route")
                .retryType("test-compensation")
                .count(0)
                .build()));
    }

    @Test
    void shouldRejectScanCountLargerThanConfiguredLimit() {
        assertThrows(RetryValidationException.class, () -> chain.validate(RetryScanRequest.builder()
                .routeKey("sure-smart-redis-retry:retry:test-compensation:default::mock-route")
                .retryType("test-compensation")
                .count(51)
                .build()));
    }

    @Test
    void shouldAcceptOpaqueClusterCursor() {
        assertDoesNotThrow(() -> chain.validate(RetryScanRequest.builder()
                .routeKey("sure-smart-redis-retry:retry:test-compensation:default::mock-route")
                .retryType("test-compensation")
                .cursor("1:42")
                .count(50)
                .build()));
        log.info("Cluster 不透明游标校验通过");
    }

    @Test
    void shouldRejectMalformedScanCursor() {
        assertThrows(RetryValidationException.class, () -> chain.validate(RetryScanRequest.builder()
                .routeKey("sure-smart-redis-retry:retry:test-compensation:default::mock-route")
                .retryType("test-compensation")
                .cursor("invalid-cursor")
                .count(50)
                .build()));
        log.info("非法扫描游标校验通过");
    }
}
