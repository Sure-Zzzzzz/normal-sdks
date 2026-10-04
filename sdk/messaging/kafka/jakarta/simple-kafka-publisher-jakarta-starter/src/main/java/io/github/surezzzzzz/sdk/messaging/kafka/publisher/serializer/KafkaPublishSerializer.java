package io.github.surezzzzzz.sdk.messaging.kafka.publisher.serializer;

import io.github.surezzzzzz.sdk.messaging.kafka.publisher.exception.KafkaPublishException;
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
     * <p>返回值不得为 null，空字符串是合法 Kafka value。实现抛出的非 KafkaPublishException
     * 运行时异常会由发布引擎统一包装为 KAFKA_PUBLISHER_006。
     *
     * @param context 序列化上下文
     * @return 非 null 的序列化字符串
     * @throws KafkaPublishException 序列化失败
     */
    String serialize(KafkaPublishSerializeContext context);
}
