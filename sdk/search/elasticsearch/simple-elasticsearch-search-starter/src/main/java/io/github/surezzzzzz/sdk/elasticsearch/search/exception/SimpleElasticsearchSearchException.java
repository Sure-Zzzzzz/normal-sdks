package io.github.surezzzzzz.sdk.elasticsearch.search.exception;

/**
 * Simple Elasticsearch Search 异常基类
 *
 * @author surezzzzzz
 */
public class SimpleElasticsearchSearchException extends RuntimeException {

    private final String errorCode;

    public SimpleElasticsearchSearchException(String errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public SimpleElasticsearchSearchException(String errorCode, String message, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
    }

    public String getErrorCode() {
        return errorCode;
    }
}
