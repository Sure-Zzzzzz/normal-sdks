package io.github.surezzzzzz.sdk.kafka.route.model;

/**
 * Kafka route 诊断状态
 *
 * @author surezzzzzz
 */
public enum KafkaRouteDiagnosticStatus {

    /**
     * 探测成功
     */
    SUCCESS,

    /**
     * 探测成功但存在能力告警
     */
    WARN,

    /**
     * broker 不可达或探测失败
     */
    FAILED;

    @Override
    public String toString() {
        return name();
    }
}
