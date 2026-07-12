package io.github.surezzzzzz.sdk.kafka.route.diagnostic;

import io.github.surezzzzzz.sdk.kafka.route.model.KafkaRouteBrokerDiagnosticResult;

import java.util.Map;

/**
 * Kafka route 诊断接口
 *
 * @author surezzzzzz
 */
public interface KafkaRouteDiagnostics {

    /**
     * 获取所有 datasource 的诊断结果
     *
     * @return 诊断结果 map
     */
    Map<String, KafkaRouteBrokerDiagnosticResult> getDiagnosticResults();

    /**
     * 获取指定 datasource 的诊断结果
     *
     * @param datasourceKey datasource key
     * @return 诊断结果，不存在时返回 null
     */
    KafkaRouteBrokerDiagnosticResult getDiagnosticResult(String datasourceKey);
}
