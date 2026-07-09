package io.github.surezzzzzz.sdk.elasticsearch.route.exception;

/**
 * Elasticsearch low-level 请求兼容异常
 *
 * @author surezzzzzz
 */
public class ElasticsearchLowLevelRequestException extends ElasticsearchCompatibilityException {

    private static final long serialVersionUID = 1L;

    public ElasticsearchLowLevelRequestException(String errorCode, String message) {
        super(errorCode, message);
    }

    public ElasticsearchLowLevelRequestException(String errorCode, String message, Throwable cause) {
        super(errorCode, message, cause);
    }
}
