package io.github.surezzzzzz.sdk.messaging.kafka.publisher.model;

import lombok.*;

import java.util.Map;

/**
 * Kafka 发布消息
 *
 * @param <T> payload 类型
 * @author surezzzzzz
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@ToString(exclude = {"key", "payload", "headers", "attributes"})
public class KafkaPublishMessage<T> {

    /**
     * 实际发送 topic
     */
    private String topic;

    /**
     * Kafka record key
     */
    private String key;

    /**
     * 路由 key
     */
    private String routeKey;

    /**
     * 显式 datasource key
     */
    private String datasourceKey;

    /**
     * 分区
     */
    private Integer partition;

    /**
     * 时间戳
     */
    private Long timestamp;

    /**
     * 消息 ID
     */
    private String messageId;

    /**
     * 消息类型
     */
    private String messageType;

    /**
     * 消息 payload
     */
    private T payload;

    /**
     * 业务 header
     */
    private Map<String, String> headers;

    /**
     * envelope 扩展属性
     */
    private Map<String, Object> attributes;

    /**
     * 单条消息 envelope 开关，null 表示跟随配置
     */
    private Boolean envelopeEnabled;
}
