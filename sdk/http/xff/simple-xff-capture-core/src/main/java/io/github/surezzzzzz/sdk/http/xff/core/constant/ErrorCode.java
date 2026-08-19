package io.github.surezzzzzz.sdk.http.xff.core.constant;

/**
 * Simple XFF Capture 错误码。
 *
 * @author surezzzzzz
 */
public final class ErrorCode {

    /**
     * 必填参数缺失。
     */
    public static final String REQUIRED_VALUE_MISSING = "BIZ_001";
    /**
     * Capture 快照状态非法。
     */
    public static final String CAPTURE_SNAPSHOT_STATE_INVALID = "BIZ_002";
    /**
     * 内置地址分类规则非法。
     */
    public static final String ADDRESS_RULE_INVALID = "BIZ_003";

    private ErrorCode() {
        throw new UnsupportedOperationException("Utility class");
    }
}
