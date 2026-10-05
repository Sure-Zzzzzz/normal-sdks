package io.github.surezzzzzz.sdk.elasticsearch.persistence.constant;

/**
 * 固定异常信息，与复用的 Core 错误码对应，不拼接请求内容。
 */
public final class ErrorMessage {
    public static final String CONFIG_VALIDATION_FAILED = "持久化配置无效";
    public static final String REQUEST_VALIDATION_FAILED = "持久化请求参数无效";
    public static final String EXECUTOR_NOT_FOUND = "持久化执行器未注册";
    public static final String EXECUTION_FAILED = "Elasticsearch 持久化执行失败";
    public static final String ROUTE_RESOLVE_FAILED = "索引数据源归属无效";
    public static final String ES_REQUEST_BUILD_FAILED = "持久化请求编码失败";
    public static final String ES_RESPONSE_PARSE_FAILED = "Elasticsearch 持久化响应无效";
    public static final String UNSUPPORTED_OPERATION = "持久化操作不支持";

    private ErrorMessage() {
    }
}
