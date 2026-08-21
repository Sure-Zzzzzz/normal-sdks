package io.github.surezzzzzz.sdk.http.xff.constant;

import org.springframework.core.Ordered;

/**
 * Simple XFF Capture Web 常量。
 *
 * @author surezzzzzz
 */
public final class SimpleXffCaptureWebConstant {

    /**
     * Filter Bean 名称。
     */
    public static final String FILTER_BEAN_NAME = "simpleXffCaptureFilterRegistration";
    /**
     * Filter 注册名称。
     */
    public static final String FILTER_NAME = "simpleXffCaptureFilter";
    /**
     * URL 路径分隔符。
     */
    public static final String URL_PATH_SEPARATOR = "/";
    /**
     * URL 全路径通配符。
     */
    public static final String URL_PATH_MATCH_ALL = "*";
    /**
     * Filter URL Pattern。
     */
    public static final String FILTER_URL_PATTERN = URL_PATH_SEPARATOR + URL_PATH_MATCH_ALL;
    /**
     * Filter 默认顺序。
     */
    public static final int FILTER_ORDER = Ordered.LOWEST_PRECEDENCE - 100;

    private SimpleXffCaptureWebConstant() {
        throw new UnsupportedOperationException("Utility class");
    }
}
