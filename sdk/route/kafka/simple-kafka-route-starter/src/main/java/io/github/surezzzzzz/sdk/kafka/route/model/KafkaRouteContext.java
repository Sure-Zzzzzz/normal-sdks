package io.github.surezzzzzz.sdk.kafka.route.model;

import io.github.surezzzzzz.sdk.kafka.route.constant.KafkaRouteInputType;
import io.github.surezzzzzz.sdk.kafka.route.constant.KafkaRouteOperationType;
import lombok.Builder;
import lombok.Getter;
import lombok.ToString;

/**
 * Kafka 路由上下文
 *
 * @author surezzzzzz
 */
@Getter
@Builder
@ToString(exclude = {"topic", "routeKey", "routeInput", "datasourceKey"})
public class KafkaRouteContext {

    /**
     * 实际发送 topic
     */
    private final String topic;

    /**
     * 显式 route key
     */
    private final String routeKey;

    /**
     * 参与规则匹配的输入
     */
    private final String routeInput;

    /**
     * 路由输入类型
     */
    private final KafkaRouteInputType inputType;

    /**
     * 操作类型
     */
    private final KafkaRouteOperationType operationType;

    /**
     * 显式 datasource
     */
    private final String datasourceKey;
}
