package io.github.surezzzzzz.sdk.audit.http.xff.constant;

/**
 * XFF Capture 审计错误码。
 *
 * @author surezzzzzz
 */
public final class ErrorCode {

    /**
     * 必填值缺失。
     */
    public static final String REQUIRED_VALUE_MISSING = "CONFIG_001";
    /**
     * 配置值非法。
     */
    public static final String CONFIG_VALUE_INVALID = "CONFIG_002";
    /**
     * 审计文档状态非法。
     */
    public static final String AUDIT_DOCUMENT_STATE_INVALID = "BIZ_001";

    private ErrorCode() {
        throw new UnsupportedOperationException("Utility class");
    }
}
