package io.github.surezzzzzz.sdk.elasticsearch.route.exception;

/**
 * Elasticsearch 兼容公共能力异常
 *
 * @author surezzzzzz
 */
public class ElasticsearchCompatibilityException extends SimpleElasticsearchRouteException {

    private static final long serialVersionUID = 1L;

    public ElasticsearchCompatibilityException(String errorCode, String message) {
        super(errorCode, message);
    }

    public ElasticsearchCompatibilityException(String errorCode, String message, Throwable cause) {
        super(errorCode, message, cause);
    }
}
