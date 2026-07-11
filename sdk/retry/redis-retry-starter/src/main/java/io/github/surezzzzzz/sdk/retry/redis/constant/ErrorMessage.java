package io.github.surezzzzzz.sdk.retry.redis.constant;

/**
 * 错误消息常量
 *
 * @author surezzzzzz
 */
public final class ErrorMessage {

    private ErrorMessage() {
        throw new UnsupportedOperationException("Utility class");
    }

    public static final String RECORD_RETRY_FAILED = "记录重试信息失败";
    public static final String CHECK_RETRY_FAILED = "检查重试状态失败";
    public static final String CLEAR_RETRY_FAILED = "清除重试记录失败";
    public static final String QUERY_RETRY_FAILED = "获取重试信息失败";
    public static final String QUERY_RETRY_KEYS_FAILED = "获取重试记录失败";
    public static final String CONFIG_BASE_DELAY_MUST_LESS_THAN_MAX = "基础延迟时间必须小于最大延迟时间";
    public static final String RETRY_LIMIT_EXCEEDED = "重试次数已超限: %d/%d";
    public static final String RETRY_RECORD_NOT_FOUND = "重试记录未找到: %s";
}
