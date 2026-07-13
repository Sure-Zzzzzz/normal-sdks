package io.github.surezzzzzz.sdk.kafka.route.model;

import lombok.Builder;
import lombok.Getter;
import lombok.ToString;

/**
 * Kafka broker 诊断结果
 *
 * @author surezzzzzz
 */
@Getter
@Builder
@ToString(exclude = "failureReason")
public class KafkaRouteBrokerDiagnosticResult {

    /**
     * datasource key
     */
    private final String datasourceKey;

    /**
     * 诊断状态
     */
    private final KafkaRouteDiagnosticStatus status;

    /**
     * Kafka cluster id
     */
    private final String clusterId;

    /**
     * broker 节点数量
     */
    private final int nodeCount;

    /**
     * controller 是否可见
     */
    private final boolean controllerVisible;

    /**
     * 事务能力
     */
    private final KafkaRouteBrokerCapability transactionSupported;

    /**
     * 幂等 producer 能力
     */
    private final KafkaRouteBrokerCapability idempotenceSupported;

    /**
     * zstd 压缩能力
     */
    private final KafkaRouteBrokerCapability zstdSupported;

    /**
     * 基础 Admin API 探测能力
     */
    private final KafkaRouteBrokerCapability adminApiLevel;

    /**
     * 失败原因短消息
     */
    private final String failureReason;
}
