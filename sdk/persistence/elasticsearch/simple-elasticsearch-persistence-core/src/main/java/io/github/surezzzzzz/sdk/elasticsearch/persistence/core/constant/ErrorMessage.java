package io.github.surezzzzzz.sdk.elasticsearch.persistence.core.constant;

/**
 * Error Message Constants
 *
 * @author surezzzzzz
 */
public final class ErrorMessage {

    private ErrorMessage() {
        throw new UnsupportedOperationException("Utility class");
    }

    public static final String CONFIG_VALIDATION_FAILED = "配置验证失败";
    public static final String REQUEST_VALIDATION_FAILED = "请求参数校验失败：%s";
    public static final String EXECUTOR_NOT_FOUND = "未找到请求类型对应的执行器：%s";
    public static final String EXECUTION_FAILED = "Elasticsearch persistence 执行失败：%s";
    public static final String ROUTE_RESOLVE_FAILED = "索引路由解析失败：%s";
    public static final String ES_REQUEST_BUILD_FAILED = "ES 请求构建失败：%s";
    public static final String ES_RESPONSE_PARSE_FAILED = "ES 响应解析失败：%s";
    public static final String UNSUPPORTED_OPERATION = "不支持的操作：%s";
}
