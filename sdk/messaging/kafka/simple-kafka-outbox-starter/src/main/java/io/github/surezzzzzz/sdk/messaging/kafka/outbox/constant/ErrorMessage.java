package io.github.surezzzzzz.sdk.messaging.kafka.outbox.constant;

/**
 * Kafka Outbox 错误消息
 *
 * @author surezzzzzz
 */
public final class ErrorMessage {

    private ErrorMessage() {
        throw new UnsupportedOperationException(SimpleKafkaOutboxConstant.UTILITY_CLASS_MESSAGE);
    }

    public static final String KAFKA_OUTBOX_001 = "Kafka Outbox 配置非法：%s";
    public static final String KAFKA_OUTBOX_002 = "事务管理器与选中的 DataSource 不匹配：%s";
    public static final String KAFKA_OUTBOX_003 = "当前事务不能原子写入 Kafka Outbox：%s";
    public static final String KAFKA_OUTBOX_004 = "messageId 已存在，拒绝重复写入";
    public static final String KAFKA_OUTBOX_005 = "Kafka Outbox 消息快照处理失败";
    public static final String KAFKA_OUTBOX_006 = "Kafka Outbox 持久化操作失败";
    public static final String KAFKA_OUTBOX_007 = "Kafka Outbox 状态迁移或发送结果非法：%s";
    public static final String KAFKA_OUTBOX_008 = "Kafka Publisher 发送失败";
    public static final String KAFKA_OUTBOX_009 = "不支持的快照协议或 payload 类型";
}
