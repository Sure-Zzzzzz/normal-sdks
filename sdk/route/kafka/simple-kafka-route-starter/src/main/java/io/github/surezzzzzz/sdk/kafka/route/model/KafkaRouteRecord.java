package io.github.surezzzzzz.sdk.kafka.route.model;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import org.apache.kafka.common.header.Header;

/**
 * Kafka route 发送记录
 *
 * @author surezzzzzz
 */
@Getter
@Setter
@ToString(exclude = {"key", "value", "headers"})
public class KafkaRouteRecord<K, V> {

    /**
     * 显式路由 key；为 null 时按 topic 路由
     */
    private String routeKey;

    /**
     * 实际发送 topic
     */
    private String topic;

    private Integer partition;
    private Long timestamp;
    private K key;
    private V value;
    private Iterable<Header> headers;
}
