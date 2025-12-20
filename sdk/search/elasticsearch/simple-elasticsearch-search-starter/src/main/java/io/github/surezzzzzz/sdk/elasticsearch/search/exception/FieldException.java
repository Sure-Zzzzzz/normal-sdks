package io.github.surezzzzzz.sdk.elasticsearch.search.exception;

/**
 * 字段异常
 *
 * @author surezzzzzz
 */
public class FieldException extends SimpleElasticsearchSearchException {

    public FieldException(String errorCode, String message) {
        super(errorCode, message);
    }

    public FieldException(String errorCode, String message, Throwable cause) {
        super(errorCode, message, cause);
    }
}
