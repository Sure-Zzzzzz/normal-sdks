package io.github.surezzzzzz.sdk.messaging.kafka.outbox.configuration;

import io.github.surezzzzzz.sdk.messaging.kafka.outbox.annotation.SimpleKafkaOutboxComponent;
import io.github.surezzzzzz.sdk.messaging.kafka.outbox.constant.ErrorCode;
import io.github.surezzzzzz.sdk.messaging.kafka.outbox.constant.ErrorMessage;
import io.github.surezzzzzz.sdk.messaging.kafka.outbox.constant.SimpleKafkaOutboxConstant;
import io.github.surezzzzzz.sdk.messaging.kafka.outbox.exception.KafkaOutboxConfigurationException;
import io.github.surezzzzzz.sdk.messaging.kafka.outbox.support.KafkaOutboxStringHelper;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;

/**
 * Kafka Outbox 配置校验器
 *
 * @author surezzzzzz
 */
@SimpleKafkaOutboxComponent
@ConditionalOnMissingBean(KafkaOutboxPropertiesValidator.class)
public class KafkaOutboxPropertiesValidator {

    /**
     * 校验全部配置
     *
     * @param properties 配置
     */
    public void validate(SimpleKafkaOutboxProperties properties) {
        if (properties == null) fail(SimpleKafkaOutboxConstant.REASON_PROPERTIES_EMPTY);
        String tableName = properties.getTableName();
        if (!KafkaOutboxStringHelper.hasText(tableName)
                || tableName.length() > SimpleKafkaOutboxConstant.MAX_TABLE_NAME_LENGTH
                || !tableName.matches(SimpleKafkaOutboxConstant.TABLE_NAME_PATTERN)) {
            fail(SimpleKafkaOutboxConstant.REASON_TABLE_NAME_INVALID);
        }
        SimpleKafkaOutboxProperties.WorkerConfig worker = properties.getWorker();
        if (worker == null || worker.getConcurrency() <= SimpleKafkaOutboxConstant.ZERO
                || worker.getBatchSize() <= SimpleKafkaOutboxConstant.ZERO
                || worker.getScanIntervalMs() <= SimpleKafkaOutboxConstant.ZERO_LONG
                || worker.getIdleIntervalMs() <= SimpleKafkaOutboxConstant.ZERO_LONG
                || worker.getLeaseMs() <= SimpleKafkaOutboxConstant.ZERO_LONG) {
            fail(SimpleKafkaOutboxConstant.REASON_WORKER_CONFIG_INVALID);
        }
        if (worker.getLeaseMs() > Long.MAX_VALUE / SimpleKafkaOutboxConstant.MILLIS_TO_MICROS) {
            fail(SimpleKafkaOutboxConstant.REASON_LEASE_OVERFLOW);
        }
        if (properties.getSend() == null || properties.getSend().getTimeoutMs() <= SimpleKafkaOutboxConstant.ZERO_LONG
                || properties.getSend().getTimeoutMs() >= worker.getLeaseMs()) {
            fail(SimpleKafkaOutboxConstant.REASON_SEND_TIMEOUT_INVALID);
        }
        if (worker.getShutdownAwaitMs() <= SimpleKafkaOutboxConstant.ZERO_LONG
                || worker.getShutdownAwaitMs() > worker.getLeaseMs()) {
            fail(SimpleKafkaOutboxConstant.REASON_SHUTDOWN_TIMEOUT_INVALID);
        }
        SimpleKafkaOutboxProperties.RetryConfig retry = properties.getRetry();
        if (retry == null || retry.getMaxAttempts() <= SimpleKafkaOutboxConstant.ZERO
                || retry.getInitialIntervalMs() <= SimpleKafkaOutboxConstant.ZERO_LONG
                || retry.getMultiplier() < SimpleKafkaOutboxConstant.ONE_DOUBLE
                || retry.getMaxIntervalMs() <= SimpleKafkaOutboxConstant.ZERO_LONG
                || retry.getInitialIntervalMs() > retry.getMaxIntervalMs()
                || Double.isNaN(retry.getMultiplier()) || Double.isInfinite(retry.getMultiplier())
                || Double.isNaN(retry.getJitterFactor()) || Double.isInfinite(retry.getJitterFactor())
                || retry.getJitterFactor() < SimpleKafkaOutboxConstant.ZERO_DOUBLE
                || retry.getJitterFactor() > SimpleKafkaOutboxConstant.ONE_DOUBLE) {
            fail(SimpleKafkaOutboxConstant.REASON_RETRY_CONFIG_INVALID);
        }
        SimpleKafkaOutboxProperties.CleanupConfig cleanup = properties.getCleanup();
        if (cleanup == null || cleanup.getRetentionDays() <= SimpleKafkaOutboxConstant.ZERO
                || cleanup.getBatchSize() <= SimpleKafkaOutboxConstant.ZERO
                || cleanup.getIntervalMs() <= SimpleKafkaOutboxConstant.ZERO_LONG
                || cleanup.getRetentionDays() > Long.MAX_VALUE / SimpleKafkaOutboxConstant.DAY_TO_MILLIS) {
            fail(SimpleKafkaOutboxConstant.REASON_CLEANUP_CONFIG_INVALID);
        }
    }

    /**
     * 抛出配置非法异常，统一错误码 KAFKA_OUTBOX_001。
     */
    private void fail(String reason) {
        throw new KafkaOutboxConfigurationException(ErrorCode.KAFKA_OUTBOX_001,
                String.format(ErrorMessage.KAFKA_OUTBOX_001, reason));
    }
}
