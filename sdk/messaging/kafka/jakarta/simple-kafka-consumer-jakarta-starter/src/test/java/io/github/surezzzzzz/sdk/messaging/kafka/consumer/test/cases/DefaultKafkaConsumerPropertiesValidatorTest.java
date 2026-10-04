package io.github.surezzzzzz.sdk.messaging.kafka.consumer.test.cases;

import io.github.surezzzzzz.sdk.messaging.kafka.consumer.configuration.SimpleKafkaConsumerProperties;
import io.github.surezzzzzz.sdk.messaging.kafka.consumer.constant.ErrorCode;
import io.github.surezzzzzz.sdk.messaging.kafka.consumer.exception.KafkaConsumerConfigurationException;
import io.github.surezzzzzz.sdk.messaging.kafka.consumer.validator.DefaultKafkaConsumerPropertiesValidator;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 消费配置校验器测试。
 *
 * @author surezzzzzz
 */
@Slf4j
public class DefaultKafkaConsumerPropertiesValidatorTest {

    private final DefaultKafkaConsumerPropertiesValidator validator = new DefaultKafkaConsumerPropertiesValidator();

    @Test
    public void testNullContainerOverridesRemainValidForRouteInheritance() {
        SimpleKafkaConsumerProperties properties = new SimpleKafkaConsumerProperties();
        properties.getContainer().setAutoOffsetReset(null);
        properties.getContainer().setEnableAutoCommit(null);
        properties.getContainer().setMaxPollRecords(null);

        log.info("验证可空容器覆盖配置可继承 route datasource");
        assertDoesNotThrow(() -> validator.validate(properties), "可空覆盖配置应允许从 route 继承");
    }

    @Test
    public void testOffsetResetAllowsCaseAndWhitespace() {
        SimpleKafkaConsumerProperties properties = new SimpleKafkaConsumerProperties();
        properties.getContainer().setAutoOffsetReset(" EARLIEST ");

        log.info("验证 auto-offset-reset 大小写与空白兼容");
        assertDoesNotThrow(() -> validator.validate(properties), "合法偏移策略应忽略大小写与首尾空白");
    }

    @Test
    public void testRejectsNonFiniteBackoffParameters() {
        assertBackoffInvalid(Double.NaN, 0.0D);
        assertBackoffInvalid(Double.POSITIVE_INFINITY, 0.0D);
        assertBackoffInvalid(1.0D, Double.NaN);
        assertBackoffInvalid(1.0D, Double.NEGATIVE_INFINITY);
    }

    @Test
    public void testRejectsMissingIdempotencyConfiguration() {
        SimpleKafkaConsumerProperties properties = new SimpleKafkaConsumerProperties();
        properties.setIdempotency(null);

        KafkaConsumerConfigurationException exception = assertThrows(KafkaConsumerConfigurationException.class,
                () -> validator.validate(properties));
        log.info("缺失幂等配置错误码={}，消息={}", exception.getErrorCode(), exception.getMessage());

        assertEquals(ErrorCode.CONFIG_INVALID, exception.getErrorCode());
        assertEquals("消费配置或注册非法：idempotency-config-invalid", exception.getMessage());
    }

    @Test
    public void testRejectsMissingDeadLetterConfiguration() {
        SimpleKafkaConsumerProperties properties = new SimpleKafkaConsumerProperties();
        properties.getError().setDeadLetter(null);

        KafkaConsumerConfigurationException exception = assertThrows(KafkaConsumerConfigurationException.class,
                () -> validator.validate(properties));
        log.info("缺失死信配置错误码={}，消息={}", exception.getErrorCode(), exception.getMessage());

        assertEquals(ErrorCode.CONFIG_INVALID, exception.getErrorCode());
        assertEquals("消费配置或注册非法：dead-letter-invalid", exception.getMessage());
    }

    @Test
    public void testRejectsNonPositiveIdempotencyLease() {
        SimpleKafkaConsumerProperties properties = new SimpleKafkaConsumerProperties();
        properties.getIdempotency().setEnable(true);
        properties.getIdempotency().setLeaseMs(0L);

        KafkaConsumerConfigurationException exception = assertThrows(KafkaConsumerConfigurationException.class,
                () -> validator.validate(properties));
        log.info("非法幂等租约错误码={}，消息={}", exception.getErrorCode(), exception.getMessage());

        assertEquals(ErrorCode.CONFIG_INVALID, exception.getErrorCode());
        assertEquals("消费配置或注册非法：idempotency-lease-invalid", exception.getMessage());
    }

    @Test
    public void testRejectsInvalidMaxPollRecordsWhenExplicitlyConfigured() {
        SimpleKafkaConsumerProperties properties = new SimpleKafkaConsumerProperties();
        properties.getContainer().setMaxPollRecords(0);

        KafkaConsumerConfigurationException exception = assertThrows(KafkaConsumerConfigurationException.class,
                () -> validator.validate(properties));
        log.info("非法 max-poll-records 错误码：{}", exception.getErrorCode());
        assertEquals(ErrorCode.CONFIG_INVALID, exception.getErrorCode());
    }

    private void assertBackoffInvalid(double multiplier, double jitterFactor) {
        SimpleKafkaConsumerProperties properties = new SimpleKafkaConsumerProperties();
        properties.getError().setMultiplier(multiplier);
        properties.getError().setJitterFactor(jitterFactor);

        KafkaConsumerConfigurationException exception = assertThrows(KafkaConsumerConfigurationException.class,
                () -> validator.validate(properties));
        log.info("非法退避参数 multiplier={}，jitterFactor={}，错误码={}，消息={}", multiplier, jitterFactor,
                exception.getErrorCode(), exception.getMessage());
        assertEquals(ErrorCode.CONFIG_INVALID, exception.getErrorCode());
        assertEquals("消费配置或注册非法：backoff-invalid", exception.getMessage());
    }

    @Test
    public void testRejectsInvalidOffsetReset() {
        SimpleKafkaConsumerProperties properties = new SimpleKafkaConsumerProperties();
        properties.getContainer().setAutoOffsetReset("mock-invalid");

        KafkaConsumerConfigurationException exception = assertThrows(KafkaConsumerConfigurationException.class,
                () -> validator.validate(properties));
        log.info("非法 auto-offset-reset 错误码：{}", exception.getErrorCode());
        assertEquals(ErrorCode.CONFIG_INVALID, exception.getErrorCode());
    }
}
