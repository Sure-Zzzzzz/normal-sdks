package io.github.surezzzzzz.sdk.http.xff.constant;

import java.util.*;

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
     * 排除路径模式配置名称。
     */
    public static final String CONFIG_EXCLUDED_PATH_PATTERNS = "excluded-path-patterns";
    /**
     * 默认排除路径模式。
     */
    public static final List<String> DEFAULT_EXCLUDED_PATH_PATTERNS = Collections.emptyList();
    /**
     * 默认是否启用。
     */
    public static final boolean DEFAULT_ENABLE = false;
    /**
     * 请求体快照默认最大字节数。
     */
    public static final long DEFAULT_BODY_MAX_BYTES = 65536L;
    /**
     * 请求体流读取缓冲区字节数。
     */
    public static final int BODY_READ_BUFFER_BYTES = 4096;
    /**
     * 配置启用值。
     */
    public static final String CONFIG_VALUE_TRUE = "true";
    /**
     * request 字段名。
     */
    public static final String FIELD_REQUEST = "request";

    // ==================== 校验消息常量 ====================
    public static final String DETAIL_REQUEST_DATA_REQUIRED = "请求数据采集配置不能为空";
    public static final String DETAIL_REQUEST_REQUIRED = "请求不能为空";
    public static final String DETAIL_UTF8_UNAVAILABLE = "UTF-8 不可用";
    public static final String DETAIL_BODY_MAX_BYTES_POSITIVE = "请求体最大采集字节数必须大于 0";
    public static final String DETAIL_ALLOWED_CONTENT_TYPES_REQUIRED = "请求体允许 Content-Type 不能为空";
    public static final String DETAIL_ALLOWED_CONTENT_TYPE_NOT_BLANK = "请求体允许 Content-Type 不能为空白";
    public static final String DETAIL_ALLOWED_CONTENT_TYPE_INVALID = "请求体允许 Content-Type 格式非法：%s";
    public static final String DETAIL_REPLAY_FILE_UNREADABLE = "请求体回放临时文件不可读取";
    public static final String DETAIL_REPLAY_ASYNC_UNSUPPORTED = "请求体回放不支持异步读取";
    public static final String DETAIL_RULE_PATH_REQUIRED = "请求数据采集%s规则 pathPattern 不能为空";
    public static final String DETAIL_RULE_DUPLICATE = "请求数据采集%s规则不能重复：%s";
    public static final String DETAIL_RULE_METHOD_REQUIRED = "请求数据采集%s规则 method 不能为空";
    public static final String DETAIL_RULE_METHOD_INVALID =
            "请求数据采集%s规则 method 非法，仅支持 GET、POST、PUT、PATCH、DELETE、ALL：%s";

    // ==================== 请求数据协议常量 ====================
    /**
     * 表单回放临时文件前缀。
     */
    public static final String REPLAY_FILE_PREFIX = "simple-xff-capture-";
    /**
     * 表单回放临时文件后缀。
     */
    public static final String REPLAY_FILE_SUFFIX = ".body";
    /**
     * 表单媒体类型。
     */
    public static final String FORM_CONTENT_TYPE = "application/x-www-form-urlencoded";
    /**
     * 媒体类型参数分隔符。
     */
    public static final String MEDIA_TYPE_PARAMETER_SEPARATOR = ";";
    /**
     * 媒体类型与子类型分隔符。
     */
    public static final String MEDIA_TYPE_SEPARATOR = "/";
    /**
     * 媒体类型通配符。
     */
    public static final String MEDIA_TYPE_WILDCARD = "*";
    /**
     * 媒体类型结构化后缀通配符。
     */
    public static final String MEDIA_TYPE_SUFFIX_WILDCARD = "*+";
    /**
     * 表单参数分隔符。
     */
    public static final char FORM_PARAMETER_SEPARATOR = '&';
    /**
     * 表单名称和值分隔符。
     */
    public static final char FORM_VALUE_SEPARATOR = '=';
    /**
     * 全部请求方法规则。
     */
    public static final String REQUEST_METHOD_ALL = "ALL";
    /**
     * 白名单规则名称。
     */
    public static final String RULE_TYPE_WHITELIST = "白名单";
    /**
     * 黑名单规则名称。
     */
    public static final String RULE_TYPE_BLACKLIST = "黑名单";
    /**
     * 规则唯一键分隔符。
     */
    public static final String RULE_KEY_SEPARATOR = " ";
    /**
     * 可配置的请求方法。
     */
    public static final Set<String> SUPPORTED_REQUEST_METHODS = Collections.unmodifiableSet(
            new LinkedHashSet<String>(Arrays.asList("GET", "POST", "PUT", "PATCH", "DELETE", REQUEST_METHOD_ALL)));

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
    /**
     * 请求内请求数据快照属性名。
     */
    public static final String REQUEST_ATTRIBUTE_REQUEST_DATA_SNAPSHOT =
            "io.github.surezzzzzz.sdk.http.xff.capture.RequestDataSnapshot";

    // ==================== 请求生命周期常量 ====================

    private SimpleXffCaptureConstant() {
        throw new UnsupportedOperationException("Utility class");
    }
}
