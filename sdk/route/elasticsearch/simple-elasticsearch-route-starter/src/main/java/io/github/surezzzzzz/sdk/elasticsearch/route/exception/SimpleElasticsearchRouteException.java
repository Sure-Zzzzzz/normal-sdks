package io.github.surezzzzzz.sdk.elasticsearch.route.exception;

/**
 * Simple Elasticsearch Route 异常基类
 *
 * @author surezzzzzz
 */
public class SimpleElasticsearchRouteException extends RuntimeException {

    private final String errorCode;

    public SimpleElasticsearchRouteException(String errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public SimpleElasticsearchRouteException(String errorCode, String message, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
    }

    public String getErrorCode() {
        return errorCode;
    }
}
