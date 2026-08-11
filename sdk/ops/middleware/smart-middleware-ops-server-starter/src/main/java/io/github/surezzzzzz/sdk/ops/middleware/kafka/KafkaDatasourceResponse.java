package io.github.surezzzzzz.sdk.ops.middleware.kafka;

import lombok.Builder;
import lombok.Getter;

/**
 * Kafka 数据源安全诊断投影。
 *
 * @author surezzzzzz
 */
@Getter
@Builder
public class KafkaDatasourceResponse {

    /**
     * 数据源标识。
     */
    private final String datasourceKey;
    /**
     * 诊断状态。
     */
    private final String diagnosticStatus;
    /**
     * 诊断告警或失败原因。
     */
    private final String diagnosticReason;
    /**
     * 可安全展示的集群标识。
     */
    private final String clusterId;
    /**
     * broker 节点数量。
     */
    private final Integer nodeCount;
    /**
     * controller 是否可见。
     */
    private final Boolean controllerVisible;
}
