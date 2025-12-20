package io.github.surezzzzzz.sdk.elasticsearch.search.exception;

/**
 * 聚合异常
 *
 * @author surezzzzzz
 */
public class AggregationException extends SimpleElasticsearchSearchException {

    public AggregationException(String errorCode, String message) {
        super(errorCode, message);
    }

    public AggregationException(String errorCode, String message, Throwable cause) {
        super(errorCode, message, cause);
    }
}
