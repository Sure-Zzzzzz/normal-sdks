package io.github.surezzzzzz.sdk.elasticsearch.persistence.core.constant;

/**
 * Error Code Constants
 *
 * @author surezzzzzz
 */
public final class ErrorCode {

    private ErrorCode() {
        throw new UnsupportedOperationException("Utility class");
    }

    public static final String CONFIG_VALIDATION_FAILED = "CONFIG_001";
    public static final String REQUEST_VALIDATION_FAILED = "REQUEST_001";
    public static final String EXECUTOR_NOT_FOUND = "EXECUTOR_001";
    public static final String EXECUTION_FAILED = "EXECUTION_001";
    public static final String ROUTE_RESOLVE_FAILED = "ROUTE_001";
    public static final String ES_REQUEST_BUILD_FAILED = "ES_001";
    public static final String ES_RESPONSE_PARSE_FAILED = "ES_002";
    public static final String UNSUPPORTED_OPERATION = "UNSUPPORTED_001";
}
