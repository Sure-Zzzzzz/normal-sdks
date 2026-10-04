package io.github.surezzzzzz.sdk.messaging.kafka.consumer.error;

import io.github.surezzzzzz.sdk.messaging.kafka.consumer.model.KafkaConsumerRecord;

/**
 * 错误处理器 SPI，在 handler 抛异常后同步调用，返回处理决策
 *
 * @param <K> key 类型
 * @param <V> value 类型
 * @author surezzzzzz
 */
public interface KafkaConsumerErrorHandler<K, V> {

    /**
     * 处理 handler 抛出的异常，返回重试或死信决策
     *
     * @param record  消费记录
     * @param cause   handler 抛出的异常
     * @param attempt 当前尝试次数（1-based）
     * @return 处理决策
     */
    ErrorHandlerDecision onError(KafkaConsumerRecord<K, V> record, Exception cause, int attempt);
}
