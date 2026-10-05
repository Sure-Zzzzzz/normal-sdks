package io.github.surezzzzzz.sdk.elasticsearch.search.constant;

/**
 * 不包含请求内容的错误分类。
 */
public final class ErrorCode {
    public static final String REQUEST_INVALID = "SEARCH_REQUEST_001";
    public static final String CONFIG_INVALID = "SEARCH_CONFIG_001";
    public static final String FIELD_INVALID = "SEARCH_FIELD_001";
    public static final String MAPPING_FAILED = "SEARCH_MAPPING_001";
    public static final String CURSOR_INVALID = "SEARCH_CURSOR_001";
    public static final String RESPONSE_INVALID = "SEARCH_RESPONSE_001";
    public static final String REST_FAILED = "SEARCH_REST_001";

    private ErrorCode() {
    }
}
