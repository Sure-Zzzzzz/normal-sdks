package io.github.surezzzzzz.sdk.messaging.kafka.publisher.model;

import lombok.Builder;
import lombok.Getter;

/**
 * Kafka 发布序列化上下文
 *
 * @author surezzzzzz
 */
@Getter
@Builder
public class KafkaPublishSerializeContext {

    private String topic;
    private String messageId;
    private String messageType;
    private Object payload;
    private KafkaPublishEnvelope<?> envelope;
    private boolean envelopeEnabled;
}
