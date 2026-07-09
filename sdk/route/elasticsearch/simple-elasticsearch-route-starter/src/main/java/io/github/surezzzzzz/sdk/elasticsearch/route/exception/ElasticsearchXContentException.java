package io.github.surezzzzzz.sdk.elasticsearch.route.exception;

/**
 * Elasticsearch XContent 兼容异常
 *
 * @author surezzzzzz
 */
public class ElasticsearchXContentException extends ElasticsearchCompatibilityException {

    private static final long serialVersionUID = 1L;

    public ElasticsearchXContentException(String errorCode, String message) {
        super(errorCode, message);
    }

    public ElasticsearchXContentException(String errorCode, String message, Throwable cause) {
        super(errorCode, message, cause);
    }
}
