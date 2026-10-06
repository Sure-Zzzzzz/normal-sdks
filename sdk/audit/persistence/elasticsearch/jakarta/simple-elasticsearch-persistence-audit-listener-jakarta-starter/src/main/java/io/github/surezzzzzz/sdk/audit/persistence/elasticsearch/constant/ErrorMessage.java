package io.github.surezzzzzz.sdk.audit.persistence.elasticsearch.constant;

/**
 * 审计配置错误消息。
 */
public final class ErrorMessage {
    public static final String INVALID_EXECUTOR = "审计线程池配置无效";
    public static final String INVALID_REJECT_POLICY = "审计拒绝策略无效";
    public static final String INVALID_EVENT_RESULT = "审计事件结果类型无效";

    private ErrorMessage() {
        throw new UnsupportedOperationException("Utility class");
    }
}
