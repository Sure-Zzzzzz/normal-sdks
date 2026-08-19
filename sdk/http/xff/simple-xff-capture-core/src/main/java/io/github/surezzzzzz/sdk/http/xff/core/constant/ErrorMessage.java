package io.github.surezzzzzz.sdk.http.xff.core.constant;

/**
 * Simple XFF Capture 错误消息。
 *
 * @author surezzzzzz
 */
public final class ErrorMessage {

    /**
     * 必填值不能为空。
     */
    public static final String REQUIRED_VALUE_MISSING = "必填值缺失：%s";
    /**
     * Capture 快照状态非法。
     */
    public static final String CAPTURE_SNAPSHOT_STATE_INVALID = "Capture 快照状态非法：%s";
    /**
     * 内置地址分类规则非法。
     */
    public static final String ADDRESS_RULE_INVALID = "内置地址分类规则非法：%s";

    private ErrorMessage() {
        throw new UnsupportedOperationException("Utility class");
    }
}
