package io.github.surezzzzzz.sdk.messaging.kafka.outbox.management.constant;

/**
 * Management 错误消息。
 *
 * @author surezzzzzz
 */
public final class ErrorMessage {

    public static final String CONFIGURATION_INVALID = "Kafka Outbox Management 配置无效：%s";
    public static final String PERSISTENCE_FAILED = "Kafka Outbox Management 数据访问失败";
    public static final String RECORD_NOT_FOUND = "Outbox 记录不存在";
    public static final String RECORD_STATE_CONFLICT = "Outbox 记录当前状态不允许该操作";
    public static final String REQUEST_INVALID = "页面请求参数无效";

    private ErrorMessage() {
        throw new UnsupportedOperationException("常量类不能实例化");
    }
}
