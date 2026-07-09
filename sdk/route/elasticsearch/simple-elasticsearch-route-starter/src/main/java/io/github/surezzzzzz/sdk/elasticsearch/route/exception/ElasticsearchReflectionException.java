package io.github.surezzzzzz.sdk.elasticsearch.route.exception;

/**
 * Elasticsearch 反射兼容异常
 *
 * @author surezzzzzz
 */
public class ElasticsearchReflectionException extends ElasticsearchCompatibilityException {

    private static final long serialVersionUID = 1L;

    public ElasticsearchReflectionException(String errorCode, String message) {
        super(errorCode, message);
    }

    public ElasticsearchReflectionException(String errorCode, String message, Throwable cause) {
        super(errorCode, message, cause);
    }
}
