package io.github.surezzzzzz.sdk.audit.http.xff.constant;

/**
 * XFF Capture 审计错误消息。
 *
 * @author surezzzzzz
 */
public final class ErrorMessage {

    /**
     * 必填值缺失。
     */
    public static final String REQUIRED_VALUE_MISSING = "必填值缺失：%s";
    /**
     * 配置值非法。
     */
    public static final String CONFIG_VALUE_INVALID = "配置值非法：%s";
    /**
     * 审计文档状态非法。
     */
    public static final String AUDIT_DOCUMENT_STATE_INVALID = "审计文档状态非法：%s";

    private ErrorMessage() {
        throw new UnsupportedOperationException("Utility class");
    }
}
