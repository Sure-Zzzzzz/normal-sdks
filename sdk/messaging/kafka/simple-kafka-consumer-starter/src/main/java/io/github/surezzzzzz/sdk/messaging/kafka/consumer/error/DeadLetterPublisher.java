package io.github.surezzzzzz.sdk.messaging.kafka.consumer.error;

import io.github.surezzzzzz.sdk.messaging.kafka.consumer.model.KafkaConsumerRecord;

/**
 * 死信投递 SPI，使用 route-starter 的 KafkaTemplate 投递死信，不依赖 publisher-starter
 *
 * @author surezzzzzz
 */
public interface DeadLetterPublisher {

    /**
     * 投递死信
     *
     * @param record    原始消费记录
     * @param cause     handler 抛出的异常
     * @param attempt   尝试次数
     * @param errorCode 错误码
     * @return true 投递成功；false 投递失败
     */
    boolean publish(KafkaConsumerRecord<?, ?> record, Exception cause, int attempt, String errorCode);
}
