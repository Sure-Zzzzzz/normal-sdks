package io.github.surezzzzzz.sdk.messaging.kafka.publisher.constant;

/**
 * Simple Kafka Publisher 错误码
 *
 * @author surezzzzzz
 */
public final class ErrorCode {

    private ErrorCode() {
        throw new UnsupportedOperationException(SimpleKafkaPublisherConstant.UTILITY_CLASS_MESSAGE);
    }

    /**
     * 配置非法
     */
    public static final String KAFKA_PUBLISHER_001 = "KAFKA_PUBLISHER_001";

    /**
     * 消息为空
     */
    public static final String KAFKA_PUBLISHER_002 = "KAFKA_PUBLISHER_002";

    /**
     * payload 非法
     */
    public static final String KAFKA_PUBLISHER_003 = "KAFKA_PUBLISHER_003";

    /**
     * topic 为空
     */
    public static final String KAFKA_PUBLISHER_004 = "KAFKA_PUBLISHER_004";

    /**
     * partition/timestamp 非法
     */
    public static final String KAFKA_PUBLISHER_005 = "KAFKA_PUBLISHER_005";

    /**
     * 序列化失败
     */
    public static final String KAFKA_PUBLISHER_006 = "KAFKA_PUBLISHER_006";

    /**
     * 发送失败
     */
    public static final String KAFKA_PUBLISHER_007 = "KAFKA_PUBLISHER_007";

    /**
     * 同步等待超时
     */
    public static final String KAFKA_PUBLISHER_008 = "KAFKA_PUBLISHER_008";

    /**
     * header 非法
     */
    public static final String KAFKA_PUBLISHER_009 = "KAFKA_PUBLISHER_009";

    /**
     * routeKey/datasourceKey 非法
     */
    public static final String KAFKA_PUBLISHER_010 = "KAFKA_PUBLISHER_010";

    /**
     * 同步等待被中断（发送状态未知，调用方不应盲目重试以免重复投递）
     */
    public static final String KAFKA_PUBLISHER_011 = "KAFKA_PUBLISHER_011";
}
