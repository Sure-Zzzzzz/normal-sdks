package io.github.surezzzzzz.sdk.http.xff.constant;

/**
 * Simple XFF Capture Starter 常量。
 *
 * @author surezzzzzz
 */
public final class SimpleXffCaptureConstant {

    /**
     * 配置前缀。
     */
    public static final String CONFIG_PREFIX = "io.github.surezzzzzz.sdk.http.xff.capture";

    // ==================== 配置常量 ====================
    /**
     * 启用配置名称。
     */
    public static final String CONFIG_ENABLE = "enable";
    /**
     * Filter 顺序配置名称。
     */
    public static final String CONFIG_ORDER = "order";
    /**
     * 默认是否启用。
     */
    public static final boolean DEFAULT_ENABLE = false;
    /**
     * 配置启用值。
     */
    public static final String CONFIG_VALUE_TRUE = "true";
    /**
     * request 字段名。
     */
    public static final String FIELD_REQUEST = "request";

    // ==================== 字段常量 ====================
    /**
     * Host Header 名称。
     */
    public static final String HEADER_HOST = "Host";

    // ==================== Header 常量 ====================
    /**
     * X-Real-IP Header 名称。
     */
    public static final String HEADER_X_REAL_IP = "X-Real-IP";
    /**
     * X-Forwarded-For Header 名称。
     */
    public static final String HEADER_X_FORWARDED_FOR = "X-Forwarded-For";
    /**
     * X-Forwarded-Host Header 名称。
     */
    public static final String HEADER_X_FORWARDED_HOST = "X-Forwarded-Host";
    /**
     * X-Forwarded-Port Header 名称。
     */
    public static final String HEADER_X_FORWARDED_PORT = "X-Forwarded-Port";
    /**
     * X-Forwarded-Proto Header 名称。
     */
    public static final String HEADER_X_FORWARDED_PROTO = "X-Forwarded-Proto";
    /**
     * XFF 元素分隔符。
     */
    public static final char VALUE_SEPARATOR = ',';
    /**
     * HTTP 空格字符。
     */
    public static final char OPTIONAL_WHITESPACE_SPACE = ' ';
    /**
     * HTTP 水平制表符。
     */
    public static final char OPTIONAL_WHITESPACE_TAB = '\t';
    /**
     * 请求内完整 Capture 快照属性名。
     */
    public static final String REQUEST_ATTRIBUTE_CAPTURE_SNAPSHOT =
            "io.github.surezzzzzz.sdk.http.xff.capture.XffCaptureSnapshot";

    // ==================== 请求生命周期常量 ====================

    private SimpleXffCaptureConstant() {
        throw new UnsupportedOperationException("Utility class");
    }
}
