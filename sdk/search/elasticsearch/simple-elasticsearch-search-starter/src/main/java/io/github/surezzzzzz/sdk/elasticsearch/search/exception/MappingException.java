package io.github.surezzzzzz.sdk.elasticsearch.search.exception;

/**
 * Mapping 异常
 *
 * @author surezzzzzz
 */
public class MappingException extends SimpleElasticsearchSearchException {

    public MappingException(String errorCode, String message) {
        super(errorCode, message);
    }

    public MappingException(String errorCode, String message, Throwable cause) {
        super(errorCode, message, cause);
    }
}
