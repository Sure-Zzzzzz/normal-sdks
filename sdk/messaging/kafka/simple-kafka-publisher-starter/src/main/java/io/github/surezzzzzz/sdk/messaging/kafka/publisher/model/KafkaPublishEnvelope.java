package io.github.surezzzzzz.sdk.messaging.kafka.publisher.model;

import lombok.*;

import java.util.Map;

/**
 * Kafka 发布 envelope
 *
 * @param <T> payload 类型
 * @author surezzzzzz
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@ToString(exclude = {"payload", "attributes"})
public class KafkaPublishEnvelope<T> {

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
     * 消息 payload
     */
    private T payload;

    /**
     * 扩展属性
     */
    private Map<String, Object> attributes;
}
