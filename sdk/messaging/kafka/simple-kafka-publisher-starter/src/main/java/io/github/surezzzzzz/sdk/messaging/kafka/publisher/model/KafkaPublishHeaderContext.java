package io.github.surezzzzzz.sdk.messaging.kafka.publisher.model;

import lombok.Builder;
import lombok.Getter;

import java.util.Map;

/**
 * Kafka 发布 header 自定义上下文
 *
 * @author surezzzzzz
 */
@Getter
@Builder
public class KafkaPublishHeaderContext {

    private String topic;
    private String key;
    private String messageId;
    private String messageType;
    private String traceId;
    private String source;
    private Long publishedAt;
    private Map<String, String> headers;
}
