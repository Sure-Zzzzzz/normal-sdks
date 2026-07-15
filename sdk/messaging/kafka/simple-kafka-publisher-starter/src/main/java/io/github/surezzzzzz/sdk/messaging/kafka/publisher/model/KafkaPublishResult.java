package io.github.surezzzzzz.sdk.messaging.kafka.publisher.model;

import lombok.*;

/**
 * Kafka 发布结果
 *
 * @author surezzzzzz
 */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@ToString(exclude = {"key"})
public class KafkaPublishResult {

    /**
     * 消息 ID
     */
    private String messageId;

    /**
     * topic
     */
    private String topic;

    /**
     * Kafka record key
     */
    private String key;

    /**
     * 分区
     */
    private Integer partition;

    /**
     * offset
     */
    private Long offset;

    /**
     * broker 时间戳
     */
    private Long timestamp;

    /**
     * 显式 datasource key，仅 publishOn 模式回填
     */
    private String datasourceKey;
}
