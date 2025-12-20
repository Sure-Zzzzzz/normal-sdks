package io.github.surezzzzzz.sdk.elasticsearch.route.exception;

/**
 * 配置异常
 *
 * @author surezzzzzz
 */
public class ConfigurationException extends SimpleElasticsearchRouteException {

    public ConfigurationException(String errorCode, String message) {
        super(errorCode, message);
    }

    public ConfigurationException(String errorCode, String message, Throwable cause) {
        super(errorCode, message, cause);
    }
}
