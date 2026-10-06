package io.github.surezzzzzz.sdk.messaging.kafka.outbox.retry;

import io.github.surezzzzzz.sdk.messaging.kafka.outbox.configuration.SimpleKafkaOutboxProperties;
import io.github.surezzzzzz.sdk.messaging.kafka.outbox.constant.SimpleKafkaOutboxConstant;
import io.github.surezzzzzz.sdk.messaging.kafka.outbox.model.OutboxRetryContext;
import io.github.surezzzzzz.sdk.messaging.kafka.publisher.exception.KafkaPublishException;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.TimeoutException;

/**
 * 默认 Kafka Outbox 指数退避策略
 *
 * @author surezzzzzz
 */
public class DefaultKafkaOutboxRetryPolicy implements KafkaOutboxRetryPolicy {

    /**
     * publisher 确定性错误码集合：命中即不可重试，直接进入 POISON
     */
    private static final Set<String> DETERMINISTIC_ERROR_CODES = new HashSet<>(Arrays.asList(
            io.github.surezzzzzz.sdk.messaging.kafka.publisher.constant.ErrorCode.KAFKA_PUBLISHER_002,
            io.github.surezzzzzz.sdk.messaging.kafka.publisher.constant.ErrorCode.KAFKA_PUBLISHER_003,
            io.github.surezzzzzz.sdk.messaging.kafka.publisher.constant.ErrorCode.KAFKA_PUBLISHER_004,
            io.github.surezzzzzz.sdk.messaging.kafka.publisher.constant.ErrorCode.KAFKA_PUBLISHER_005,
            io.github.surezzzzzz.sdk.messaging.kafka.publisher.constant.ErrorCode.KAFKA_PUBLISHER_006,
            io.github.surezzzzzz.sdk.messaging.kafka.publisher.constant.ErrorCode.KAFKA_PUBLISHER_009,
            io.github.surezzzzzz.sdk.messaging.kafka.publisher.constant.ErrorCode.KAFKA_PUBLISHER_010));

    private final SimpleKafkaOutboxProperties.RetryConfig properties;
    private final KafkaOutboxJitterGenerator jitterGenerator;

    /**
     * 创建默认重试策略
     *
     * @param properties      重试配置
     * @param jitterGenerator 抖动生成器
     */
    public DefaultKafkaOutboxRetryPolicy(SimpleKafkaOutboxProperties.RetryConfig properties,
                                         KafkaOutboxJitterGenerator jitterGenerator) {
        this.properties = properties;
        this.jitterGenerator = jitterGenerator;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean isRetryable(OutboxRetryContext context, Throwable cause) {
        if (cause instanceof TimeoutException) {
            return true;
        }
        if (cause instanceof KafkaPublishException) {
            return !DETERMINISTIC_ERROR_CODES.contains(((KafkaPublishException) cause).getErrorCode());
        }
        return true;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public long nextDelayMs(OutboxRetryContext context) {
        int attempt = context == null || context.getAttempt() == null
                ? SimpleKafkaOutboxConstant.ONE : context.getAttempt();
        int exponent = Math.max(SimpleKafkaOutboxConstant.ZERO,
                attempt - SimpleKafkaOutboxConstant.ONE);
        double calculated = properties.getInitialIntervalMs();
        for (int index = SimpleKafkaOutboxConstant.ZERO; index < exponent; index++) {
            if (calculated >= properties.getMaxIntervalMs() / properties.getMultiplier()) {
                calculated = properties.getMaxIntervalMs();
                break;
            }
            calculated *= properties.getMultiplier();
        }
        calculated = Math.min(calculated, properties.getMaxIntervalMs());
        double random = jitterGenerator.nextDouble();
        double normalized = Math.max(SimpleKafkaOutboxConstant.ZERO_DOUBLE,
                Math.min(SimpleKafkaOutboxConstant.ONE_DOUBLE, random));
        double factor = SimpleKafkaOutboxConstant.ONE_DOUBLE - properties.getJitterFactor()
                + normalized * properties.getJitterFactor() * properties.getMultiplier();
        double jittered = Math.min(properties.getMaxIntervalMs(), Math.max(SimpleKafkaOutboxConstant.ZERO_DOUBLE,
                calculated * factor));
        return (long) jittered;
    }
}
