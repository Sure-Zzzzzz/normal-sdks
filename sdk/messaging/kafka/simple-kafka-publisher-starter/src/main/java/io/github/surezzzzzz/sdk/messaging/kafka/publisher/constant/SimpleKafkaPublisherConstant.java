package io.github.surezzzzzz.sdk.messaging.kafka.publisher.constant;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

/**
 * Simple Kafka Publisher 常量
 *
 * @author surezzzzzz
 */
public final class SimpleKafkaPublisherConstant {

    private SimpleKafkaPublisherConstant() {
        throw new UnsupportedOperationException(UTILITY_CLASS_MESSAGE);
    }

    // ==================== 配置相关常量 ====================

    public static final String CONFIG_PREFIX = "io.github.surezzzzzz.sdk.messaging.kafka.publisher";
    public static final String CONFIG_PROPERTY_ENABLE = "enable";
    public static final String BOOLEAN_TRUE = "true";

    // ==================== 默认值常量 ====================

    public static final boolean DEFAULT_ENABLE = false;
    public static final boolean DEFAULT_ENVELOPE_ENABLE = true;
    public static final boolean DEFAULT_INCLUDE_NULL_PAYLOAD = false;
    public static final boolean DEFAULT_ENABLE_DEFAULT_HEADERS = true;
    public static final boolean DEFAULT_ALLOW_HEADER_OVERRIDE = false;
    public static final String DEFAULT_APP_NAME = "default";
    public static final long DEFAULT_SEND_TIMEOUT_MS = 3000L;

    // ==================== Header 常量 ====================

    public static final String DEFAULT_HEADER_MESSAGE_ID = "x-message-id";
    public static final String DEFAULT_HEADER_MESSAGE_TYPE = "x-message-type";
    public static final String DEFAULT_HEADER_TRACE_ID = "x-trace-id";
    public static final String DEFAULT_HEADER_SOURCE = "x-source";
    public static final String DEFAULT_HEADER_PUBLISHED_AT = "x-published-at";
    public static final String HEADER_VALUE_EMPTY = "";

    // ==================== 错误展示常量 ====================

    public static final String ERROR_VALUE_UNSAFE_DISPLAY = "<unsafe>";
    public static final int MAX_ERROR_DISPLAY_LENGTH = 256;

    // ==================== 字符集常量 ====================

    public static final String UTF_8 = "UTF-8";
    public static final Charset CHARSET_UTF_8 = StandardCharsets.UTF_8;

    // ==================== MDC 常量 ====================

    public static final String MDC_TRACE_ID = "traceId";
    public static final String MDC_TRACE_ID_WITH_HYPHEN = "trace-id";
    public static final String MDC_X_TRACE_ID = "X-B3-TraceId";

    // ==================== 工具类常量 ====================

    public static final String UTILITY_CLASS_MESSAGE = "Utility class";

    // ==================== 校验原因常量 ====================

    public static final String REASON_MESSAGE_EMPTY = "message 不能为空";
    public static final String REASON_PAYLOAD_EMPTY = "payload 不能为空";
    public static final String REASON_ROUTE_KEY_EMPTY = "routeKey 不能为空";
    public static final String REASON_DATASOURCE_KEY_EMPTY = "datasourceKey 不能为空";
    public static final String REASON_PARTITION_NEGATIVE = "partition 不能小于 0";
    public static final String REASON_TIMESTAMP_NEGATIVE = "timestamp 不能小于 0";
    public static final String REASON_HEADER_KEY_EMPTY = "header key 不能为空";
    public static final String REASON_HEADER_KEY_CONTROL = "header key 不能包含控制字符";
    public static final String REASON_HEADER_VALUE_NULL = "header value 不能为 null";
    public static final String REASON_HEADER_RESERVED = "默认 header 不允许被覆盖";
    public static final String REASON_HEADER_DUPLICATE = "header 名大小写不敏感重复";
    public static final String REASON_PROPERTIES_EMPTY = "publisher 配置不能为空";
    public static final String REASON_ENVELOPE_CONFIG_EMPTY = "envelope 配置不能为空";
    public static final String REASON_HEADER_CONFIG_EMPTY = "headers 配置不能为空";
    public static final String REASON_SEND_CONFIG_EMPTY = "send 配置不能为空";
    public static final String REASON_SEND_TIMEOUT_INVALID = "send.timeout-ms 必须大于 0";
    public static final String REASON_CONFIG_HEADER_NAME_EMPTY = "默认 header 名称不能为空";
    public static final String REASON_CONFIG_HEADER_NAME_CONTROL = "默认 header 名称不能包含控制字符";
    public static final String REASON_CONFIG_HEADER_NAME_DUPLICATE = "默认 header 名称不能重复";
    public static final String REASON_APP_NAME_EMPTY = "启用 envelope 或默认 header 时 app-name 不能为空";
    public static final String REASON_MESSAGE_ID_EMPTY = "messageId 生成结果不能为空";
    public static final String REASON_SEND_FUTURE_EMPTY = "底层发送 Future 不能为空";
    public static final String REASON_SEND_RESULT_EMPTY = "底层发送结果或 metadata 不能为空";
    public static final String REASON_SEND_INTERRUPTED = "同步等待被中断，发送状态未知，不应盲目重试以免重复投递";

    // ==================== 数字常量 ====================

    public static final int ZERO = 0;
}
