package io.github.surezzzzzz.sdk.messaging.kafka.consumer.validator;

import io.github.surezzzzzz.sdk.messaging.kafka.consumer.configuration.SimpleKafkaConsumerProperties;
import io.github.surezzzzzz.sdk.messaging.kafka.consumer.constant.ErrorCode;
import io.github.surezzzzzz.sdk.messaging.kafka.consumer.constant.ErrorMessage;
import io.github.surezzzzzz.sdk.messaging.kafka.consumer.constant.SimpleKafkaConsumerConstant;
import io.github.surezzzzzz.sdk.messaging.kafka.consumer.exception.KafkaConsumerConfigurationException;
import io.github.surezzzzzz.sdk.messaging.kafka.consumer.support.KafkaConsumerStringHelper;

import java.util.Locale;


/**
 * 默认 Kafka Consumer Properties 校验器
 *
 * @author surezzzzzz
 */
public class DefaultKafkaConsumerPropertiesValidator implements KafkaConsumerPropertiesValidator {

    @Override
    public void validate(SimpleKafkaConsumerProperties properties) {
        if (properties == null) {
            throw configInvalid(SimpleKafkaConsumerConstant.REASON_BACKOFF_INVALID);
        }
        validateError(properties.getError());
        validateContainer(properties.getContainer());
        validateIdempotency(properties.getIdempotency());
    }

    private void validateError(SimpleKafkaConsumerProperties.ErrorConfig error) {
        if (error == null) {
            throw configInvalid(SimpleKafkaConsumerConstant.REASON_BACKOFF_INVALID);
        }
        if (error.getMaxAttempts() < SimpleKafkaConsumerConstant.BACKOFF_MAX_ATTEMPTS_MIN) {
            throw configInvalid(SimpleKafkaConsumerConstant.REASON_BACKOFF_INVALID);
        }
        if (!Double.isFinite(error.getMultiplier())
                || error.getMultiplier() < SimpleKafkaConsumerConstant.BACKOFF_MULTIPLIER_MIN) {
            throw configInvalid(SimpleKafkaConsumerConstant.REASON_BACKOFF_INVALID);
        }
        if (!Double.isFinite(error.getJitterFactor())
                || error.getJitterFactor() < SimpleKafkaConsumerConstant.BACKOFF_JITTER_MIN
                || error.getJitterFactor() > SimpleKafkaConsumerConstant.BACKOFF_JITTER_MAX) {
            throw configInvalid(SimpleKafkaConsumerConstant.REASON_BACKOFF_INVALID);
        }
        if (error.getInitialIntervalMs() <= SimpleKafkaConsumerConstant.ZERO
                || error.getMaxIntervalMs() <= SimpleKafkaConsumerConstant.ZERO
                || error.getInitialIntervalMs() > error.getMaxIntervalMs()) {
            throw configInvalid(SimpleKafkaConsumerConstant.REASON_BACKOFF_INVALID);
        }
        SimpleKafkaConsumerProperties.DeadLetterConfig deadLetter = error.getDeadLetter();
        if (deadLetter == null || (deadLetter.isEnable()
                && !KafkaConsumerStringHelper.hasText(deadLetter.getSuffix()))) {
            throw configInvalid(SimpleKafkaConsumerConstant.REASON_DEAD_LETTER_INVALID);
        }
    }

    private void validateContainer(SimpleKafkaConsumerProperties.ContainerConfig container) {
        if (container == null) {
            throw configInvalid(SimpleKafkaConsumerConstant.REASON_CONCURRENCY_INVALID);
        }
        if (container.getConcurrency() < SimpleKafkaConsumerConstant.CONCURRENCY_MIN) {
            throw configInvalid(SimpleKafkaConsumerConstant.REASON_CONCURRENCY_INVALID);
        }
        Integer maxPollRecords = container.getMaxPollRecords();
        if (maxPollRecords != null && maxPollRecords <= SimpleKafkaConsumerConstant.ZERO) {
            throw configInvalid(SimpleKafkaConsumerConstant.REASON_MAX_POLL_RECORDS_INVALID);
        }
        if (container.getShutdownAwaitMs() < SimpleKafkaConsumerConstant.ZERO) {
            throw configInvalid(SimpleKafkaConsumerConstant.REASON_CONCURRENCY_INVALID);
        }
        String autoOffsetReset = container.getAutoOffsetReset();
        if (KafkaConsumerStringHelper.hasText(autoOffsetReset)
                && !isValidAutoOffsetReset(autoOffsetReset)) {
            throw configInvalid(SimpleKafkaConsumerConstant.REASON_AUTO_OFFSET_RESET_INVALID);
        }
    }

    private boolean isValidAutoOffsetReset(String value) {
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        return SimpleKafkaConsumerConstant.AUTO_OFFSET_RESET_EARLIEST.equals(normalized)
                || SimpleKafkaConsumerConstant.AUTO_OFFSET_RESET_LATEST.equals(normalized)
                || SimpleKafkaConsumerConstant.AUTO_OFFSET_RESET_NONE.equals(normalized);
    }

    private void validateIdempotency(SimpleKafkaConsumerProperties.IdempotencyConfig idempotency) {
        if (idempotency == null) {
            throw configInvalid(SimpleKafkaConsumerConstant.REASON_IDEMPOTENCY_CONFIG_INVALID);
        }
        if (idempotency.isEnable() && idempotency.getTtlMs() <= SimpleKafkaConsumerConstant.ZERO) {
            throw configInvalid(SimpleKafkaConsumerConstant.REASON_IDEMPOTENCY_TTL_INVALID);
        }
        if (idempotency.isEnable() && idempotency.getLeaseMs() <= SimpleKafkaConsumerConstant.ZERO) {
            throw configInvalid(SimpleKafkaConsumerConstant.REASON_IDEMPOTENCY_LEASE_INVALID);
        }
    }

    private KafkaConsumerConfigurationException configInvalid(String reason) {
        return new KafkaConsumerConfigurationException(ErrorCode.CONFIG_INVALID,
                String.format(ErrorMessage.CONFIG_INVALID, reason));
    }
}
