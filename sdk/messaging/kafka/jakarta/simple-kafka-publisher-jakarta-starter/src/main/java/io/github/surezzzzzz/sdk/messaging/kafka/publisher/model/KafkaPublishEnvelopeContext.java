package io.github.surezzzzzz.sdk.messaging.kafka.publisher.model;

import lombok.Builder;
import lombok.Getter;
import lombok.ToString;

import java.util.Map;

/**
 * Kafka 发布 envelope 自定义上下文
 *
 * @author surezzzzzz
 */
@Getter
@Builder
@ToString(exclude = {"attributes"})
public class KafkaPublishEnvelopeContext {

    /**
     * 消息 ID
     */
    private String messageId;

    /**
     * 消息类型
     */
    private String messageType;

    /**
     * 来源应用
     */
    private String source;

    /**
     * 发布时间戳
     */
    private Long timestamp;

    /**
     * trace ID
     */
    private String traceId;

    /**
     * 本次 envelope 的可变扩展属性
     */
    private Map<String, Object> attributes;
}
