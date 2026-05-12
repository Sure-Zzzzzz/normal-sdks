package io.github.surezzzzzz.sdk.elasticsearch.route.exception;

import lombok.Getter;

/**
 * Simple Elasticsearch Route 异常基类
 *
 * @author surezzzzzz
 */
@Getter
public class SimpleElasticsearchRouteException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final String errorCode;

    public SimpleElasticsearchRouteException(String errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public SimpleElasticsearchRouteException(String errorCode, String message, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
    }
}
