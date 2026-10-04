package io.github.surezzzzzz.sdk.elasticsearch.route.exception;

/**
 * 版本相关异常
 *
 * @author surezzzzzz
 */
public class VersionException extends SimpleElasticsearchRouteException {

    public VersionException(String errorCode, String message) {
        super(errorCode, message);
    }

    public VersionException(String errorCode, String message, Throwable cause) {
        super(errorCode, message, cause);
    }
}
