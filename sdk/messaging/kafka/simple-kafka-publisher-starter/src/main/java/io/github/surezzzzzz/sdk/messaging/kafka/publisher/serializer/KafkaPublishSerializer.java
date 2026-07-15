package io.github.surezzzzzz.sdk.messaging.kafka.publisher.serializer;

import io.github.surezzzzzz.sdk.messaging.kafka.publisher.model.KafkaPublishSerializeContext;

/**
 * Kafka 发布序列化器
 *
 * @author surezzzzzz
 */
public interface KafkaPublishSerializer {

    /**
     * 序列化发布内容
     *
     * @param context 序列化上下文
     * @return 序列化后的字符串
     */
    String serialize(KafkaPublishSerializeContext context);
}
